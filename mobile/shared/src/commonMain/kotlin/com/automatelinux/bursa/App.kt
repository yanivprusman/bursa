package com.automatelinux.bursa

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.Api
import com.automatelinux.bursa.data.AppContext
import com.automatelinux.bursa.data.KeyValueStore
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.Platform
import com.automatelinux.bursa.nav.Screen
import com.automatelinux.bursa.ui.components.SyncNotice
import com.automatelinux.bursa.ui.screens.HomeScreen
import com.automatelinux.bursa.ui.screens.IndexScreen
import com.automatelinux.bursa.ui.screens.IndicesScreen
import com.automatelinux.bursa.ui.screens.SearchScreen
import com.automatelinux.bursa.ui.screens.SecurityScreen
import com.automatelinux.bursa.ui.theme.AppTheme

/**
 * Shared entry composable. The Android shell supplies what is platform-specific: where
 * small strings are stored, the system back button, and whether the app is on screen
 * ([active]) — nothing is fetched while it is not.
 */
@Composable
fun App(
    baseUrl: String,
    token: String,
    store: KeyValueStore,
    platform: Platform,
    font: FontFamily,
    active: Boolean,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val app = remember { AppContext(Api(baseUrl, token, store), store, platform, scope) }
    SideEffect { app.active = active }
    val nav = app.nav

    AppTheme(font) {
        CompositionLocalProvider(LocalApp provides app) {
            backHandler(nav.canGoBack) { nav.back() }
            val saveable = rememberSaveableStateHolder()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = nav.current,
                        transitionSpec = {
                            // The app is right-to-left: going deeper moves leftward, coming back rightward.
                            val way = nav.direction
                            (slideInHorizontally(tween(240)) { full -> -way * full / 7 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(200)) { full -> way * full / 7 } + fadeOut(tween(140)))
                        },
                        label = "screen",
                    ) { screen ->
                        Box(Modifier.fillMaxSize()) {
                            saveable.SaveableStateProvider(screen.toString()) {
                                when (screen) {
                                    Screen.Home -> HomeScreen()
                                    Screen.Search -> SearchScreen()
                                    Screen.Indices -> IndicesScreen()
                                    is Screen.Security -> SecurityScreen(screen.id, screen.name)
                                    is Screen.Index -> IndexScreen(screen.id, screen.name)
                                }
                            }
                        }
                    }
                    app.portfolio.syncError?.let { message ->
                        SyncNotice(
                            message,
                            onRetry = { app.portfolio.retry() },
                            onDismiss = { app.portfolio.dismissError() },
                            // Clear of the keyboard, the system bar and the home screen's tab bar.
                            modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(bottom = 84.dp),
                        )
                    }
                }
            }
        }
    }
}
