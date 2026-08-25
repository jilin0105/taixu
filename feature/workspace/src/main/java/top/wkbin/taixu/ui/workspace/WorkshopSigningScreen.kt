package top.wkbin.taixu.ui.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.wkbin.taixu.core.datastore.WorkshopKeystore
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.SectionHeader
import androidx.compose.ui.res.stringResource

/**
 * 工坊签名管理：创建 / 导入 / 删除 Android 签名（keystore）。
 * Release 构建时在构建类型选择弹窗中选用这里登记的签名。
 */
@Composable
fun WorkshopSigningScreen(onBack: () -> Unit, viewModel: WorkshopSigningViewModel = hiltViewModel()) {
    val keystores by viewModel.keystores.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WorkshopKeystore?>(null) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RuntimeTopBar(stringResource(R.string.workshop_signing_title), onBack, stringResource(R.string.workshop_signing_subtitle)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionHeader(stringResource(R.string.workshop_signing_section), stringResource(R.string.workshop_signing_section_description)) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RuntimeOutlinedButton(onClick = { showImport = true }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.workshop_signing_import)) }
                    RuntimeButton(onClick = { showCreate = true }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.workshop_signing_create)) }
                }
            }
            if (message != null) {
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Text(message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.fillMaxWidth().padding(10.dp))
                    }
                }
            }
            if (keystores.isEmpty()) {
                item {
                    RuntimeCard { Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.workshop_signing_empty), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.workshop_signing_empty_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
            } else {
                item { RuntimeCard(contentPadding = PaddingValues(0.dp)) {
                    keystores.forEachIndexed { index, keystore ->
                        KeystoreRow(keystore) { deleteTarget = keystore }
                        if (index != keystores.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                } }
            }
        }
    }

    if (showCreate) {
        CreateKeystoreDialog(
            busy = busy,
            onDismiss = { showCreate = false },
            onConfirm = { draft ->
                viewModel.createKeystore(draft) { showCreate = false }
            },
        )
    }
    if (showImport) {
        ImportKeystoreDialog(
            busy = busy,
            onDismiss = { showImport = false },
            onConfirm = { draft ->
                viewModel.importKeystore(draft) { showImport = false }
            },
        )
    }
    deleteTarget?.let { target ->
        RuntimeAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.workshop_signing_delete_title, target.name)) },
            text = { Text(stringResource(R.string.workshop_signing_delete_message)) },
            confirmButton = {
                RuntimeButton(onClick = { viewModel.deleteKeystore(target); deleteTarget = null }) { Text(stringResource(R.string.workspace_confirm_delete)) }
            },
            dismissButton = {
                RuntimeOutlinedButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.workspace_cancel)) }
            },
        )
    }
}

@Composable
private fun KeystoreRow(keystore: WorkshopKeystore, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
            RuntimeIcon(RuntimeIconName.Key, Modifier.padding(9.dp).size(20.dp), MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(keystore.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "alias ${keystore.alias} · ${keystore.validityYears}y · ${formatDate(keystore.createdAtMillis)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(keystore.fileName, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RuntimeOutlinedButton(onClick = onDelete) { Text(stringResource(R.string.workspace_delete)) }
    }
}

@Composable
private fun CreateKeystoreDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (WorkshopSigningCreationDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(WorkshopSigningCreationDraft()) }
    val valid = draft.name.isNotBlank() && draft.storePassword.length >= 6
    RuntimeAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.workshop_signing_create)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.workshop_signing_create_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SigningField(stringResource(R.string.workshop_signing_field_name), draft.name) { draft = draft.copy(name = it) }
                SigningField(stringResource(R.string.workshop_signing_field_alias), draft.alias, optional = true) { draft = draft.copy(alias = it) }
                SigningField(stringResource(R.string.workshop_signing_field_store_password), draft.storePassword, password = true) { draft = draft.copy(storePassword = it) }
                SigningField(stringResource(R.string.workshop_signing_field_key_password), draft.keyPassword, password = true, optional = true) { draft = draft.copy(keyPassword = it) }
                SigningField(stringResource(R.string.workshop_signing_field_validity), draft.validityYears.toString()) { text ->
                    draft = draft.copy(validityYears = text.filter(Char::isDigit).take(3).toIntOrNull() ?: WorkshopKeystore.DEFAULT_VALIDITY_YEARS)
                }
                SigningField(stringResource(R.string.workshop_signing_field_organization), draft.organization, optional = true) { draft = draft.copy(organization = it) }
            }
        },
        confirmButton = {
            RuntimeButton(onClick = { onConfirm(draft) }, enabled = valid && !busy) { Text(stringResource(R.string.workshop_signing_create)) }
        },
        dismissButton = {
            RuntimeOutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.workspace_cancel)) }
        },
    )
}

@Composable
private fun ImportKeystoreDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (WorkshopSigningImportDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(WorkshopSigningImportDraft()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val displayName = queryDisplayName(context, uri) ?: uri.toString().substringAfterLast('/')
            draft = draft.copy(
                uri = uri.toString(),
                displayName = displayName,
                name = draft.name.ifBlank { displayName.substringBeforeLast('.') },
            )
        }
    }
    val valid = draft.uri.isNotBlank() && draft.name.isNotBlank() && draft.storePassword.isNotBlank()
    RuntimeAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.workshop_signing_import)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.workshop_signing_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (draft.uri.isBlank()) {
                    RuntimeOutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.workshop_signing_pick_file))
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(draft.displayName.ifBlank { draft.uri.substringAfterLast('/') }, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            RuntimeOutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text(stringResource(R.string.workshop_signing_repick_file)) }
                        }
                    }
                }
                SigningField(stringResource(R.string.workshop_signing_field_name), draft.name) { draft = draft.copy(name = it) }
                SigningField(stringResource(R.string.workshop_signing_field_alias), draft.alias, optional = true) { draft = draft.copy(alias = it) }
                SigningField(stringResource(R.string.workshop_signing_field_store_password), draft.storePassword, password = true) { draft = draft.copy(storePassword = it) }
                SigningField(stringResource(R.string.workshop_signing_field_key_password), draft.keyPassword, password = true, optional = true) { draft = draft.copy(keyPassword = it) }
            }
        },
        confirmButton = {
            RuntimeButton(onClick = { onConfirm(draft) }, enabled = valid && !busy) { Text(stringResource(R.string.workshop_signing_import)) }
        },
        dismissButton = {
            RuntimeOutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.workspace_cancel)) }
        },
    )
}

@Composable
private fun SigningField(
    label: String,
    value: String,
    password: Boolean = false,
    optional: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (optional) "$label（可选）" else label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = if (password) FontFamily.Default else FontFamily.Monospace),
    )
}

private fun formatDate(millis: Long): String =
    if (millis <= 0L) "-" else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
}.getOrNull()
