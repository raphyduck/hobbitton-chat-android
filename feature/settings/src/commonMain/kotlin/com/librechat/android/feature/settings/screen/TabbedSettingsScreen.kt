package com.librechat.android.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import librechat_android.feature.settings.generated.resources.Res
import librechat_android.feature.settings.generated.resources.*
import com.librechat.android.feature.settings.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val SettingsTabs = listOf("General", "Chat", "Account", "Data")

/**
 * Single tabbed settings screen with Material 3 secondary tabs.
 * Replaces the previous sidebar-category-navigation approach.
 * Each tab hosts the content previously shown in its own sub-page screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedSettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToArchived: () -> Unit,
    onNavigateToSharedLinks: () -> Unit,
    onNavigateToPresets: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { SettingsTabs.size })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(Res.string.title_settings)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.cd_back),
                            )
                        }
                    },
                )
                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SettingsTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(title) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> GeneralSettingsContent(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                )
                1 -> ChatSettingsContent(
                    onNavigateToPresets = onNavigateToPresets,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                )
                2 -> AccountSettingsContent(
                    onLogout = onLogout,
                    onNavigateToApiKeys = onNavigateToApiKeys,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                )
                3 -> DataSettingsContent(
                    onNavigateToArchived = onNavigateToArchived,
                    onNavigateToSharedLinks = onNavigateToSharedLinks,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                )
            }
        }
    }
}
