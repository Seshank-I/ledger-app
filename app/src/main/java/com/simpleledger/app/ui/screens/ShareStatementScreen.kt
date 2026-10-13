package com.simpleledger.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simpleledger.app.AppContainer
import com.simpleledger.app.R
import com.simpleledger.app.domain.InterestCalculator
import com.simpleledger.app.domain.historyRows
import com.simpleledger.app.share.StatementImage
import com.simpleledger.app.share.StatementSharer
import com.simpleledger.app.ui.appContainer
import com.simpleledger.app.ui.components.Banner
import com.simpleledger.app.ui.components.BigButton
import com.simpleledger.app.ui.components.BigOutlinedButton
import com.simpleledger.app.ui.components.ChoiceButton
import com.simpleledger.app.ui.components.ScreenScaffold
import com.simpleledger.app.ui.theme.LedgerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class ShareStatementViewModel(c: AppContainer, personId: String) : ViewModel() {
    data class Data(
        val person: com.simpleledger.app.data.PersonEntity? = null,
        val rows: List<com.simpleledger.app.domain.EntryRow> = emptyList(),
        val state: com.simpleledger.app.domain.AccountState = com.simpleledger.app.domain.AccountState(),
    )

    val data = combine(
        c.repo.observePerson(personId),
        c.repo.observeEntriesFor(personId),
        c.repo.observeRatesFor(personId),
    ) { p, entries, rates ->
        Data(p, historyRows(entries), InterestCalculator.state(entries, rates, LocalDate.now()))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Data())

    var count by mutableIntStateOf(DEFAULT_ROWS)
    var includeNotes by mutableStateOf(true)
    var preview by mutableStateOf<Bitmap?>(null)
        private set

    fun refreshPreview(context: android.content.Context) {
        val d = data.value
        val person = d.person ?: return
        val shown = count.coerceAtMost(d.rows.size)
        viewModelScope.launch {
            // Only the first page is previewed; the rest look the same.
            preview = withContext(Dispatchers.Default) {
                StatementImage.render(context, person, d.rows, d.state, shown, includeNotes).first()
            }
        }
    }

    suspend fun files(context: android.content.Context): Pair<List<android.net.Uri>, String>? {
        val d = data.value
        val person = d.person ?: return null
        val pages = withContext(Dispatchers.Default) {
            StatementImage.render(context, person, d.rows, d.state, count.coerceAtMost(d.rows.size), includeNotes)
        }
        val uris = StatementSharer.save(context, pages, person.name)
        return uris to StatementImage.balanceText(context, person.name, d.state.balancePaise)
    }

    companion object {
        const val DEFAULT_ROWS = 10
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareStatementScreen(personId: String, onBack: () -> Unit, onEditPerson: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: ShareStatementViewModel = viewModel(key = "share-$personId") { ShareStatementViewModel(c, personId) }
    val data by vm.data.collectAsStateWithLifecycle()
    val total = data.rows.size
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(data, vm.count, vm.includeNotes) { vm.refreshPreview(context) }

    ScreenScaffold(title = stringResource(R.string.share_title_for, data.person?.name.orEmpty()), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (total == 0) {
                Text(stringResource(R.string.no_entries_short), style = MaterialTheme.typography.headlineSmall)
                BigButton(stringResource(R.string.go_back), onBack)
                return@Column
            }
            message?.let { Banner(it, critical = true) }

            Text(stringResource(R.string.share_how_many), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(5, 10, 20).filter { it < total }.forEach { n ->
                    ChoiceButton(stringResource(R.string.share_last_n, n), selected = vm.count == n) { vm.count = n }
                }
                ChoiceButton(stringResource(R.string.share_all_n, total), selected = vm.count >= total) { vm.count = total }
            }
            // Any exact number with large − and + buttons.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceButton("−", selected = false, enabled = vm.count > 1) { vm.count = (vm.count.coerceAtMost(total) - 1).coerceAtLeast(1) }
                Text(
                    stringResource(R.string.share_rows_count, vm.count.coerceAtMost(total)),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                ChoiceButton("+", selected = false, enabled = vm.count < total) { vm.count = (vm.count + 1).coerceAtMost(total) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(R.string.share_include_notes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = vm.includeNotes, onCheckedChange = { vm.includeNotes = it })
            }
            if (vm.count.coerceAtMost(total) > StatementImage.ROWS_PER_PAGE) {
                Text(
                    stringResource(R.string.share_pages_note, StatementImage.ROWS_PER_PAGE),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LedgerColors.muted,
                )
            }

            val name = data.person?.name.orEmpty()
            val phone = data.person?.phone
            val hasWhatsAppNumber = StatementSharer.whatsAppId(phone) != null
            val notInstalled = stringResource(R.string.share_no_whatsapp)
            val failedTemplate = stringResource(R.string.share_failed)
            fun send(toPerson: Boolean) {
                busy = true
                message = null
                scope.launch {
                    vm.files(context)?.let { (uris, caption) ->
                        when (val r = StatementSharer.sendToWhatsApp(context, uris, caption, phone.takeIf { toPerson })) {
                            StatementSharer.Result.Opened -> Unit
                            StatementSharer.Result.NotInstalled -> {
                                message = notInstalled
                                StatementSharer.sendAnyWay(context, uris, caption)
                            }
                            is StatementSharer.Result.Failed -> message = failedTemplate.format(r.reason)
                        }
                    }
                    busy = false
                }
            }

            if (hasWhatsAppNumber) {
                // Straight to this person's own WhatsApp chat, using the number saved for them.
                BigButton(
                    stringResource(R.string.share_to_person, name),
                    { send(toPerson = true) },
                    enabled = !busy,
                    containerColor = LedgerColors.gotButton,
                    contentColor = Color.White,
                )
                BigOutlinedButton(stringResource(R.string.share_to_someone_else), { send(toPerson = false) }, enabled = !busy)
            } else {
                BigButton(
                    stringResource(R.string.share_whatsapp),
                    { send(toPerson = false) },
                    enabled = !busy,
                    containerColor = LedgerColors.gotButton,
                    contentColor = Color.White,
                )
                Text(stringResource(R.string.share_add_phone_hint, name), style = MaterialTheme.typography.bodyLarge, color = LedgerColors.muted)
                BigOutlinedButton(stringResource(R.string.share_add_phone, name), onEditPerson)
            }
            BigOutlinedButton(
                stringResource(R.string.share_other),
                {
                    busy = true
                    scope.launch {
                        vm.files(context)?.let { (uris, caption) -> StatementSharer.sendAnyWay(context, uris, caption) }
                        busy = false
                    }
                },
                enabled = !busy,
            )

            Text(stringResource(R.string.share_preview), style = MaterialTheme.typography.titleLarge)
            vm.preview?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_preview),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
