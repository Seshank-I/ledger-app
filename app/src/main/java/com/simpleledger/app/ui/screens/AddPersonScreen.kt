package com.simpleledger.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.simpleledger.app.R
import com.simpleledger.app.ui.appContainer
import com.simpleledger.app.ui.components.BigButton
import com.simpleledger.app.ui.components.BigOutlinedButton
import com.simpleledger.app.ui.components.BigTextField
import com.simpleledger.app.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

/** Opens the phone's contact list to pick one phone number. Needs no Contacts permission. */
private class PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private fun readContact(context: Context, uri: Uri): Pair<String, String?>? = try {
    val columns = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0).orEmpty() to c.getString(1) else null
    }
} catch (e: SecurityException) {
    null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPersonScreen(onBack: () -> Unit, onSaved: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(PickPhoneContact()) { uri ->
        if (uri != null) {
            val picked = readContact(context, uri)
            if (picked == null || picked.first.isBlank()) {
                error = R.string.contact_failed
            } else {
                name = picked.first
                phone = picked.second.orEmpty()
                error = null
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.add_person_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BigOutlinedButton(stringResource(R.string.pick_contact), {
                try {
                    picker.launch(Unit)
                } catch (e: ActivityNotFoundException) {
                    error = R.string.contact_failed
                }
            })
            BigTextField(name, { name = it; error = null }, stringResource(R.string.name_label))
            BigTextField(phone, { phone = it }, stringResource(R.string.phone_label), KeyboardType.Phone)
            error?.let { Text(stringResource(it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) }
            BigButton(stringResource(R.string.save), enabled = !saving, onClick = {
                if (name.isBlank()) {
                    error = R.string.name_required
                } else {
                    saving = true
                    scope.launch { onSaved(c.repo.addPerson(name, phone)) }
                }
            })
        }
    }
}
