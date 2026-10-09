package com.simpleledger.app.ui.screens

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simpleledger.app.AppContainer
import com.simpleledger.app.BuildConfig
import com.simpleledger.app.R
import com.simpleledger.app.backup.BackupContents
import com.simpleledger.app.backup.BackupException
import com.simpleledger.app.domain.Dates
import com.simpleledger.app.ui.appContainer
import com.simpleledger.app.ui.components.Banner
import com.simpleledger.app.ui.components.BigButton
import com.simpleledger.app.ui.components.BigOutlinedButton
import com.simpleledger.app.ui.components.LedgerCard
import com.simpleledger.app.ui.components.ScreenScaffold
import com.simpleledger.app.ui.theme.LedgerColors
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val lastBackupAt = c.prefs.lastBackupAt

    var message by mutableStateOf<Int?>(null)
    var messageIsError by mutableStateOf(false)
        private set
    var pendingRestore by mutableStateOf<BackupContents?>(null)
        private set
    var entryCount by mutableStateOf(0)
        private set
    var chainOk by mutableStateOf<Boolean?>(null)
        private set
    var isEmpty by mutableStateOf(false)
        private set
    var pinEnabled by mutableStateOf(c.pin.isEnabled())
        private set
    var fingerprint by mutableStateOf(c.prefs.biometricEnabled)
        private set

    fun refresh() {
        pinEnabled = c.pin.isEnabled()
        fingerprint = c.prefs.biometricEnabled
        viewModelScope.launch {
            entryCount = c.repo.entryCount()
            isEmpty = c.repo.isEmpty()
            chainOk = c.repo.verifyChain() == null
        }
    }

    private fun show(res: Int, error: Boolean) {
        message = res
        messageIsError = error
    }

    fun share(start: (android.content.Intent) -> Unit) {
        viewModelScope.launch {
            try {
                start(c.backup.shareIntent())
            } catch (e: Exception) {
                show(R.string.backup_failed, true)
            }
        }
    }

    fun backupFileName() = c.backup.fileName()

    fun saveTo(uri: Uri) {
        viewModelScope.launch {
            try {
                c.backup.writeTo(uri)
                show(R.string.backup_saved, false)
            } catch (e: Exception) {
                show(R.string.backup_failed, true)
            }
        }
    }

    fun readForRestore(uri: Uri) {
        viewModelScope.launch {
            try {
                if (!c.repo.isEmpty()) throw BackupException(R.string.restore_not_empty)
                pendingRestore = c.backup.read(uri)
                message = null
            } catch (e: BackupException) {
                show(e.messageRes, true)
            } catch (e: Exception) {
                show(R.string.backup_bad_file, true)
            }
        }
    }

    fun confirmRestore() {
        val contents = pendingRestore ?: return
        pendingRestore = null
        viewModelScope.launch {
            try {
                c.backup.restore(contents)
                show(R.string.restore_done, false)
            } catch (e: BackupException) {
                show(e.messageRes, true)
            }
            refresh()
        }
    }

    fun cancelRestore() {
        pendingRestore = null
    }

    fun changeFingerprint(on: Boolean) {
        c.prefs.biometricEnabled = on
        fingerprint = on
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onPin: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(c) }
    val lastBackup by vm.lastBackupAt.collectAsStateWithLifecycle()
    val fingerprintAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(Unit) { vm.refresh() }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::saveTo)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::readForRestore)
    }

    ScreenScaffold(title = stringResource(R.string.settings), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            vm.message?.let { Banner(stringResource(it), critical = vm.messageIsError) }

            // Backup
            Text(stringResource(R.string.backup_section), style = MaterialTheme.typography.headlineSmall)
            val last = if (lastBackup == 0L) stringResource(R.string.never) else Dates.formatMillis(lastBackup)
            Text(stringResource(R.string.last_backup, last), style = MaterialTheme.typography.titleMedium)
            BigButton(stringResource(R.string.backup_share), {
                vm.share { intent ->
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        vm.message = R.string.backup_failed
                    }
                }
            })
            BigOutlinedButton(stringResource(R.string.backup_save), { saveLauncher.launch(vm.backupFileName()) })

            val pending = vm.pendingRestore
            if (pending != null) {
                LedgerCard {
                    val lastEntry = pending.entries.maxOfOrNull { it.occurredOn }?.let { Dates.formatIso(it) } ?: stringResource(R.string.never)
                    Text(
                        stringResource(R.string.restore_summary, pending.people.size, pending.entries.size, lastEntry),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    BigButton(stringResource(R.string.restore_confirm), vm::confirmRestore)
                    BigOutlinedButton(stringResource(R.string.go_back), vm::cancelRestore)
                }
            } else {
                BigOutlinedButton(stringResource(R.string.restore), { openLauncher.launch(arrayOf("*/*")) }, enabled = vm.isEmpty)
                if (!vm.isEmpty) {
                    Text(stringResource(R.string.restore_only_empty), style = MaterialTheme.typography.bodyLarge, color = LedgerColors.muted)
                }
            }

            // PIN lock
            Text(stringResource(R.string.security_section), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(if (vm.pinEnabled) R.string.pin_on else R.string.pin_off), style = MaterialTheme.typography.titleMedium)
            if (vm.pinEnabled) {
                BigOutlinedButton(stringResource(R.string.change_pin), { onPin("set") })
                BigOutlinedButton(stringResource(R.string.turn_off_pin), { onPin("off") })
                if (fingerprintAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 64.dp)) {
                        Text(stringResource(R.string.use_fingerprint), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = vm.fingerprint, onCheckedChange = vm::changeFingerprint)
                    }
                }
            } else {
                BigOutlinedButton(stringResource(R.string.turn_on_pin), { onPin("set") })
            }

            // Records check
            Text(stringResource(R.string.records_section), style = MaterialTheme.typography.headlineSmall)
            when (vm.chainOk) {
                true -> Text(stringResource(R.string.records_ok, vm.entryCount), style = MaterialTheme.typography.titleMedium, color = LedgerColors.gotText)
                false -> Text(stringResource(R.string.records_bad), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                null -> Unit
            }

            Text(stringResource(R.string.about, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = LedgerColors.muted)
        }
    }
}
