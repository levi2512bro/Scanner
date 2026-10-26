package de.levi.scanner

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val scansDir by lazy { File(filesDir, "scans").apply { mkdirs() } }
    private val docs = mutableStateListOf<File>()
    private var pendingExport: File? = null

    private val scanner: GmsDocumentScanner by lazy {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)          // auch Fotos aus der Galerie
            .setPageLimit(50)                       // mehrseitige Dokumente
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL) // Filter, Radierer, Zuschneiden
            .build()
        GmsDocumentScanning.getClient(options)
    }

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val pdf = result?.pdf
            if (pdf != null) savePdf(pdf.uri, pdf.pageCount)
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { target: Uri? ->
        val source = pendingExport
        pendingExport = null
        if (target != null && source != null) {
            try {
                contentResolver.openOutputStream(target)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                }
                toast("Gespeichert")
            } catch (e: Exception) {
                toast("Speichern fehlgeschlagen")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reload()
        setContent {
            AppTheme {
                ScannerScreen(
                    docs = docs,
                    onScan = ::startScan,
                    onOpen = ::openPdf,
                    onShare = ::sharePdf,
                    onExport = { file ->
                        pendingExport = file
                        exportLauncher.launch(file.name)
                    },
                    onRename = ::rename,
                    onDelete = ::delete
                )
            }
        }
    }

    // ---------- Aktionen ----------

    private fun startScan() {
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                toast("Scanner konnte nicht starten: ${e.localizedMessage ?: "unbekannter Fehler"}")
            }
    }

    private fun savePdf(uri: Uri, pages: Int) {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.GERMANY).format(Date())
        val file = File(scansDir, "Scan_$stamp.pdf")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            reload()
            toast(if (pages == 1) "1 Seite gespeichert" else "$pages Seiten gespeichert")
        } catch (e: Exception) {
            toast("Speichern fehlgeschlagen")
        }
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.files", file)

    private fun openPdf(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(file), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Keine App zum Öffnen von PDFs gefunden")
        }
    }

    private fun sharePdf(file: File) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uriFor(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Scan teilen"))
    }

    private fun rename(file: File, newName: String) {
        val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").removeSuffix(".pdf")
        if (clean.isEmpty()) return
        val target = File(scansDir, "$clean.pdf")
        if (target.exists()) {
            toast("Name existiert schon")
            return
        }
        if (file.renameTo(target)) reload() else toast("Umbenennen fehlgeschlagen")
    }

    private fun delete(file: File) {
        if (file.delete()) reload()
    }

    private fun reload() {
        docs.clear()
        docs.addAll(
            scansDir.listFiles { f -> f.extension.equals("pdf", true) }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
        )
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// ---------- Oberfläche ----------

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF7FD3BC), secondary = Color(0xFFFFD166))
        else -> lightColorScheme(primary = Color(0xFF1F6F5C), secondary = Color(0xFFE0A800))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    docs: List<File>,
    onScan: () -> Unit,
    onOpen: (File) -> Unit,
    onShare: (File) -> Unit,
    onExport: (File) -> Unit,
    onRename: (File, String) -> Unit,
    onDelete: (File) -> Unit
) {
    var renaming by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scanner", fontWeight = FontWeight.SemiBold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Scannen") }
            )
        }
    ) { padding ->
        if (docs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Noch keine Scans", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tippe auf „Scannen“, halte die Kamera über ein Dokument – die Ränder werden automatisch erkannt und zugeschnitten.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(docs, key = { it.absolutePath }) { file ->
                    DocRow(
                        file = file,
                        onOpen = { onOpen(file) },
                        onShare = { onShare(file) },
                        onExport = { onExport(file) },
                        onRename = { renaming = file },
                        onDelete = { deleting = file }
                    )
                }
            }
        }
    }

    renaming?.let { file ->
        var name by remember(file) { mutableStateOf(file.nameWithoutExtension) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Umbenennen") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { onRename(file, name); renaming = null }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Abbrechen") } }
        )
    }

    deleting?.let { file ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Löschen?") },
            text = { Text("„${file.nameWithoutExtension}“ wird endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = { onDelete(file); deleting = null }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
fun DocRow(
    file: File,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val date = remember(file.lastModified()) {
        SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY).format(Date(file.lastModified()))
    }
    val size = remember(file.length()) {
        val kb = file.length() / 1024.0
        if (kb < 1024) "%.0f KB".format(kb) else "%.1f MB".format(kb / 1024)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.nameWithoutExtension,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$date · $size",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Teilen") }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Mehr")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Speichern unter …") },
                        leadingIcon = { Icon(Icons.Filled.Check, null) },
                        onClick = { menu = false; onExport() }
                    )
                    DropdownMenuItem(
                        text = { Text("Umbenennen") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}
