package com.simpleledger.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simpleledger.app.AppContainer
import com.simpleledger.app.R
import com.simpleledger.app.data.PersonEntity
import com.simpleledger.app.domain.AccountState
import com.simpleledger.app.domain.Dates
import com.simpleledger.app.domain.InterestCalculator
import com.simpleledger.app.domain.Money
import com.simpleledger.app.domain.needsBackupReminder
import com.simpleledger.app.ui.appContainer
import com.simpleledger.app.ui.components.Banner
import com.simpleledger.app.ui.components.BigOutlinedButton
import com.simpleledger.app.ui.components.HighlightButton
import com.simpleledger.app.ui.components.LedgerCard
import com.simpleledger.app.ui.components.ScreenScaffold
import com.simpleledger.app.ui.components.balanceColor
import com.simpleledger.app.ui.theme.LedgerColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

data class PersonRow(val person: PersonEntity, val state: AccountState)

data class HomeUi(
    val loaded: Boolean = false,
    val visible: List<PersonRow> = emptyList(),
    val hidden: List<PersonRow> = emptyList(),
    val owedToYou: Long = 0,
    val youOwe: Long = 0,
    val backupReminder: Boolean = false,
    val lastBackupAt: Long = 0,
    val chainBroken: Boolean = false,
)

class HomeViewModel(c: AppContainer) : ViewModel() {
    private val chainBroken = MutableStateFlow(false)

    val ui: StateFlow<HomeUi> = combine(
        c.repo.observePeople(),
        c.repo.observeAllEntries(),
        c.repo.observeAllRates(),
        c.prefs.lastBackupAt,
        chainBroken,
    ) { people, entries, rates, lastBackup, broken ->
        val today = LocalDate.now()
        val entriesBy = entries.groupBy { it.personId }
        val ratesBy = rates.groupBy { it.personId }
        val rows = people
            .map { PersonRow(it, InterestCalculator.state(entriesBy[it.id].orEmpty(), ratesBy[it.id].orEmpty(), today)) }
            .sortedByDescending { abs(it.state.balancePaise) }
        HomeUi(
            loaded = true,
            visible = rows.filterNot { it.person.hidden },
            hidden = rows.filter { it.person.hidden },
            owedToYou = rows.sumOf { maxOf(it.state.balancePaise, 0L) },
            youOwe = rows.sumOf { maxOf(-it.state.balancePaise, 0L) },
            backupReminder = needsBackupReminder(entries, lastBackup, System.currentTimeMillis()),
            lastBackupAt = lastBackup,
            chainBroken = broken,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    init {
        viewModelScope.launch { chainBroken.value = c.repo.verifyChain() != null }
    }
}

@Composable
fun HomeScreen(onPerson: (String) -> Unit, onAddPerson: () -> Unit, onSettings: () -> Unit) {
    val c = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(c) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showHidden by rememberSaveable { mutableStateOf(false) }

    // No title here: the top bar holds the main action instead, highlighted in yellow.
    ScreenScaffold(
        title = null,
        onBack = null,
        actions = {
            HighlightButton(stringResource(R.string.add_person), onAddPerson, Modifier.weight(1f))
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (ui.chainBroken) {
                item { Banner(stringResource(R.string.banner_chain_broken), critical = true) }
            }
            if (ui.backupReminder) {
                item {
                    val text = if (ui.lastBackupAt == 0L) {
                        stringResource(R.string.banner_backup_never)
                    } else {
                        stringResource(R.string.banner_backup_old, Dates.formatMillis(ui.lastBackupAt))
                    }
                    Banner(text, critical = false, action = stringResource(R.string.backup_now) to onSettings)
                }
            }
            item {
                LedgerCard {
                    Text(stringResource(R.string.people_owe_you), style = MaterialTheme.typography.titleMedium)
                    Text(Money.format(ui.owedToYou), style = MaterialTheme.typography.headlineMedium, color = LedgerColors.gotText)
                    Text(stringResource(R.string.you_owe_total), style = MaterialTheme.typography.titleMedium)
                    Text(Money.format(ui.youOwe), style = MaterialTheme.typography.headlineMedium, color = LedgerColors.gaveText)
                }
            }
            if (ui.loaded && ui.visible.isEmpty() && ui.hidden.isEmpty()) {
                item { Text(stringResource(R.string.no_people), style = MaterialTheme.typography.titleMedium) }
            }
            items(ui.visible, key = { it.person.id }) { row -> PersonCard(row) { onPerson(row.person.id) } }
            if (ui.hidden.isNotEmpty()) {
                item {
                    val label = if (showHidden) {
                        stringResource(R.string.hide_hidden)
                    } else {
                        stringResource(R.string.show_hidden, ui.hidden.size)
                    }
                    BigOutlinedButton(label, { showHidden = !showHidden })
                }
                if (showHidden) {
                    items(ui.hidden, key = { it.person.id }) { row -> PersonCard(row) { onPerson(row.person.id) } }
                }
            }
        }
    }
}

@Composable
private fun PersonCard(row: PersonRow, onClick: () -> Unit) {
    val b = row.state.balancePaise
    LedgerCard(onClick = onClick) {
        Text(row.person.name, style = MaterialTheme.typography.titleLarge)
        val sentence = when {
            b > 0 -> stringResource(R.string.row_they_owe, Money.format(b))
            b < 0 -> stringResource(R.string.row_you_owe, Money.format(b))
            else -> stringResource(R.string.balance_settled)
        }
        Text(sentence, style = MaterialTheme.typography.titleMedium, color = balanceColor(b))
        if (row.state.accruedPaise != 0L) {
            Text(
                stringResource(R.string.row_plus_interest, Money.format(row.state.accruedPaise)),
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerColors.muted,
            )
        }
    }
}
