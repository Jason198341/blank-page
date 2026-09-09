package com.blank.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.blank.app.ui.calendar.ScheduleScreen
import com.blank.app.ui.home.TodayScreen
import com.blank.app.ui.item.CreateScreen
import com.blank.app.ui.item.KnowledgeScreen
import com.blank.app.ui.item.LibraryScreen
import com.blank.app.ui.recall.RecallScreen
import com.blank.app.ui.result.DiffScreen
import com.blank.app.ui.settings.SettingsScreen
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.Bg
import com.blank.app.ui.theme.TextTertiary

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("today", "오늘", Icons.Outlined.Inbox),
    Tab("schedule", "달력", Icons.Outlined.CalendarMonth),
    Tab("library", "서재", Icons.Outlined.Description)
)

@Composable
fun BlankNav(openRoundId: Long?, onOpenRoundConsumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // 알림 탭 → 재현 화면 직행
    LaunchedEffect(openRoundId) {
        openRoundId?.let {
            nav.navigate("recall/$it")
            onOpenRoundConsumed()
        }
    }

    // 재현 화면은 탭바를 숨긴다. 여기 들어오면 다른 데로 새지 않는다.
    val showTabs = route in TABS.map { it.route }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (showTabs) NavigationBar(containerColor = Bg) {
                TABS.forEach { tab ->
                    val selected = entry?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            unselectedIconColor = TextTertiary,
                            unselectedTextColor = TextTertiary,
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
            modifier = Modifier.padding(if (showTabs) padding else androidx.compose.foundation.layout.PaddingValues(0.dp))
        ) {
            composable("today") {
                TodayScreen(
                    onOpenRound = { nav.navigate("recall/$it") },
                    onOpenResult = { nav.navigate("diff/$it") },
                    onCreate = { nav.navigate("create") }
                )
            }
            composable("schedule") {
                ScheduleScreen(
                    onOpenRound = { nav.navigate("recall/$it") },
                    onOpenResult = { nav.navigate("diff/$it") }
                )
            }
            composable("library") {
                LibraryScreen(
                    onOpenItem = { nav.navigate("item/$it") },
                    onCreate = { nav.navigate("create") },
                    onSettings = { nav.navigate("settings") }
                )
            }
            composable("create") {
                CreateScreen(onDone = { nav.popBackStack() })
            }
            composable(
                "recall/{roundId}",
                arguments = listOf(navArgument("roundId") { type = NavType.LongType })
            ) { backStack ->
                val roundId = backStack.arguments?.getLong("roundId") ?: 0L
                RecallScreen(
                    roundId = roundId,
                    onSubmitted = { submissionId ->
                        nav.navigate("diff/$submissionId") {
                            popUpTo("recall/$roundId") { inclusive = true }
                        }
                    },
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
            composable(
                "item/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) { backStack ->
                KnowledgeScreen(
                    itemId = backStack.arguments?.getLong("itemId") ?: 0L,
                    onOpenResult = { nav.navigate("diff/$it") },
                    onBack = { nav.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
