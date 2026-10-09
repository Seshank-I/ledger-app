package com.simpleledger.app.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.simpleledger.app.R
import com.simpleledger.app.ui.appContainer
import com.simpleledger.app.ui.components.BigOutlinedButton
import com.simpleledger.app.ui.components.LedgerCard
import com.simpleledger.app.ui.components.NumberPad
import com.simpleledger.app.ui.components.PinDots
import com.simpleledger.app.ui.components.ScreenScaffold
import kotlinx.coroutines.delay

private const val PIN_LENGTH = 4
private const val MAX_TRIES = 5
private const val WAIT_MS = 30_000L

/** Prompt, four dots, a message line and the number pad. Calls [onComplete] when four digits are typed. */
@Composable
private fun PinEntry(prompt: String, message: String?, enabled: Boolean, onComplete: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(prompt, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        PinDots(typed.length)
        Text(
            message.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        NumberPad(
            onDigit = { d ->
                if (enabled && typed.length < PIN_LENGTH) {
                    typed += d
                    if (typed.length == PIN_LENGTH) {
                        val pin = typed
                        typed = ""
                        onComplete(pin)
                    }
                }
            },
            onBackspace = { typed = typed.dropLast(1) },
            showDoubleZero = false,
        )
    }
}

/** Turn the PIN on, change it ("set"), or turn it off ("off"). Asks for the current PIN first when one exists. */
@Composable
fun PinScreen(mode: String, onDone: () -> Unit, onBack: () -> Unit) {
    val c = appContainer()
    val needsCurrent = remember { c.pin.isEnabled() }
    var stage by rememberSaveable { mutableStateOf(if (needsCurrent) "current" else "new") }
    var first by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }

    val prompt = when (stage) {
        "current" -> R.string.pin_current
        "new" -> R.string.pin_enter_new
        else -> R.string.pin_repeat
    }
    ScreenScaffold(title = stringResource(if (mode == "off") R.string.turn_off_pin else R.string.pin_setup_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PinEntry(stringResource(prompt), error?.let { stringResource(it) }, enabled = true) { pin ->
                error = null
                when (stage) {
                    "current" -> when {
                        !c.pin.verify(pin) -> error = R.string.pin_wrong
                        mode == "off" -> {
                            c.pin.disable()
                            onDone()
                        }
                        else -> stage = "new"
                    }
                    "new" -> {
                        first = pin
                        stage = "repeat"
                    }
                    else -> if (pin == first) {
                        c.pin.setPin(pin)
                        onDone()
                    } else {
                        error = R.string.pin_mismatch
                        first = ""
                        stage = "new"
                    }
                }
            }
        }
    }
}

/** Covers the whole app until the right PIN or fingerprint is given. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    var failures by rememberSaveable { mutableIntStateOf(0) }
    var waitUntil by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var wrong by remember { mutableStateOf(false) }
    var showForgot by rememberSaveable { mutableStateOf(false) }

    val fingerprintReady = remember {
        c.prefs.biometricEnabled &&
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val promptTitle = stringResource(R.string.fingerprint_title)
    val usePin = stringResource(R.string.use_pin)
    val askFingerprint = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlock()
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(promptTitle)
                    .setNegativeButtonText(usePin)
                    .setAllowedAuthenticators(BIOMETRIC_WEAK)
                    .build(),
            )
        }
    }

    LaunchedEffect(Unit) { if (fingerprintReady) askFingerprint() }
    LaunchedEffect(waitUntil) {
        while (System.currentTimeMillis() < waitUntil) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }

    val waiting = now < waitUntil
    val message = when {
        waiting -> stringResource(R.string.pin_wait, ((waitUntil - now) / 1000 + 1).toInt())
        wrong -> stringResource(R.string.pin_wrong)
        else -> null
    }

    ScreenScaffold(title = stringResource(R.string.app_name), onBack = null) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PinEntry(stringResource(R.string.pin_enter), message, enabled = !waiting) { pin ->
                if (c.pin.verify(pin)) {
                    failures = 0
                    wrong = false
                    onUnlock()
                } else {
                    failures++
                    wrong = true
                    if (failures >= MAX_TRIES) {
                        failures = 0
                        waitUntil = System.currentTimeMillis() + WAIT_MS
                        now = System.currentTimeMillis()
                    }
                }
            }
            if (fingerprintReady) BigOutlinedButton(stringResource(R.string.unlock_fingerprint_button), { askFingerprint() })
            BigOutlinedButton(stringResource(R.string.pin_forgot), { showForgot = !showForgot })
            if (showForgot) {
                LedgerCard { Text(stringResource(R.string.pin_forgot_help), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
