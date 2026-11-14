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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
    private var project by mutableStateOf(Project())
    private var loadFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Navy.toArgb()))
        store = ProjectStore(this)
        runCatching(store::load).onSuccess { project = it }.onFailure { loadFailed = true }
        setContent { ObraTheme { Screen() } }
    }

    private fun save(updated: Project) {
        check(!loadFailed) { "Restaure um backup válido antes de editar a obra." }
        updated.validate()
        store.save(updated)
        project = updated
    }

    @Composable
    private fun Screen() {
        var tab by rememberSaveable { mutableStateOf(Section.DASHBOARD.name) }
        var route by rememberSaveable { mutableStateOf("") }
        var pendingRestore by remember { mutableStateOf<Project?>(null) }
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
        val makeBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                val snapshot = project
                runFileOperation {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                            it.write(ProjectCodec.encode(snapshot))
                        } ?: throw IOException("Sem acesso ao backup")
                    }
                    "Backup salvo."
                }
            }
        }
        val readJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runFileOperation {
                    pendingRestore = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { ProjectCodec.decode(readBackup(it)) }
                            ?: throw IOException("Sem acesso ao backup")
                    }
                    null
                }
            }
        }
        val openRestore = { readJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

        fun goBack() { route = backRoute(route) }
        BackHandler(route.isNotEmpty() && !activeFileOperation) { goBack() }
        pendingRestore?.let { backup ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text("Carregar ${backup.name}?") },
                text = { Text("O backup tem ${backup.stages.size} etapas, ${backup.purchases.size} compras e ${backup.payments.size} pagamentos. O cadastro neste aparelho será substituído.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingRestore = null
                        runFileOperation {
                            store.save(backup)
                            project = backup
                            loadFailed = false
                            exportedPdf = null
                            route = ""
                            tab = Section.DASHBOARD.name
                            "Obra restaurada."
                        }
                    }) { Text("Restaurar") }
                },
                dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") } },
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
                    Section.DASHBOARD -> Dashboard(project, balance, { route = "obra" }, { route = "pendencias" })
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
                        { makeBackup.launch("ObraControl_Backup.json") },
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
