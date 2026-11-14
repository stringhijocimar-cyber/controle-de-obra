package br.com.obracontrol

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val attachmentsSaver = Saver<List<DocumentAttachment>, ArrayList<Bundle>>(
    save = { items -> ArrayList(items.map { item ->
        Bundle().apply {
            putString("id", item.id)
            putString("name", item.name)
            putString("type", item.type.name)
            putLong("size", item.size)
        }
    }) },
    restore = { items -> items.map { bundle ->
        DocumentAttachment(checkNotNull(bundle.getString("id")), checkNotNull(bundle.getString("name")),
            DocumentType.valueOf(checkNotNull(bundle.getString("type"))), bundle.getLong("size"))
    } },
)

@Composable
internal fun rememberAttachments(initial: List<DocumentAttachment>) =
    rememberSaveable(stateSaver = attachmentsSaver) { mutableStateOf(initial) }

internal val documentPickerTypes = arrayOf("image/*", "application/pdf", "application/octet-stream")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocumentEditor(
    mode: RecognitionScope,
    attachments: List<DocumentAttachment>,
    change: (List<DocumentAttachment>) -> Unit,
    busyChange: (Boolean) -> Unit,
    applyAmount: (MoneyCandidate, MoneyField) -> Unit,
) {
    val context = LocalContext.current
    val files = remember(context) { DocumentFiles(context.applicationContext) }
    val recognizer = remember(files) { DocumentRecognizer(files) }
    val isPurchase = mode == RecognitionScope.PURCHASE
    val scope = rememberCoroutineScope()
    var photoName by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }
    var scanned by remember { mutableStateOf<DocumentAttachment?>(null) }
    var amounts by remember { mutableStateOf(emptyList<MoneyCandidate>()) }
    var nextPage by remember { mutableStateOf<Int?>(null) }
    var review by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(12) }
    var selected by remember { mutableStateOf<MoneyCandidate?>(null) }

    fun work(task: suspend () -> Unit) {
        if (busy) return
        busy = true
        busyChange(true)
        status = ""
        scope.launch {
            try {
                task()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: IllegalArgumentException) {
                status = failure.message ?: "Confira o arquivo selecionado."
            } catch (_: Exception) {
                status = "Não foi possível concluir. Confira o arquivo ou informe o valor manualmente."
            } finally {
                busy = false
                busyChange(false)
            }
        }
    }

    suspend fun scan(file: DocumentAttachment, start: Int = 0) {
        if (start == 0) {
            amounts = emptyList()
            limit = 12
        }
        val result = recognizer.scan(file, mode, start)
        scanned = file
        amounts = sortedAmounts(amounts + result.amounts)
        nextPage = result.nextPage
        review = amounts.isNotEmpty() || nextPage != null
        if (!review) status = "Nenhum valor legível. Confira o documento e preencha o valor manualmente."
    }

    suspend fun add(file: DocumentAttachment) {
        if (attachments.none { it.id == file.id }) change(attachments + file)
        status = if (isPurchase) "Documento anexado. Salve a compra para manter o vínculo."
            else "Comprovante anexado. Salve o pagamento para manter o vínculo."
        if (isPurchase) scan(file)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work { add(withContext(Dispatchers.IO) { files.fromPicker(uri, isPurchase) }) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val name = photoName
        photoName = null
        if (name != null) {
            if (captured) work { add(withContext(Dispatchers.IO) { files.fromPhoto(name) }) }
            else files.cameraFile(name).delete()
        }
    }

    HorizontalDivider()
    Heading(if (isPurchase) "Ler nota ou orçamento" else "Comprovantes")
    if (attachments.isEmpty()) {
        Detail(if (isPurchase) "Confira os valores sugeridos antes de salvar a compra." else "Fotografe ou selecione um PDF ou imagem do pagamento.")
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedButton(enabled = !busy, onClick = {
            try {
                val photo = files.startPhoto()
                photoName = photo.file.name
                camera.launch(photo.uri)
            } catch (_: Exception) {
                photoName?.let { files.cameraFile(it).delete() }
                photoName = null
                status = "Câmera indisponível. Selecione uma foto ou arquivo."
            }
        }) { Text("Tirar foto") }
        OutlinedButton(enabled = !busy, onClick = {
            try {
                picker.launch(if (isPurchase) documentPickerTypes + arrayOf("application/xml", "text/xml") else documentPickerTypes)
            } catch (_: ActivityNotFoundException) {
                status = "O seletor de arquivos está indisponível neste aparelho."
            }
        }) { Text("Selecionar arquivo") }
    }
    attachments.forEach { file ->
        Detail(file.name)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = !busy, onClick = { work { scan(file) } }) {
                Text(if (isPurchase) "Ler valores" else "Ler valor pago")
            }
            if (file.type != DocumentType.XML) TextButton(enabled = !busy, onClick = {
                try {
                    val uri = files.uri(file)
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.type.mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.clipData = ClipData.newRawUri(file.name, uri)
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    status = "Instale um visualizador compatível para abrir este arquivo."
                } catch (_: Exception) {
                    status = "Não foi possível abrir o anexo."
                }
            }) { Text("Abrir") }
            TextButton(enabled = !busy, onClick = {
                change(attachments.filterNot { it.id == file.id })
                status = "Anexo removido do cadastro em edição."
            }) { Text("Remover") }
        }
    }
    if (busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Detail("Processando arquivo…")
    }
    if (status.isNotBlank()) Detail(status)

    if (review && selected == null) {
        AlertDialog(
            onDismissRequest = { if (!busy) review = false },
            title = { Text(if (isPurchase) "Confira os valores lidos" else "Valor do comprovante") },
            text = {
                Column(Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState())) {
                    scanned?.let { Detail(it.name) }
                    if (amounts.isEmpty()) Detail("Nenhum valor identificado nas páginas lidas.")
                    amounts.take(limit).forEach { amount ->
                        TextButton(enabled = !busy, onClick = { selected = amount }, modifier = Modifier.fillMaxWidth()) {
                            LedgerLine(amount.label, amount.cents.brl())
                        }
                    }
                    if (amounts.size > limit) TextButton(enabled = !busy, onClick = { limit += 12 }) { Text("Mostrar mais valores") }
                    val next = nextPage
                    val file = scanned
                    if (next != null && file != null) TextButton(enabled = !busy, onClick = { work { scan(file, next) } }) {
                        Text("Ler a partir da página ${next + 1}")
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (status.isNotBlank()) Detail(status)
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = { review = false }) { Text("Concluir conferência") } },
        )
    }
    selected?.let { value ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(value.cents.brl()) },
            text = { Detail(if (isPurchase) "Escolha em qual campo aplicar o valor." else "Usar como valor pago?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isPurchase) TextButton(onClick = {
                        applyAmount(value, MoneyField.DISCOUNT)
                        selected = null
                        status = "Confira o desconto e salve a compra."
                    }) { Text("Desconto") }
                    Button(onClick = {
                        applyAmount(value, MoneyField.TOTAL)
                        selected = null
                        status = "Confira o valor preenchido antes de salvar."
                    }) { Text(if (isPurchase) "Total da compra" else "Usar valor") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Voltar") } },
        )
    }
}
