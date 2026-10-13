package com.simpleledger.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpleledger.app.R
import com.simpleledger.app.data.Direction
import com.simpleledger.app.domain.InterestCalculator
import com.simpleledger.app.domain.Money
import com.simpleledger.app.ui.appContainer
import com.simpleledger.app.ui.components.Banner
import com.simpleledger.app.ui.components.BigButton
import com.simpleledger.app.ui.components.BigOutlinedButton
import com.simpleledger.app.ui.components.LedgerCard
import com.simpleledger.app.ui.components.ScreenScaffold
import com.simpleledger.app.ui.components.balanceColor
import com.simpleledger.app.ui.components.balanceSentence
import com.simpleledger.app.ui.theme.LedgerColors
import com.simpleledger.app.voice.VoiceCommand
import com.simpleledger.app.voice.Understood
import com.simpleledger.app.voice.VoiceUnderstanding
import com.simpleledger.app.voice.rememberVoiceInput
import java.time.LocalDate

/**
 * Shows what was heard and fills in anything missing (who, gave or got) with big buttons.
 * Once the entry is complete it moves on to the normal confirm screen, which reads it aloud;
 * nothing is ever saved from here directly.
 */
@Composable
fun VoiceScreen(
    heard: String,
    currentPersonId: String?,
    onBack: () -> Unit,
    onHeardAgain: (String) -> Unit,
    onComplete: (personId: String, direction: Direction, amountPaise: Long?) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val c = appContainer()
    val people by c.repo.observePeople().collectAsStateWithLifecycle(initialValue = null)
    val all = people ?: return
    // On-device Gemini where the phone has it, combined with the rules; rules alone elsewhere.
    val understood by produceState<Understood?>(initialValue = null, heard, all) {
        value = VoiceUnderstanding.merge(heard, all, currentPersonId, c.ai.understand(heard, all))
    }
    val result = understood
    if (result == null) {
        ScreenScaffold(title = stringResource(R.string.voice_title), onBack = onBack) { padding ->
            Text(stringResource(R.string.voice_understanding), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(padding).padding(16.dp))
        }
        return
    }
    val command = result.command
    var pickedPerson by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedDirection by rememberSaveable { mutableStateOf<Direction?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    val listen = rememberVoiceInput(onHeard = onHeardAgain, onUnavailable = { unavailable = true })

    val personId = pickedPerson ?: when (command) {
        is VoiceCommand.Record -> command.personId
        is VoiceCommand.AskBalance -> command.personId
        VoiceCommand.NotUnderstood -> null
    }
    val candidates = when (command) {
        is VoiceCommand.Record -> command.candidates
        is VoiceCommand.AskBalance -> command.candidates
        VoiceCommand.NotUnderstood -> emptyList()
    }.ifEmpty { all.filterNot { it.hidden }.map { it.id } }
    val direction = pickedDirection ?: (command as? VoiceCommand.Record)?.direction

    // A complete entry goes straight on to the confirm screen.
    if (command is VoiceCommand.Record && personId != null && direction != null) {
        LaunchedEffect(personId, direction) { onComplete(personId, direction, command.amountPaise) }
    }

    ScreenScaffold(title = stringResource(R.string.voice_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (unavailable) Banner(stringResource(R.string.voice_unavailable), critical = true)
            LedgerCard {
                Text(stringResource(R.string.voice_heard, heard), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (result.usedAi) R.string.voice_by_ai else R.string.voice_by_rules),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerColors.muted,
                )
            }

            when {
                all.isEmpty() -> Text(stringResource(R.string.voice_no_people), style = MaterialTheme.typography.titleMedium)

                command == VoiceCommand.NotUnderstood ->
                    Text(stringResource(R.string.voice_not_understood), style = MaterialTheme.typography.titleMedium)

                personId == null -> {
                    Text(stringResource(R.string.voice_who), style = MaterialTheme.typography.headlineSmall)
                    candidates.mapNotNull { id -> all.firstOrNull { it.id == id } }.forEach { p ->
                        BigOutlinedButton(p.name, { pickedPerson = p.id })
                    }
                }

                command is VoiceCommand.AskBalance -> BalanceAnswer(personId, onOpenPerson)

                direction == null -> {
                    (command as? VoiceCommand.Record)?.amountPaise?.let {
                        Text(Money.format(it), style = MaterialTheme.typography.headlineMedium)
                    }
                    Text(stringResource(R.string.voice_which_way), style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigButton(
                            stringResource(R.string.i_gave), { pickedDirection = Direction.GAVE },
                            modifier = Modifier.weight(1f), containerColor = LedgerColors.gaveButton, contentColor = Color.White,
                        )
                        BigButton(
                            stringResource(R.string.i_got), { pickedDirection = Direction.GOT },
                            modifier = Modifier.weight(1f), containerColor = LedgerColors.gotButton, contentColor = Color.White,
                        )
                    }
                }
            }

            BigOutlinedButton(stringResource(R.string.voice_try_again), listen)
            BigOutlinedButton(stringResource(R.string.voice_type_instead), onBack)
        }
    }
}

/** Answers "How much does Ramesh owe?" on screen and aloud. */
@Composable
private fun BalanceAnswer(personId: String, onOpenPerson: (String) -> Unit) {
    val c = appContainer()
    val person by remember(personId) { c.repo.observePerson(personId) }.collectAsStateWithLifecycle(initialValue = null)
    val entries by remember(personId) { c.repo.observeEntriesFor(personId) }.collectAsStateWithLifecycle(initialValue = null)
    val rates by remember(personId) { c.repo.observeRatesFor(personId) }.collectAsStateWithLifecycle(initialValue = null)
    val p = person ?: return
    val e = entries ?: return
    val r = rates ?: return
    val state = remember(e, r) { InterestCalculator.state(e, r, LocalDate.now()) }
    val sentence = balanceSentence(p.name, state.balancePaise)
    val interest = if (state.accruedPaise != 0L) stringResource(R.string.accrued_line, Money.format(state.accruedPaise)) else null

    LaunchedEffect(sentence, interest) { c.speaker.speak(listOfNotNull(sentence, interest).joinToString(". ")) }

    LedgerCard {
        Text(sentence, style = MaterialTheme.typography.headlineMedium, color = balanceColor(state.balancePaise))
        interest?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
    BigButton(stringResource(R.string.voice_open_person, p.name), { onOpenPerson(p.id) })
}
