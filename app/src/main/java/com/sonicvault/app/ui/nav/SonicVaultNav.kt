package com.sonicvault.app.ui.nav

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sonicvault.app.SonicVaultApplication
import com.sonicvault.app.data.preferences.OnboardingPreferences
import com.sonicvault.app.ui.component.SoundWaveLoader
import com.sonicvault.app.ui.screen.backup.BackupScreen
import com.sonicvault.app.ui.screen.home.HomeScreen
import com.sonicvault.app.ui.screen.onboarding.OnboardingScreen
import com.sonicvault.app.ui.screen.settings.SettingsScreen
import com.sonicvault.app.ui.screen.recovery.RecoveryScreen
import com.sonicvault.app.ui.screen.recovery.RestoreBroadcastScreen
import com.sonicvault.app.ui.screen.recovery.RestoreBroadcastViewModel
import com.sonicvault.app.ui.screen.recovery.RestoreCeremonyScreen
import com.sonicvault.app.ui.screen.recovery.RestoreCeremonyViewModel
import com.sonicvault.app.ui.screen.shamir.RecombineSeedScreen
import com.sonicvault.app.ui.screen.shamir.SplitSeedScreen
import com.sonicvault.app.ui.screen.voice.VoiceEnrollScreen
import com.sonicvault.app.ui.screen.faq.FaqScreen
import com.sonicvault.app.ui.screen.recovery.RecoveryGuideScreen
import com.sonicvault.app.ui.screen.message.ImageMessageScreen
import com.sonicvault.app.ui.screen.message.VoiceMessageScreen
import com.sonicvault.app.ui.screen.deadrop.DeadDropScreen
import com.sonicvault.app.ui.screen.sonicrequest.SonicRequestScreen
import com.sonicvault.app.ui.screen.noncepool.NoncePoolSetupScreen
import com.sonicvault.app.ui.screen.cnftdrop.CnftDropScreen
import com.sonicvault.app.ui.screen.matryoshka.MatryoshkaScreen
import com.sonicvault.app.ui.screen.presence.PresenceOracleScreen
import com.sonicvault.app.ui.screen.vote.GuardianVoteScreen
import com.sonicvault.app.util.AutoLockManager
import kotlinx.coroutines.delay

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Settings : Route("settings")
    data object Backup : Route("backup")
    data object Recovery : Route("recovery")
    data object SplitSeed : Route("split_seed")
    data object RecombineSeed : Route("recombine_seed")
    data object SoundTransfer : Route("sound_transfer")
    /** Legacy aliases kept for backward compat — both resolve to SoundTransfer */
    data object SoundTransmit : Route("sound_transmit")
    data object SoundReceive : Route("sound_receive")
    data object VoiceEnroll : Route("voice_enroll")
    data object Faq : Route("faq")
    data object RecoveryGuide : Route("recovery_guide")
    data object ImageMessage : Route("image_message")
    data object VoiceMessage : Route("voice_message")
    data object DeadDrop : Route("dead_drop")
    data object SonicRequest : Route("sonic_request")
    data object NoncePoolSetup : Route("nonce_pool_setup")
    data object Matryoshka : Route("matryoshka")
    data object AcousticRestore : Route("acoustic_restore")
    data object RestoreBroadcast : Route("restore_broadcast")
    data object CnftDrop : Route("cnft_drop")
    data object PresenceOracle : Route("presence_oracle")
    data object GuardianVote : Route("guardian_vote")
}

