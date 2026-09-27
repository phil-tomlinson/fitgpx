/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.philtomlinson.fitgpx.ui.about.AboutScreen
import io.github.philtomlinson.fitgpx.ui.editor.EditorScreen
import io.github.philtomlinson.fitgpx.ui.editor.EditorViewModel
import io.github.philtomlinson.fitgpx.ui.home.HomeScreen
import io.github.philtomlinson.fitgpx.ui.home.HomeViewModel
import io.github.philtomlinson.fitgpx.ui.settings.PrivacyZonesScreen
import io.github.philtomlinson.fitgpx.ui.settings.SettingsScreen
import io.github.philtomlinson.fitgpx.ui.settings.SettingsViewModel
import io.github.philtomlinson.fitgpx.ui.theme.FitGpxTheme
import io.github.philtomlinson.fitgpx.una.DemoWatchConnector
import io.github.philtomlinson.fitgpx.una.UnaSyncScreen
import io.github.philtomlinson.fitgpx.una.UnaSyncViewModel

class MainActivity : ComponentActivity() {

    private val container get() = (application as FitGpxApp).container

    internal val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory { initializer { HomeViewModel(application, container.queue, container.settings) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by homeViewModel.settings.collectAsStateWithLifecycle()
            FitGpxTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                val nav = rememberNavController()
                val settingsFactory = viewModelFactory { initializer { SettingsViewModel(application, container.settings, container.queue, container.unaSync) } }
                NavHost(
                    navController = nav,
                    startDestination = "home",
                    enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) + fadeIn(tween(280)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) + fadeOut(tween(280)) },
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onOpenItem = { id -> nav.navigate("editor/$id") },
                            onOpenSettings = { nav.navigate("settings") },
                            onOpenAbout = { nav.navigate("about") },
                            onOpenUna = { nav.navigate("una") },
                        )
                    }
                    composable("una") {
                        val vm: UnaSyncViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer {
                                    UnaSyncViewModel(
                                        container.unaSync, container.unaDevices, container.settings,
                                        demoConnector = if (BuildConfig.DEBUG) DemoWatchConnector(application) else null,
                                    )
                                }
                            },
                        )
                        UnaSyncScreen(vm, container.unaDevices, onBack = { nav.popBackStack() })
                    }
                    composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        val vm: EditorViewModel = viewModel(
                            factory = viewModelFactory { initializer { EditorViewModel(id, container.queue, container.settings) } },
                        )
                        EditorScreen(
                            viewModel = vm,
                            onClose = { nav.popBackStack() },
                            onExport = {
                                nav.popBackStack()
                                homeViewModel.requestExport(id)
                            },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel(factory = settingsFactory),
                            onBack = { nav.popBackStack() },
                            onOpenZones = { nav.navigate("zones") },
                            onOpenAbout = { nav.navigate("about") },
                        )
                    }
                    composable("zones") {
                        PrivacyZonesScreen(viewModel = viewModel(factory = settingsFactory), onBack = { nav.popBackStack() })
                    }
                    composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Files opened with, or shared to, FitGPX. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.streamExtra())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamListExtra()
            else -> emptyList()
        }
        homeViewModel.import(uris)
    }

    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_STREAM)

    private fun Intent.streamListExtra(): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
}
