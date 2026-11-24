package com.example.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.core.remote.DeviceIdentityStore
import com.example.core.remote.DiyaRemoteRepository
import com.example.core.remote.SecureWssRelayClient
import com.example.model.DiyaUiState
import com.example.ui.components.ChatScreen
import com.example.ui.components.HistoryScreen
import com.example.ui.components.ShortcutPreviewDialog
import com.example.ui.screens.ConnectionDashboardScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiyaAuthScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.signOutDiyaSession
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.diyaHudBackground
import com.example.viewmodel.DiyaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object DiyaRoutes {
    const val CORE = "diya_core_dashboard"
    const val CHAT = "diya_chat_conversation"
    const val BRIDGE = "diya_connection_bridge"
    const val HISTORY = "diya_command_history"
    const val SETTINGS = "diya_settings"
}

private data class DiyaNavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val diyaDestinations = listOf(
    DiyaNavDestination(
        route = DiyaRoutes.CORE,
        labelRes = R.string.nav_core,
        selectedIcon = Icons.Filled.Hub,
        unselectedIcon = Icons.Outlined.Hub,
        testTag = "nav_tab_core"
    ),
    DiyaNavDestination(
        route = DiyaRoutes.CHAT,
        labelRes = R.string.nav_chat,
        selectedIcon = Icons.AutoMirrored.Filled.Chat,
        unselectedIcon = Icons.AutoMirrored.Outlined.Chat,
        testTag = "nav_tab_chat"
    ),
    DiyaNavDestination(
        route = DiyaRoutes.BRIDGE,
        labelRes = R.string.nav_bridge,
        selectedIcon = Icons.Filled.Devices,
        unselectedIcon = Icons.Outlined.Devices,
        testTag = "nav_tab_bridge"
    ),
    DiyaNavDestination(
        route = DiyaRoutes.HISTORY,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history"
    ),
    DiyaNavDestination(
        route = DiyaRoutes.SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
)

@Composable
fun DiyaApp() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        DiyaAuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            DiyaAuthenticatedApp(user = user)
        }
    }
}

@Composable
private fun DiyaAuthenticatedApp(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: DiyaViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = DiyaRemoteRepository(db)
                val identityStore = DeviceIdentityStore(app)
                val relayClient = SecureWssRelayClient()
                DiyaViewModel(
                    currentUserId = user.uid,
                    currentUserEmail = user.email.orEmpty(),
                    remoteRepository = repo,
                    identityStore = identityStore,
                    relayClient = relayClient
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val remoteState by viewModel.remoteState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navigateToRoute: (String) -> Unit = { targetRoute ->
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .diyaHudBackground(glowIntensity = uiState.settings.holographicGlowIntensity)
    ) {
        val useNavigationRail = maxWidth >= 840.dp

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = ObsidianPanel.copy(alpha = 0.94f),
                        contentColor = TextPrimary,
                        tonalElevation = 0.dp,
                        modifier = Modifier.border(
                            width = 0.8.dp,
                            color = ElectricCyan.copy(alpha = 0.24f)
                        )
                    ) {
                        diyaDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToRoute(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = GlassSurfaceElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextMuted
                                ),
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
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MidnightNavy.copy(alpha = 0.92f),
                        contentColor = TextPrimary,
                        modifier = Modifier
                            .fillMaxHeight()
                            .border(width = 0.8.dp, color = ElectricCyan.copy(alpha = 0.22f)),
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(ElectricCyan)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = "DIYA",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                )
                            }
                        }
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        diyaDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToRoute(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = GlassSurfaceElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = DiyaRoutes.CORE,
                    enterTransition = { fadeIn(animationSpec = tween(220)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) },
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    composable(DiyaRoutes.CORE) {
                        DashboardScreen(
                            uiState = uiState,
                            onMicClicked = viewModel::onMicButtonTapped,
                            onVoiceStateSelected = viewModel::setVoiceStatePreview,
                            onShortcutClicked = { shortcut ->
                                if (shortcut.isSettings) {
                                    navigateToRoute(DiyaRoutes.SETTINGS)
                                } else {
                                    viewModel.openShortcutPreview(shortcut)
                                }
                            },
                            onOpenChatTab = { navigateToRoute(DiyaRoutes.CHAT) },
                            onOpenHistoryTab = { navigateToRoute(DiyaRoutes.HISTORY) },
                            onDraftChanged = viewModel::onChatDraftChanged,
                            onSendMessage = viewModel::sendPreviewMessage,
                            onHistoryFilterChanged = viewModel::onHistoryFilterChanged,
                            onClearHistory = viewModel::clearCommandHistory
                        )
                    }

                    composable(DiyaRoutes.CHAT) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SubScreenHeader(
                                title = "CONVERSATION PANEL",
                                uiState = uiState
                            )
                            ChatScreen(
                                messages = uiState.messages,
                                draftText = uiState.chatDraft,
                                language = uiState.settings.primaryLanguage,
                                onDraftChanged = viewModel::onChatDraftChanged,
                                onSendMessage = viewModel::sendPreviewMessage,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    composable(DiyaRoutes.BRIDGE) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SubScreenHeader(
                                title = "REMOTE BRIDGE & PAIRING",
                                uiState = uiState
                            )
                            ConnectionDashboardScreen(
                                remoteState = remoteState,
                                onRelayUrlChanged = viewModel::onRelayUrlDraftChanged,
                                onSaveAndConnectRelay = viewModel::saveAndConnectRelay,
                                onDisconnectRelay = viewModel::disconnectRelay,
                                onGeneratePairingCode = viewModel::generatePairingCode,
                                onRevokePairingCode = viewModel::revokeActivePairingCode,
                                onRequestAction = viewModel::requestDesktopAction,
                                onOpenConfirmation = viewModel::openConfirmationDialog,
                                onConfirmCommand = viewModel::confirmPendingCommand,
                                onCancelCommand = viewModel::cancelPendingCommand,
                                onDismissConfirmation = viewModel::dismissConfirmationDialog,
                                onClearBanner = viewModel::clearRemoteBanner,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    composable(DiyaRoutes.HISTORY) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SubScreenHeader(
                                title = "COMMAND HISTORY",
                                uiState = uiState
                            )
                            HistoryScreen(
                                entries = uiState.commandHistory,
                                selectedFilter = uiState.historyFilterTag,
                                onFilterChanged = viewModel::onHistoryFilterChanged,
                                onClearHistory = viewModel::clearCommandHistory,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    composable(DiyaRoutes.SETTINGS) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SubScreenHeader(
                                title = "CORE SETTINGS",
                                uiState = uiState
                            )
                            SettingsScreen(
                                settings = uiState.settings,
                                remoteState = remoteState,
                                geminiProtocolSpec = viewModel.getGeminiProtocolSpec(),
                                desktopSecurityProfile = viewModel.getDesktopSecurityProfile(),
                                onLanguageSelected = viewModel::updateLanguage,
                                onSettingsUpdated = viewModel::updateSettings,
                                onSignOutClicked = {
                                    viewModel.disconnectRelay()
                                    signOutDiyaSession(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        uiState.selectedShortcutSheet?.let { shortcut ->
            ShortcutPreviewDialog(
                shortcut = shortcut,
                onDismiss = viewModel::dismissShortcutSheet
            )
        }
    }
}

@Composable
private fun SubScreenHeader(
    title: String,
    uiState: DiyaUiState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "DIYA AI • $title",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Text(
            text = uiState.currentTimeText,
            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
        )
    }
}
