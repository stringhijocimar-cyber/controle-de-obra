package br.com.obracontrol

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.IOException

class MainActivity : ComponentActivity() {
    private lateinit var store: ProjectStore
    private lateinit var documents: DocumentFiles
    private lateinit var backups: PortableBackup
    private var project by mutableStateOf(Project())
    private var loadFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Navy.toArgb()))
        store = ProjectStore(this)
        documents = DocumentFiles(applicationContext)
        backups = PortableBackup(documents)
        runCatching(store::load).onSuccess {
            project = it
            documents.removeOldDrafts(it)
        }.onFailure { loadFailed = true }
        setContent { ObraTheme { Screen() } }
    }

    private fun save(updated: Project) {
        check(!loadFailed) { "Restaure um backup válido antes de editar a obra." }
        updated.validate()
        updated.documentAttachments().forEach(documents::requireFile)
        store.save(updated)
        project = updated
    }

    @Composable
    private fun Screen() {
        var tab by rememberSaveable { mutableStateOf(Section.DASHBOARD.name) }
        var route by rememberSaveable { mutableStateOf("") }
        var pendingRestore by remember { mutableStateOf<Project?>(null) }
        var confirmReplace by remember { mutableStateOf(false) }
        var activeFileOperation by remember { mutableStateOf(false) }
        var exportedPdf by rememberSaveable { mutableStateOf<String?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val balance = remember(project) { Balance(project) }
        val section = Section.valueOf(tab)

        fun runFileOperation(operation: suspend () -> String?) {
            if (activeFileOperation) return
            activeFileOperation = true
            scope.launch {
                val outcome = try {
                    operation()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: IllegalArgumentException) {
                    failure.message ?: "Confira o arquivo e tente novamente."
                } catch (_: Exception) {
                    "Não foi possível processar o arquivo. Confira o formato e o acesso."
                } finally {
                    activeFileOperation = false
                }
                outcome?.let { snackbar.showSnackbar(it) }
            }
        }

        val makePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri != null) {
                val snapshot = project
                runFileOperation {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri, "wt")?.use { FullReport.write(it, snapshot) }
                            ?: throw IOException("Sem acesso ao PDF")
                    }
                    exportedPdf = uri.toString()
                    "Relatório PDF salvo."
                }
            }
        }
        val makeBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) {
                val snapshot = project
                runFileOperation {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri, "wt")?.use { backups.write(it, snapshot) }
                            ?: throw IOException("Sem acesso ao backup")
                    }
                    "Backup com anexos salvo."
                }
            }
        }
        val readProject = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runFileOperation {
                    pendingRestore = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use(backups::read)
                            ?: throw IOException("Sem acesso ao backup")
                    }
                    confirmReplace = false
                    null
                }
            }
        }
        val openRestore = { readProject.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/json", "text/plain", "application/octet-stream")) }

        fun goBack() { route = backRoute(route) }
        BackHandler(route.isNotEmpty() && !activeFileOperation) { goBack() }
        pendingRestore?.let { backup ->
            val existing = !loadFailed && project.hasWork()
            val same = existing && project.isSameWork(backup)
            val details = "${backup.stages.size} etapas, ${backup.purchases.size} compras, ${backup.payments.size} pagamentos e ${backup.documentAttachments().size} anexos."
            fun finishImport(merge: Boolean) {
                runFileOperation {
                    val result = if (merge) project.mergeFrom(backup) else ProjectImport(backup, 0)
                    result.project.validate()
                    result.project.documentAttachments().forEach(documents::requireFile)
                    store.save(result.project)
                    project = result.project
                    pendingRestore = null
                    confirmReplace = false
                    loadFailed = false
                    exportedPdf = null
                    route = ""
                    tab = Section.DASHBOARD.name
                    if (!merge) "Obra importada."
                    else if (result.added == 0) "Cadastro conferido. Nenhum lançamento duplicado."
                    else "Importação concluída. ${result.added} registros ou anexos adicionados."
                }
            }
            AlertDialog(
                onDismissRequest = { if (!activeFileOperation) { pendingRestore = null; confirmReplace = false } },
                title = { Text(if (confirmReplace) "Substituir a obra atual?" else "Importar ${backup.name}?") },
                text = {
                    Text(when {
                        confirmReplace -> "Os registros e anexos do cadastro atual serão substituídos pelos deste arquivo."
                        same -> "$details Você pode acrescentar os registros e anexos que faltam e manter suas edições."
                        existing -> "$details Este arquivo pertence a outra obra. A substituição troca o cadastro atual."
                        else -> details
                    })
                },
                confirmButton = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(enabled = !activeFileOperation, onClick = {
                            when {
                                confirmReplace || !existing -> finishImport(merge = false)
                                same -> finishImport(merge = true)
                                else -> confirmReplace = true
                            }
                        }) { Text(if (confirmReplace) "Confirmar substituição" else if (same) "Mesclar lançamentos" else if (existing) "Substituir obra" else "Importar") }
                        if (same && !confirmReplace) TextButton(enabled = !activeFileOperation, onClick = { confirmReplace = true }) {
                            Text("Substituir obra")
                        }
                    }
                },
                dismissButton = { TextButton(enabled = !activeFileOperation, onClick = { pendingRestore = null; confirmReplace = false }) { Text("Cancelar") } },
            )
        }

        val editor = route.isNotEmpty()
        Scaffold(
            containerColor = Mist,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Box(Modifier.background(Navy).statusBarsPadding()) {
                    ObraHeader(
                        title = if (editor) editorTitle(route) else "ObraControl",
                        subtitle = if (editor) project.name else section.title,
                        onBack = if (editor) ::goBack else null,
                        onSettings = if (!editor && !loadFailed) ({ route = "obra" }) else null,
                    )
                }
            },
            bottomBar = {
                if (!editor && !loadFailed) ObraNavigation(section) { tab = it.name }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { inset ->
            Column(Modifier.fillMaxSize().padding(inset).imePadding()) {
                if (loadFailed) {
                    RecoveryScreen(openRestore)
                } else if (editor) {
                    EditorScreen(route, project, balance, ::save, { route = it })
                } else when (section) {
                    Section.DASHBOARD -> Dashboard(project, balance, { route = "obra" }, { route = "pendencias" }, openRestore)
                    Section.STAGES -> StageList(project, balance, { route = "etapa/nova" }, { route = "etapa/$it" })
                    Section.PURCHASES -> PurchaseList(project, balance, { route = "compra/nova" }, { route = "compra/$it" })
                    Section.PAYMENTS -> PaymentsScreen(project, balance, { route = "pagamento/novo" }, { route = "pagamento/$it" })
                    Section.REPORT -> ExportScreen(
                        activeFileOperation,
                        { makePdf.launch("ObraControl_Relatorio.pdf") },
                        exportedPdf?.let { last ->
                            {
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(last), "application/pdf")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                } catch (_: ActivityNotFoundException) {
                                    scope.launch { snackbar.showSnackbar("Abra o arquivo salvo com um leitor de PDF.") }
                                } catch (_: SecurityException) {
                                    scope.launch { snackbar.showSnackbar("Abra o PDF pela pasta em que foi salvo.") }
                                }
                                Unit
                            }
                        },
                        { makeBackup.launch("ObraControl_Backup.zip") },
                        openRestore,
                    )
                }
            }
        }
    }
}

private fun backRoute(route: String): String {
    val parts = route.split('/')
    return when (parts.first()) {
        "linha" -> "compra/${parts[1]}"
        "pendencia" -> "pendencias"
        else -> ""
    }
}

private fun editorTitle(route: String): String = when (route.substringBefore('/')) {
    "obra" -> "Dados da obra"
    "etapa" -> "Etapa da obra"
    "compra" -> "Ficha da compra"
    "linha" -> "Item da compra"
    "pagamento" -> "Pagamento"
    "pendencia" -> "Editar pendência"
    "pendencias" -> "Pendências"
    else -> "ObraControl"
}
