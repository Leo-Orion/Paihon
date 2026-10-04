package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.size
import eu.kanade.domain.ui.model.AppTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource

object HomeScreen : Screen() {

    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()

    @Suppress("ConstPropertyName")
    private const val TabFadeDuration = 200

    @Suppress("ConstPropertyName")
    private const val TabNavigatorKey = "HomeTabs"

    private val TABS = listOf(
        LibraryTab,
        UpdatesTab,
        HistoryTab,
        BrowseTab,
        MoreTab,
    )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        TabNavigator(
            tab = LibraryTab,
            key = TabNavigatorKey,
        ) { tabNavigator ->
            // Provide usable navigator to content screen
            CompositionLocalProvider(LocalNavigator provides navigator) {
                val tabletUi = isTabletUi()
                val navigationSuiteType = if (tabletUi) {
                    NavigationSuiteType.NavigationRail
                } else {
                    NavigationSuiteType.NavigationBar
                }
                val navigationSuiteState = rememberNavigationSuiteScaffoldState()
                LaunchedEffect(navigationSuiteState, tabletUi) {
                    if (tabletUi) navigationSuiteState.show()
                    showBottomNavEvent.receiveAsFlow().collectLatest { show ->
                        if (tabletUi || show) {
                            navigationSuiteState.show()
                        } else {
                            navigationSuiteState.hide()
                        }
                    }
                }

                NavigationSuiteScaffold(
                    navigationSuiteType = navigationSuiteType,
                    state = navigationSuiteState,
                    navigationSuiteColors = NavigationSuiteDefaults.colors(
                        navigationRailContainerColor = MaterialTheme.colorScheme
                            .surfaceColorAtElevation(3.dp),
                    ),
                    navigationItemVerticalArrangement = Arrangement.Center,
                    navigationItems = {
                        TABS.fastForEach { NavigationSuiteItem(it, navigationSuiteType) }
                    },
                ) {
                    AnimatedContent(
                        targetState = tabNavigator.current,
                        transitionSpec = {
                            materialFadeThroughIn(
                                initialScale = 1f,
                                durationMillis = TabFadeDuration,
                            ) togetherWith materialFadeThroughOut(durationMillis = TabFadeDuration)
                        },
                        label = "tabContent",
                    ) {
                        tabNavigator.saveableState(key = "currentTab", it) {
                            it.Content()
                        }
                    }
                }
            }

            val goToLibraryTab = { tabNavigator.current = LibraryTab }

            BackHandler(enabled = tabNavigator.current != LibraryTab, onBack = goToLibraryTab)

            LaunchedEffect(Unit) {
                launch {
                    librarySearchEvent.receiveAsFlow().collectLatest {
                        goToLibraryTab()
                        LibraryTab.search(it)
                    }
                }
                launch {
                    openTabEvent.receiveAsFlow().collectLatest {
                        tabNavigator.current = when (it) {
                            is Tab.Library -> LibraryTab
                            Tab.Updates -> UpdatesTab
                            Tab.History -> HistoryTab
                            is Tab.Browse -> {
                                if (it.toExtensions) {
                                    BrowseTab.showExtension()
                                }
                                BrowseTab
                            }
                            is Tab.More -> MoreTab
                        }

                        if (it is Tab.Library && it.mangaIdToOpen != null) {
                            navigator.push(MangaScreen(it.mangaIdToOpen))
                        }
                        if (it is Tab.More && it.toDownloads) {
                            navigator.push(DownloadQueueScreen)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun NavigationSuiteItem(
        tab: eu.kanade.presentation.util.Tab,
        navigationSuiteType: NavigationSuiteType,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationSuiteItem(
            navigationSuiteType = navigationSuiteType,
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = {
                val context = LocalContext.current
                val appTheme by produceState(initialValue = AppTheme.DEFAULT) {
                    context.appGraph.uiPreferences.appTheme.changes().collectLatest { value = it }
                }
                val iconRes = getNavbarIconForTab(tab, appTheme)
                if (iconRes != null) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(iconRes),
                        contentDescription = tab.options.title,
                        modifier = Modifier.size(26.dp),
                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                    )
                } else {
                    Icon(
                        painter = tab.options.icon!!,
                        contentDescription = tab.options.title,
                    )
                }
            },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            badge = tabBadge(tab),
        )
    }

    private fun getNavbarIconForTab(tab: eu.kanade.presentation.util.Tab, appTheme: AppTheme): Int? {
        return when (tab) {
            is LibraryTab -> when (appTheme) {
                AppTheme.MONET -> eu.kanade.tachiyomi.R.drawable.ic_anime_dynamic
                AppTheme.CLOUDFLARE -> eu.kanade.tachiyomi.R.drawable.ic_anime_cloudflare
                AppTheme.COTTONCANDY -> eu.kanade.tachiyomi.R.drawable.ic_anime_cotton_candy
                AppTheme.DOOM -> eu.kanade.tachiyomi.R.drawable.ic_anime_doom
                AppTheme.GREEN_APPLE -> eu.kanade.tachiyomi.R.drawable.ic_anime_green_apple
                AppTheme.LAVENDER -> eu.kanade.tachiyomi.R.drawable.ic_anime_lavender
                AppTheme.MATRIX -> eu.kanade.tachiyomi.R.drawable.ic_anime_matrix
                AppTheme.MIDNIGHT_DUSK -> eu.kanade.tachiyomi.R.drawable.ic_anime_midnight_dusk
                AppTheme.MOCHA -> eu.kanade.tachiyomi.R.drawable.ic_anime_mocha
                AppTheme.SAPPHIRE -> eu.kanade.tachiyomi.R.drawable.ic_anime_sapphire
                AppTheme.NORD -> eu.kanade.tachiyomi.R.drawable.ic_anime_nord
                AppTheme.STRAWBERRY_DAIQUIRI -> eu.kanade.tachiyomi.R.drawable.ic_anime_strawberry_daiquiri
                AppTheme.TAKO -> eu.kanade.tachiyomi.R.drawable.ic_anime_tako
                AppTheme.TEALTURQUOISE -> eu.kanade.tachiyomi.R.drawable.ic_anime_teal_turquoise
                AppTheme.TIDAL_WAVE -> eu.kanade.tachiyomi.R.drawable.ic_anime_tidal_wave
                AppTheme.YINYANG -> eu.kanade.tachiyomi.R.drawable.ic_anime_yin_yang
                AppTheme.YOTSUBA -> eu.kanade.tachiyomi.R.drawable.ic_anime_yotsuba
                AppTheme.MONOCHROME -> eu.kanade.tachiyomi.R.drawable.ic_anime_monochrome
                else -> null
            }
            is HistoryTab -> when (appTheme) {
                AppTheme.MONET -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_dynamic
                AppTheme.CLOUDFLARE -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_cloudflare
                AppTheme.COTTONCANDY -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_cotton_candy
                AppTheme.DOOM -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_doom
                AppTheme.GREEN_APPLE -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_green_apple
                AppTheme.LAVENDER -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_lavender
                AppTheme.MATRIX -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_matrix
                AppTheme.MIDNIGHT_DUSK -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_midnight_dusk
                AppTheme.MOCHA -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_mocha
                AppTheme.SAPPHIRE -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_sapphire
                AppTheme.NORD -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_nord
                AppTheme.STRAWBERRY_DAIQUIRI -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_strawberry_daiquiri
                AppTheme.TAKO -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_tako
                AppTheme.TEALTURQUOISE -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_teal_turquoise
                AppTheme.TIDAL_WAVE -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_tidal_wave
                AppTheme.YINYANG -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_yin_yang
                AppTheme.YOTSUBA -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_yotsuba
                AppTheme.MONOCHROME -> eu.kanade.tachiyomi.R.drawable.ic_dashboard_monochrome
                else -> null
            }
            is UpdatesTab -> when (appTheme) {
                AppTheme.MONET -> eu.kanade.tachiyomi.R.drawable.ic_updates_dynamic
                AppTheme.CLOUDFLARE -> eu.kanade.tachiyomi.R.drawable.ic_updates_cloudflare
                AppTheme.COTTONCANDY -> eu.kanade.tachiyomi.R.drawable.ic_updates_cotton_candy
                AppTheme.DOOM -> eu.kanade.tachiyomi.R.drawable.ic_updates_doom
                AppTheme.GREEN_APPLE -> eu.kanade.tachiyomi.R.drawable.ic_updates_green_apple
                AppTheme.LAVENDER -> eu.kanade.tachiyomi.R.drawable.ic_updates_lavender
                AppTheme.MATRIX -> eu.kanade.tachiyomi.R.drawable.ic_updates_matrix
                AppTheme.MIDNIGHT_DUSK -> eu.kanade.tachiyomi.R.drawable.ic_updates_midnight_dusk
                AppTheme.MOCHA -> eu.kanade.tachiyomi.R.drawable.ic_updates_mocha
                AppTheme.SAPPHIRE -> eu.kanade.tachiyomi.R.drawable.ic_updates_sapphire
                AppTheme.NORD -> eu.kanade.tachiyomi.R.drawable.ic_updates_nord
                AppTheme.STRAWBERRY_DAIQUIRI -> eu.kanade.tachiyomi.R.drawable.ic_updates_strawberry_daiquiri
                AppTheme.TAKO -> eu.kanade.tachiyomi.R.drawable.ic_updates_tako
                AppTheme.TEALTURQUOISE -> eu.kanade.tachiyomi.R.drawable.ic_updates_teal_turquoise
                AppTheme.TIDAL_WAVE -> eu.kanade.tachiyomi.R.drawable.ic_updates_tidal_wave
                AppTheme.YINYANG -> eu.kanade.tachiyomi.R.drawable.ic_updates_yin_yang
                AppTheme.YOTSUBA -> eu.kanade.tachiyomi.R.drawable.ic_updates_yotsuba
                AppTheme.MONOCHROME -> eu.kanade.tachiyomi.R.drawable.ic_updates_monochrome
                else -> null
            }
            is BrowseTab -> when (appTheme) {
                AppTheme.MONET -> eu.kanade.tachiyomi.R.drawable.ic_browse_dynamic
                AppTheme.CLOUDFLARE -> eu.kanade.tachiyomi.R.drawable.ic_browse_cloudflare
                AppTheme.COTTONCANDY -> eu.kanade.tachiyomi.R.drawable.ic_browse_cotton_candy
                AppTheme.DOOM -> eu.kanade.tachiyomi.R.drawable.ic_browse_doom
                AppTheme.GREEN_APPLE -> eu.kanade.tachiyomi.R.drawable.ic_browse_green_apple
                AppTheme.LAVENDER -> eu.kanade.tachiyomi.R.drawable.ic_browse_lavender
                AppTheme.MATRIX -> eu.kanade.tachiyomi.R.drawable.ic_browse_matrix
                AppTheme.MIDNIGHT_DUSK -> eu.kanade.tachiyomi.R.drawable.ic_browse_midnight_dusk
                AppTheme.MOCHA -> eu.kanade.tachiyomi.R.drawable.ic_browse_mocha
                AppTheme.SAPPHIRE -> eu.kanade.tachiyomi.R.drawable.ic_browse_sapphire
                AppTheme.NORD -> eu.kanade.tachiyomi.R.drawable.ic_browse_nord
                AppTheme.STRAWBERRY_DAIQUIRI -> eu.kanade.tachiyomi.R.drawable.ic_browse_strawberry_daiquiri
                AppTheme.TAKO -> eu.kanade.tachiyomi.R.drawable.ic_browse_tako
                AppTheme.TEALTURQUOISE -> eu.kanade.tachiyomi.R.drawable.ic_browse_teal_turquoise
                AppTheme.TIDAL_WAVE -> eu.kanade.tachiyomi.R.drawable.ic_browse_tidal_wave
                AppTheme.YINYANG -> eu.kanade.tachiyomi.R.drawable.ic_browse_yin_yang
                AppTheme.YOTSUBA -> eu.kanade.tachiyomi.R.drawable.ic_browse_yotsuba
                AppTheme.MONOCHROME -> eu.kanade.tachiyomi.R.drawable.ic_browse_monochrome
                else -> null
            }
            is MoreTab -> when (appTheme) {
                AppTheme.MONET -> eu.kanade.tachiyomi.R.drawable.ic_settings_dynamic
                AppTheme.CLOUDFLARE -> eu.kanade.tachiyomi.R.drawable.ic_settings_cloudflare
                AppTheme.COTTONCANDY -> eu.kanade.tachiyomi.R.drawable.ic_settings_cotton_candy
                AppTheme.DOOM -> eu.kanade.tachiyomi.R.drawable.ic_settings_doom
                AppTheme.GREEN_APPLE -> eu.kanade.tachiyomi.R.drawable.ic_settings_green_apple
                AppTheme.LAVENDER -> eu.kanade.tachiyomi.R.drawable.ic_settings_lavender
                AppTheme.MATRIX -> eu.kanade.tachiyomi.R.drawable.ic_settings_matrix
                AppTheme.MIDNIGHT_DUSK -> eu.kanade.tachiyomi.R.drawable.ic_settings_midnight_dusk
                AppTheme.MOCHA -> eu.kanade.tachiyomi.R.drawable.ic_settings_mocha
                AppTheme.SAPPHIRE -> eu.kanade.tachiyomi.R.drawable.ic_settings_sapphire
                AppTheme.NORD -> eu.kanade.tachiyomi.R.drawable.ic_settings_nord
                AppTheme.STRAWBERRY_DAIQUIRI -> eu.kanade.tachiyomi.R.drawable.ic_settings_strawberry_daiquiri
                AppTheme.TAKO -> eu.kanade.tachiyomi.R.drawable.ic_settings_tako
                AppTheme.TEALTURQUOISE -> eu.kanade.tachiyomi.R.drawable.ic_settings_teal_turquoise
                AppTheme.TIDAL_WAVE -> eu.kanade.tachiyomi.R.drawable.ic_settings_tidal_wave
                AppTheme.YINYANG -> eu.kanade.tachiyomi.R.drawable.ic_settings_yin_yang
                AppTheme.YOTSUBA -> eu.kanade.tachiyomi.R.drawable.ic_settings_yotsuba
                AppTheme.MONOCHROME -> eu.kanade.tachiyomi.R.drawable.ic_settings_monochrome
                else -> null
            }
            else -> null
        }
    }

    @Composable
    private fun tabBadge(tab: eu.kanade.presentation.util.Tab): (@Composable () -> Unit)? {
        val context = LocalContext.current
        val count by produceState(initialValue = 0, tab) {
            val graph = context.appGraph
            when (tab) {
                is UpdatesTab -> {
                    combine(
                        graph.libraryPreferences.newShowUpdatesCount.changes(),
                        graph.libraryPreferences.newUpdatesCount.changes(),
                    ) { show, count ->
                        if (show) count else 0
                    }
                        .collectLatest { value = it }
                }

                is BrowseTab -> {
                    graph.sourcePreferences.extensionUpdatesCount.changes()
                        .collectLatest { value = it }
                }

                else -> value = 0
            }
        }
        if (count <= 0) return null
        return {
            Badge {
                val desc = when (tab) {
                    is UpdatesTab -> pluralStringResource(
                        MR.plurals.notification_chapters_generic,
                        count = count,
                        count,
                    )

                    is BrowseTab -> pluralStringResource(
                        MR.plurals.update_check_notification_ext_updates,
                        count = count,
                        count,
                    )

                    else -> null
                }
                Text(
                    text = count.toString(),
                    modifier = Modifier.semantics {
                        if (desc != null) contentDescription = desc
                    },
                )
            }
        }
    }

    suspend fun search(query: String) {
        librarySearchEvent.send(query)
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavEvent.send(show)
    }

    sealed interface Tab {
        data class Library(val mangaIdToOpen: Long? = null) : Tab
        data object Updates : Tab
        data object History : Tab
        data class Browse(val toExtensions: Boolean = false) : Tab
        data class More(val toDownloads: Boolean) : Tab
    }
}
