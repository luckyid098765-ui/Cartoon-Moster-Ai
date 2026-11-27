package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

object Routes {
    const val STUDIO = "studio"
    const val CHAT = "chat"
    const val PROJECTS = "projects"
    const val ADMIN = "admin"
    const val PLAYER = "player/{projectId}"

    fun player(projectId: String) = "player/$projectId"
}

private data class TopDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
    } else {
        key(user.uid) {
            AuthenticatedShell(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedShell(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val studioViewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = StudioViewModel.provideFactory(currentUserId)
    )
    val isAdmin by studioViewModel.isAdmin.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val destinations = remember(isAdmin) {
        buildList {
            add(
                TopDestination(
                    route = Routes.STUDIO,
                    labelRes = R.string.nav_studio,
                    selectedIcon = Icons.Filled.AutoAwesome,
                    unselectedIcon = Icons.Outlined.AutoAwesome,
                    testTag = "nav_studio"
                )
            )
            add(
                TopDestination(
                    route = Routes.CHAT,
                    labelRes = R.string.nav_chat,
                    selectedIcon = Icons.Filled.Forum,
                    unselectedIcon = Icons.Outlined.Forum,
                    testTag = "nav_chat"
                )
            )
            add(
                TopDestination(
                    route = Routes.PROJECTS,
                    labelRes = R.string.nav_projects,
                    selectedIcon = Icons.Filled.VideoLibrary,
                    unselectedIcon = Icons.Outlined.VideoLibrary,
                    testTag = "nav_projects"
                )
            )
            if (isAdmin) {
                add(
                    TopDestination(
                        route = Routes.ADMIN,
                        labelRes = R.string.nav_admin,
                        selectedIcon = Icons.Filled.AdminPanelSettings,
                        unselectedIcon = Icons.Outlined.AdminPanelSettings,
                        testTag = "nav_admin"
                    )
                )
            }
        }
    }

    val isTopLevel = destinations.any { it.route == currentRoute }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(Routes.STUDIO) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useRail) {
                    NavigationRail {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(Routes.STUDIO) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.STUDIO
                    ) {
                        composable(Routes.STUDIO) {
                            StudioScreen(
                                viewModel = studioViewModel,
                                onSignOut = {
                                    signOut(credentialManager, scope)
                                },
                                onOpenProjects = {
                                    navController.navigate(Routes.PROJECTS) {
                                        popUpTo(Routes.STUDIO) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onOpenPlayer = { projectId ->
                                    navController.navigate(Routes.player(projectId))
                                }
                            )
                        }
                        composable(Routes.CHAT) {
                            val chatVm: ChatViewModel = viewModel(
                                key = "chat_$currentUserId",
                                factory = ChatViewModel.provideFactory()
                            )
                            ChatScreen(
                                viewModel = chatVm,
                                onApplyStoryToStudio = { storyText, lang ->
                                    studioViewModel.updateStoryPrompt(storyText.take(2500))
                                    studioViewModel.selectLanguage(lang)
                                    navController.navigate(Routes.STUDIO) {
                                        popUpTo(Routes.STUDIO) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                        composable(Routes.PROJECTS) {
                            ProjectsScreen(
                                viewModel = studioViewModel,
                                onOpenStudio = {
                                    navController.navigate(Routes.STUDIO) {
                                        popUpTo(Routes.STUDIO) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onOpenPlayer = { projectId ->
                                    navController.navigate(Routes.player(projectId))
                                }
                            )
                        }
                        composable(Routes.ADMIN) {
                            val adminVm: AdminViewModel = viewModel(
                                key = "admin_$currentUserId",
                                factory = AdminViewModel.provideFactory()
                            )
                            AdminDashboardScreen(viewModel = adminVm)
                        }
                        composable(
                            route = Routes.PLAYER,
                            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                        ) { entry ->
                            val projectId = entry.arguments?.getString("projectId").orEmpty()
                            VideoPlayerScreen(
                                projectId = projectId,
                                viewModel = studioViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
