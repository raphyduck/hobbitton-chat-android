package com.garfiec.librechat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.attention.AndroidAttentionNotifier
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.data.engine.ConversationRequests
import com.garfiec.librechat.core.data.engine.EngineCallbackDelivery
import com.garfiec.librechat.core.network.engine.auth.CALLBACK_SCHEME
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.shared.engine.EngineNavHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val connectivityObserver: ConnectivityObserver by inject()
    private val themeDataStore: ThemeDataStore by inject()
    private val settingsDataStore: SettingsDataStore by inject()
    private val engineCallbacks: EngineCallbackDelivery by inject()
    private val conversationRequests: ConversationRequests by inject()

    /** Android 13+: posting a notification needs the person's leave, asked at run time. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.i { "Notification permission ${if (granted) "granted" else "refused"}" }
        }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only process the launch intent on a genuinely fresh start: on recreation it is still sticky,
        // and the portal's callback it may carry has already been delivered.
        if (savedInstanceState == null) handleIntent(intent)

        // Sound and notifications are on by default (03/10/2026); a question asked while the app is
        // out of sight is only heard through a notification. Asked once, on a fresh start: the
        // Settings switch asks again when turned back on.
        if (savedInstanceState == null) askForNotificationsOnce()

        // The home-screen model shortcuts deep-linked into LibreChat's chat (`librechat://model`),
        // which no longer exists (D-077). Clear whatever an earlier build published.
        ShortcutManagerCompat.removeAllDynamicShortcuts(this)

        setContent {
            val isConnected by connectivityObserver.isConnected.collectAsStateWithLifecycle(initialValue = true)
            // Hold off drawing themed content until the persisted theme has resolved, so a
            // dark-mode user on a light-system device never sees a one-frame flash of the wrong
            // theme. The system window background covers the sub-frame gap.
            val themeReady by themeDataStore.isReady.collectAsStateWithLifecycle()
            val themeMode by themeDataStore.themeMode.collectAsStateWithLifecycle(initialValue = themeDataStore.initialThemeMode)
            val accentColorArgb by themeDataStore.accentColor.collectAsStateWithLifecycle(
                initialValue = themeDataStore.initialAccentColor,
            )
            val useDynamicColor by themeDataStore.useDynamicColor.collectAsStateWithLifecycle(
                initialValue = themeDataStore.initialUseDynamicColor,
            )
            // Gate on the language warm-up too so a persisted non-system language is applied
            // before the first frame (no flash of the system locale before switching).
            val localeReady by settingsDataStore.isReady.collectAsStateWithLifecycle()
            val selectedLanguage by settingsDataStore.selectedLanguage.collectAsStateWithLifecycle(
                initialValue = settingsDataStore.initialSelectedLanguage,
            )
            // The DEFAULT_LANGUAGE sentinel ("system") maps to no override → keep the device locale.
            val appLocale = selectedLanguage.takeIf { it != SettingsDataStore.DEFAULT_LANGUAGE }
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Update system bar icon colors to match the app's resolved theme.
            // When light theme: dark icons on light background (isAppearanceLight = true).
            // When dark theme: light icons on dark background (isAppearanceLight = false).
            // This ensures correct visibility even when the user overrides the system theme.
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }

            if (themeReady && localeReady) {
                LibreChatTheme(
                    darkTheme = darkTheme,
                    accentColor = Color(accentColorArgb),
                    useDynamicColor = useDynamicColor,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { testTagsAsResourceId = true },
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(
                                visible = !isConnected,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .windowInsetsPadding(WindowInsets.statusBars)
                                        .padding(vertical = 6.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_connection),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                            // The engine shell (D-077): the portal's sign-in, then the chat on the engine.
                            // LibreChat's shell is no longer composed on Android.
                            EngineNavHost(
                                appLocaleTag = appLocale,
                                onRequestNotificationPermission = ::requestNotificationPermission,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (!isConnected) {
                                            Modifier.consumeWindowInsets(WindowInsets.statusBars)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep getIntent() pointing at the latest intent so a later recreation doesn't re-read a stale one.
        setIntent(intent)
        handleIntent(intent)
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationsAllowed()) return
        val prefs = getSharedPreferences(ATTENTION_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false)) return
        lifecycleScope.launch {
            if (!settingsDataStore.attentionSound.first()) return@launch
            prefs.edit { putBoolean(KEY_NOTIFICATIONS_ASKED, true) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** The Settings switch turned on: ask, unless granted already. Android stops asking after two refusals. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationsAllowed()) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The only link this app still acts on (D-077): the portal's return. LibreChat's own deep links
     * (`librechat://`, a conversation, a model shortcut, an OAuth hop) and shares into a chat named
     * screens that no longer exist; they are ignored, and the manifest no longer asks for them.
     */
    private fun handleIntent(intent: Intent?) {
        // A tapped notification (a question, a reply ready): the shell opens its conversation.
        AndroidAttentionNotifier.conversationOf(intent)?.let { conversation ->
            conversationRequests.open(conversation)
            return
        }
        val uri = intent?.data ?: return
        // Le retour du portail d'authentification. Il ne désigne aucun écran — il porte un code
        // d'autorisation que le tour de connexion attend — et va donc droit à la boîte aux lettres.
        if (uri.scheme.equals(CALLBACK_SCHEME, ignoreCase = true)) {
            engineCallbacks.deposer(uri.toString())
        } else {
            Logger.w { "Ignoring a link this app no longer handles: scheme=${uri.scheme}" }
        }
    }

    private companion object {
        const val ATTENTION_PREFS = "attention"
        const val KEY_NOTIFICATIONS_ASKED = "notifications_asked"
    }
}
