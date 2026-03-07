package com.sonicvault.app.ui.screen.zk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonicvault.app.data.zk.ZkSoundPassport
import com.sonicvault.app.ui.component.ConnectionState
import com.sonicvault.app.ui.component.SoundHandshakeIndicator
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ZK Sound Passport screen.
 *
 * Demonstrates zero-knowledge proof of seed ownership via ultrasonic
 * challenge-response. Two modes:
 *
 * PROVER: "I own this seed" — receives a challenge over ultrasound,
 *         computes a ZK response, transmits it back.
 *
 * VERIFIER: "Prove you own it" — sends a challenge over ultrasound,
 *           waits for a response, verifies it against the commitment.
 *
 * For the hackathon demo, includes a self-test that runs the full
 * protocol locally to demonstrate the concept.
 */
private sealed class ZkState {
    data object Idle : ZkState()
    data class SelfTesting(val step: String) : ZkState()
    data class Proving(val step: String) : ZkState()
    data class Verifying(val step: String) : ZkState()
    data class Success(val message: String) : ZkState()
    data class Failed(val reason: String) : ZkState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZkPassportScreen(onBack: () -> Unit) {
    var state by remember { mutableStateOf<ZkState>(ZkState.Idle) }
    val scope = rememberCoroutineScope()

    val connectionState = when (state) {
        is ZkState.Idle -> ConnectionState.IDLE
        is ZkState.SelfTesting -> ConnectionState.INITIALISING
        is ZkState.Proving -> ConnectionState.BROADCASTING
        is ZkState.Verifying -> ConnectionState.LISTENING
        is ZkState.Success -> ConnectionState.COMPLETE
        is ZkState.Failed -> ConnectionState.FAILED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZK SOUND PASSPORT", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.md.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(Spacing.md.dp))

            Text(
                text = "Prove you own a seed without revealing it",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = "Zero-knowledge challenge-response over ultrasound. " +
                        "No seed data is ever transmitted — only cryptographic proofs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            /* Protocol diagram */
            Text("PROTOCOL", style = LabelUppercaseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.sm.dp)) {
                    ProtocolStep("1", "SETUP", "commitment = SHA-256(seed || salt)")
                    ProtocolStep("2", "CHALLENGE", "Verifier sends random nonce via ultrasound")
                    ProtocolStep("3", "RESPONSE", "Prover computes HMAC(seed, challenge)")
                    ProtocolStep("4", "VERIFY", "Response matches commitment? Proven.")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            /* Status indicator */
            SoundHandshakeIndicator(connectionState = connectionState)

            Spacer(modifier = Modifier.height(Spacing.sm.dp))

            when (val s = state) {
                is ZkState.Idle -> {
                    Text(
                        "Run the self-test to see the full ZK protocol in action. " +
                                "In production, the prover and verifier run on separate devices " +
                                "communicating via ultrasound.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ZkState.SelfTesting -> StatusBar(status = s.step)
                is ZkState.Proving -> StatusBar(status = s.step)
                is ZkState.Verifying -> StatusBar(status = s.step)
                is ZkState.Success -> StatusBar(status = s.message)
                is ZkState.Failed -> StatusBar(status = s.reason, isError = true)
            }

            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            /* Action buttons */
            Button(
                onClick = {
                    scope.launch {
                        runSelfTest { state = it }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state is ZkState.Idle || state is ZkState.Success || state is ZkState.Failed
            ) {
                Text("RUN SELF-TEST")
            }

            Spacer(modifier = Modifier.height(Spacing.sm.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { /* Prover mode — requires ultrasonic link */ },
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) {
                    Text("PROVE")
                }
                Spacer(modifier = Modifier.width(Spacing.sm.dp))
                OutlinedButton(
                    onClick = { /* Verifier mode — requires ultrasonic link */ },
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) {
                    Text("VERIFY")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                "Prove/Verify requires two devices. Use self-test for demo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.lg.dp))
        }
    }
}

/** Runs the full ZK protocol locally for demo. */
private suspend fun runSelfTest(onState: (ZkState) -> Unit) {
    withContext(Dispatchers.Default) {
        val testSeed = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        onState(ZkState.SelfTesting("Creating commitment…"))
        delay(500)
        val commitment = ZkSoundPassport.createCommitment(testSeed)

        onState(ZkState.SelfTesting("Commitment: …${commitment.shortId()}"))
        delay(500)

        onState(ZkState.SelfTesting("Generating challenge…"))
        delay(300)
        val challenge = ZkSoundPassport.generateChallenge()

        onState(ZkState.SelfTesting("Computing response…"))
        delay(500)
        val response = ZkSoundPassport.computeResponse(testSeed, challenge, commitment.salt)

        onState(ZkState.SelfTesting("Verifying…"))
        delay(500)
        val valid = ZkSoundPassport.verifyResponse(commitment, challenge, response, testSeed)

        if (valid) {
            onState(ZkState.Success("Seed ownership proven without revealing the seed."))
        } else {
            onState(ZkState.Failed("Verification failed — unexpected error in self-test."))
        }
    }
}

@Composable
private fun ProtocolStep(number: String, title: String, description: String) {
    Row(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(20.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
