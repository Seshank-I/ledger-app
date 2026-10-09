package com.simpleledger.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleledger.app.R
import com.simpleledger.app.ui.theme.LedgerColors
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

/** Every screen: a tall top bar with a labelled Back button (no icon-only buttons). */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .heightIn(min = 72.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 64.dp)) {
                            Text(stringResource(R.string.back), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    actions()
                }
            }
        },
        content = content,
    )
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun BigOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun LedgerCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, shape = RoundedCornerShape(16.dp), content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, shape = RoundedCornerShape(16.dp), content = inner)
    }
}

@Composable
fun Banner(text: String, critical: Boolean, action: Pair<String, () -> Unit>? = null) {
    Surface(
        color = if (critical) LedgerColors.criticalBackground else LedgerColors.warningBackground,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            action?.let { (label, onClick) -> BigButton(label, onClick) }
        }
    }
}

@Composable
fun BigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        textStyle = MaterialTheme.typography.titleLarge,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Large on-screen number pad, so nobody has to fight the phone keyboard to type an amount or PIN. */
@Composable
fun NumberPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, showDoubleZero: Boolean) {
    val deleteLabel = stringResource(R.string.delete_digit)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c -> PadKey(c.toString(), Modifier.weight(1f)) { onDigit(c) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showDoubleZero) {
                PadKey("00", Modifier.weight(1f)) {
                    onDigit('0')
                    onDigit('0')
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            PadKey("0", Modifier.weight(1f)) { onDigit('0') }
            PadKey("⌫", Modifier.weight(1f), description = deleteLabel, onClick = onBackspace)
        }
    }
}

@Composable
private fun PadKey(label: String, modifier: Modifier, description: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 76.dp)
            .semantics { if (description != null) contentDescription = description },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 76.dp)) {
            Text(label, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(4) { i ->
            Surface(
                shape = CircleShape,
                color = if (i < filled) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                border = BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.size(28.dp),
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayUtc = LocalDate.now().toEpochDay() * DAY_MS
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * DAY_MS,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(it / DAY_MS)) }
                onDismiss()
            }) { Text(stringResource(R.string.ok), style = MaterialTheme.typography.titleMedium) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.go_back), style = MaterialTheme.typography.titleMedium) }
        },
    ) {
        DatePicker(state = state)
    }
}
