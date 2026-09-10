package com.blank.app.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.blank.app.ui.calendar.ScheduleScreen
import com.blank.app.ui.home.TodayScreen
import com.blank.app.ui.note.NoteEditScreen
import com.blank.app.ui.note.NoteScreen
import com.blank.app.ui.onboarding.OnboardingScreen
import com.blank.app.ui.recall.RecallScreen
import com.blank.app.ui.result.DiffScreen
import com.blank.app.ui.settings.SettingsScreen
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.Bg
import com.blank.app.ui.theme.BgReading
import com.blank.app.ui.theme.BgRecall
import com.blank.app.ui.theme.TextTertiary
import com.blank.app.ui.vault.VaultBrowserScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("today", "오늘", Icons.Outlined.Inbox),
    Tab("calendar", "달력", Icons.Outlined.CalendarMonth),
    Tab("vault", "볼트", Icons.Outlined.FolderOpen)
)

@Composable
fun BlankNav(
    hasVault: Boolean,
    onPickVault: () -> Unit,
    openRoundId: Long?,
    onOpenRoundConsumed: () -> Unit
) {
    if (!hasVault) { OnboardingScreen(onPickVault = onPickVault); return }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(openRoundId) {
        openRoundId?.let { nav.navigate("recall/$it"); onOpenRoundConsumed() }
    }

    val showTabs = route in TABS.map { it.route }

    Scaffold(
        containerColor = when {
            route?.startsWith("recall/") == true -> BgRecall
            route?.startsWith("note/") == true -> BgReading
            else -> Bg
        },
        bottomBar = {
            if (showTabs) NavigationBar(containerColor = Bg) {
                TABS.forEach { tab ->
                    val selected = entry?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent, selectedTextColor = Accent,
                            unselectedIconColor = TextTertiary, unselectedTextColor = TextTertiary,
                            indicatorColor = Bg
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "today",
            modifier = Modifier.padding(padding)
        ) {
            composable("today") {
                TodayScreen(
                    onOpenRound = { nav.navigate("recall/$it") },
                    onOpenResult = { nav.navigate("diff/$it") },
                    onCreate = { nav.navigate("edit") }
                )
            }
            composable("calendar") {
                ScheduleScreen(
                    onOpenRound = { nav.navigate("recall/$it") },
                    onOpenResult = { nav.navigate("diff/$it") }
                )
            }
            composable("vault") {
                VaultBrowserScreen(
                    onOpenNote = { nav.navigate("note/$it") },
                    onCreate = { nav.navigate("edit") },
                    onSettings = { nav.navigate("settings") }
                )
            }
            composable("edit") {
                NoteEditScreen(
                    noteId = null,
                    onDone = { id -> nav.navigate("note/$id") { popUpTo("edit") { inclusive = true } } },
                    onCancel = { nav.popBackStack() }
                )
            }
            composable(
                "edit/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.StringType })
            ) { backStack ->
                NoteEditScreen(
                    noteId = backStack.arguments?.getString("noteId"),
                    onDone = { nav.popBackStack() },
                    onCancel = { nav.popBackStack() }
                )
            }
            composable(
                "note/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.StringType })
            ) { backStack ->
                NoteScreen(
                    noteId = backStack.arguments?.getString("noteId").orEmpty(),
                    onOpenNote = { nav.navigate("note/$it") },
                    onEdit = { nav.navigate("edit/$it") },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                "recall/{roundId}",
                arguments = listOf(navArgument("roundId") { type = NavType.LongType })
            ) { backStack ->
                val roundId = backStack.arguments?.getLong("roundId") ?: 0L
                RecallScreen(
                    roundId = roundId,
                    onSubmitted = { sid -> nav.navigate("diff/$sid") { popUpTo("recall/$roundId") { inclusive = true } } },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                "diff/{submissionId}",
                arguments = listOf(navArgument("submissionId") { type = NavType.LongType })
            ) { backStack ->
                DiffScreen(
                    submissionId = backStack.arguments?.getLong("submissionId") ?: 0L,
                    onDone = { nav.popBackStack() }
                )
            }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