/** Nav host; sharedAudioUri from Share Target launches Recovery. */
@Composable
fun SonicVaultNav(
    navController: NavHostController = rememberNavController(),
    sharedAudioUri: Uri? = null
) {
    val context = LocalContext.current
    val onboardingPrefs = remember { OnboardingPreferences(context) }
    var showOnboarding by remember { mutableStateOf(!onboardingPrefs.hasSeenOnboarding) }

    /* Initial load: fluid wave-loader, Rams: tasteful, purposeful. ~1.2s */
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1200)
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        /* First-time onboarding: show 3 screens before Home */
        if (showOnboarding) {
            OnboardingScreen(
                onComplete = {
                    onboardingPrefs.hasSeenOnboarding = true
                    showOnboarding = false
                }
            )
        } else {
            /** Auto-lock: observe lock state and navigate to home when triggered. */
            val isLocked by AutoLockManager.isLocked.collectAsState()
            LaunchedEffect(isLocked) {
                if (isLocked) {
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Home.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            /* Share Target: when launched with shared audio, navigate to Recovery and pass Uri */
            LaunchedEffect(sharedAudioUri) {
                if (sharedAudioUri != null) {
                    navController.navigate(Route.Recovery.path) {
                        launchSingleTop = true
                    }
                }
            }

            NavHost(
        navController = navController,
        startDestination = Route.Home.path
    ) {
        composable(Route.Home.path) {
            HomeScreen(
                onCreateBackup = { navController.navigate(Route.Backup.path) },
                onRecover = { navController.navigate(Route.Recovery.path) },
                onTransmitSound = { navController.navigate("sound_transfer/transmit") },
                onReceiveSound = { navController.navigate("sound_transfer/receive") },
                onSettings = { navController.navigate(Route.Settings.path) }
            )
        }
        composable(Route.Settings.path) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onVoiceUnlock = { navController.navigate(Route.VoiceEnroll.path) },
                onFaq = { navController.navigate(Route.Faq.path) },
                onRecoveryGuide = { navController.navigate(Route.RecoveryGuide.path) },
                onDeadDrop = { navController.navigate(Route.DeadDrop.path) },
                onNoncePoolSetup = { navController.navigate(Route.NoncePoolSetup.path) },
                onMatryoshka = { navController.navigate(Route.Matryoshka.path) },
                onSplitSeed = { navController.navigate(Route.SplitSeed.path) },
                onRecombineSeed = { navController.navigate(Route.RecombineSeed.path) }
            )
        }
        composable(Route.Faq.path) {
            FaqScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.RecoveryGuide.path) {
            RecoveryGuideScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Backup.path) { backStackEntry ->
            BackupScreen(
                onBack = { navController.popBackStack() },
                savedStateHandle = backStackEntry.savedStateHandle
            )
        }
        composable(Route.Recovery.path) {
            RecoveryScreen(
                onBack = { navController.popBackStack() },
                initialUri = sharedAudioUri,
                onAcousticRestore = { navController.navigate(Route.AcousticRestore.path) },
                onRestoreBroadcast = { navController.navigate(Route.RestoreBroadcast.path) }
            )
        }
        composable(Route.RestoreBroadcast.path) {
            val app = LocalContext.current.applicationContext as SonicVaultApplication
            val factory = remember(app) {
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return RestoreBroadcastViewModel(app.backupRepository, app.userPreferences) as T
                    }
                }
            }
            RestoreBroadcastScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.AcousticRestore.path) {
            val app = LocalContext.current.applicationContext as SonicVaultApplication
            val factory = remember(app) {
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return RestoreCeremonyViewModel(app.recoverFromSoundUseCase) as T
                    }
                }
            }
            RestoreCeremonyScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.SplitSeed.path) {
            SplitSeedScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.RecombineSeed.path) {
            RecombineSeedScreen(onBack = { navController.popBackStack() })
        }
        composable("sound_transfer/{mode}") { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "transmit"
            DeadDropScreen(
                onBack = { navController.popBackStack() },
                initialMode = mode
            )
        }
        composable(Route.VoiceEnroll.path) {
            VoiceEnrollScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.ImageMessage.path) {
            ImageMessageScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.VoiceMessage.path) {
            VoiceMessageScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.SonicRequest.path) {
            SonicRequestScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.NoncePoolSetup.path) {
            NoncePoolSetupScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.DeadDrop.path) {
            DeadDropScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Matryoshka.path) {
            MatryoshkaScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.CnftDrop.path) {
            CnftDropScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.PresenceOracle.path) {
            PresenceOracleScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.GuardianVote.path) {
            GuardianVoteScreen(onBack = { navController.popBackStack() })
        }
    }
        }

        /* Loading overlay: clean surface + centered wave-loader. Rams: unobtrusive. */
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                SoundWaveLoader(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
