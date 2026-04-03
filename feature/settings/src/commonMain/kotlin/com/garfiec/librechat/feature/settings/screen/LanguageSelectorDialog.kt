package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import librechat_android.feature.settings.generated.resources.Res
import librechat_android.feature.settings.generated.resources.*

data class LanguageOption(
    val code: String,
    val displayName: String,
)

private val SUPPORTED_LANGUAGES = listOf(
    LanguageOption("en", "English"),
    LanguageOption("es", "Spanish"),
    LanguageOption("fr", "French"),
    LanguageOption("de", "German"),
    LanguageOption("it", "Italian"),
    LanguageOption("pt", "Portuguese"),
    LanguageOption("ru", "Russian"),
    LanguageOption("zh", "Chinese (Simplified)"),
    LanguageOption("ja", "Japanese"),
    LanguageOption("ko", "Korean"),
    LanguageOption("ar", "Arabic"),
    LanguageOption("hi", "Hindi"),
    LanguageOption("tr", "Turkish"),
    LanguageOption("pl", "Polish"),
    LanguageOption("nl", "Dutch"),
    LanguageOption("sv", "Swedish"),
    LanguageOption("da", "Danish"),
    LanguageOption("fi", "Finnish"),
    LanguageOption("no", "Norwegian"),
    LanguageOption("uk", "Ukrainian"),
    LanguageOption("th", "Thai"),
    LanguageOption("vi", "Vietnamese"),
    LanguageOption("id", "Indonesian"),
    LanguageOption("ms", "Malay"),
    LanguageOption("cs", "Czech"),
    LanguageOption("ro", "Romanian"),
    LanguageOption("hu", "Hungarian"),
    LanguageOption("el", "Greek"),
    LanguageOption("he", "Hebrew"),
    LanguageOption("bg", "Bulgarian"),
    LanguageOption("ca", "Catalan"),
    LanguageOption("hr", "Croatian"),
    LanguageOption("sk", "Slovak"),
    LanguageOption("sl", "Slovenian"),
    LanguageOption("sr", "Serbian"),
    LanguageOption("lt", "Lithuanian"),
    LanguageOption("lv", "Latvian"),
    LanguageOption("et", "Estonian"),
)

/** Searchable single-select language picker with 37+ locales. */
@Composable
internal fun LanguageSelectorDialog(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            SUPPORTED_LANGUAGES
        } else {
            SUPPORTED_LANGUAGES.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(Res.string.language)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(Res.string.hint_search_languages)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) {
                    items(filtered, key = { it.code }, contentType = { "language" }) { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLanguageSelected(language.code) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = language.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (language.code == selectedLanguage) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(Res.string.cd_selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_done))
            }
        },
    )
}
