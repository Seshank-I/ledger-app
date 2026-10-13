package com.simpleledger.app.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.simpleledger.app.R
import com.simpleledger.app.ui.appContainer

/**
 * Opens the phone's own speech recognizer (Google or Samsung). The phone records the voice,
 * so this app needs no microphone permission.
 */
private class RecognizeSpeech : ActivityResultContract<RecognizeSpeech.Input, String?>() {
    data class Input(val language: String, val prompt: String)

    override fun createIntent(context: Context, input: Input) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, input.language)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, input.prompt)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null
}

/** Returns a function that starts listening; [onHeard] gets the words, [onUnavailable] runs if the phone has no recognizer. */
@Composable
fun rememberVoiceInput(onHeard: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val c = appContainer()
    val prompt = stringResource(R.string.voice_prompt)
    val launcher = rememberLauncherForActivityResult(RecognizeSpeech()) { text ->
        if (!text.isNullOrBlank()) onHeard(text)
    }
    return {
        try {
            launcher.launch(RecognizeSpeech.Input(c.prefs.voiceLanguage, prompt))
        } catch (e: ActivityNotFoundException) {
            onUnavailable()
        }
    }
}
