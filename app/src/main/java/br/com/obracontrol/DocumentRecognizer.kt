package br.com.obracontrol

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Xml
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

internal const val PAGES_PER_SCAN = 12
private const val MAX_IMAGE_SIDE = 3200
internal data class ScanResult(val amounts: List<MoneyCandidate>, val nextPage: Int? = null)

internal class DocumentRecognizer(private val files: DocumentFiles) {
    suspend fun scan(
        document: DocumentAttachment,
        scope: RecognitionScope,
        startPage: Int = 0,
    ): ScanResult = withContext(Dispatchers.IO) {
        val file = files.requireFile(document)
        if (document.type == DocumentType.XML) return@withContext ScanResult(readInvoiceXml(file))
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use { recognizer ->
            if (document.type == DocumentType.PDF) {
                return@withContext scanPdf(file, recognizer, scope, startPage)
            }
            val bitmap = readImage(file)
            try {
                ScanResult(sortedAmounts(recognizeAmounts(recognizer.rows(bitmap), 1, scope)))
            } finally {
                bitmap.recycle()
            }
        }
    }

    private suspend fun scanPdf(file: File, recognizer: TextRecognizer, scope: RecognitionScope, startPage: Int): ScanResult {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (failure: Exception) {
            descriptor.close()
            throw IllegalArgumentException("Não foi possível ler o PDF. Confira se o arquivo abre e se está desbloqueado.", failure)
        }
        renderer.use {
            val until = minOf(startPage + PAGES_PER_SCAN, it.pageCount)
            val amounts = mutableListOf<MoneyCandidate>()
            for (index in startPage until until) {
                coroutineContext.ensureActive()
                it.openPage(index).use { page ->
                    val digital = if (Build.VERSION.SDK_INT >= 35) {
                        recognizeAmounts(pdfRows(page), index + 1, scope)
                    } else emptyList()
                    // Text extraction is faster for PDFs with a text layer. Scan the pixels for labels
                    // and amounts when the page is an image or its text layer has no recognized total.
                    if (digital.any { candidate -> candidate.field == MoneyField.TOTAL }) {
                        amounts += digital
                    } else {
                        val scale = minOf(MAX_IMAGE_SIDE.toFloat() / maxOf(page.width, page.height), 4.2f)
                        val bitmap = Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1),
                            (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            amounts += recognizeAmounts(recognizer.rows(bitmap), index + 1, scope)
                        } finally {
                            bitmap.recycle()
                        }
                        amounts += digital
                    }
                }
            }
            return ScanResult(sortedAmounts(amounts), until.takeIf { next -> next < it.pageCount })
        }
    }
}

private data class PositionedText(val text: String, val bounds: RectF)

@RequiresApi(35)
private fun pdfRows(page: PdfRenderer.Page): List<String> = runCatching {
    val segments = page.textContents
    if (segments.isEmpty() || segments.any { '\n' in it.text || it.bounds.size != 1 }) {
        segments.flatMap { it.text.lines() }
    } else {
        alignRows(segments.map { PositionedText(it.text, it.bounds.single()) })
    }
}.getOrElse { emptyList() }

private fun TextRecognizer.rows(bitmap: Bitmap): List<String> {
    val result = Tasks.await(process(InputImage.fromBitmap(bitmap, 0)), 40, TimeUnit.SECONDS)
    val segments = result.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
        line.boundingBox?.let { PositionedText(line.text, RectF(it)) }
    }
    return if (segments.isEmpty()) result.text.lines() else alignRows(segments)
}

private fun alignRows(segments: List<PositionedText>): List<String> {
    val rows = mutableListOf<MutableList<PositionedText>>()
    for (segment in segments.sortedBy { it.bounds.centerY() }) {
        val row = rows.lastOrNull()
        val first = row?.first()?.bounds
        if (first != null && kotlin.math.abs(first.centerY() - segment.bounds.centerY()) <=
            minOf(first.height(), segment.bounds.height()) * 0.55f) {
            row.add(segment)
        } else {
            rows.add(mutableListOf(segment))
        }
    }
    return rows.map { row -> row.sortedBy { it.bounds.left }.joinToString("   ") { it.text } }
}

private fun readImage(file: File): Bitmap {
    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, size)
    require(size.outWidth > 0 && size.outHeight > 0) { "Não foi possível abrir esta imagem." }
    var sampling = 1
    while (maxOf(size.outWidth, size.outHeight) / sampling > MAX_IMAGE_SIDE) sampling *= 2
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampling })
        ?: throw IllegalArgumentException("Não foi possível abrir esta imagem.")
    val exif = runCatching { ExifInterface(file) }.getOrNull() ?: return decoded
    if (exif.rotationDegrees == 0 && !exif.isFlipped) return decoded
    val transform = Matrix().apply {
        if (exif.isFlipped) postScale(-1f, 1f)
        postRotate(exif.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true).also {
        if (it !== decoded) decoded.recycle()
    }
}

private fun readInvoiceXml(file: File): List<MoneyCandidate> = file.inputStream().use { input ->
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        setInput(input, null)
    }
    val values = mutableListOf<MoneyCandidate>()
    var totalsDepth = -1
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.DOCDECL) throw IllegalArgumentException("XML de NF-e inválido.")
        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "ICMSTot") totalsDepth = parser.depth
        if (parser.eventType == XmlPullParser.END_TAG && parser.depth == totalsDepth) totalsDepth = -1
        if (parser.eventType == XmlPullParser.START_TAG && totalsDepth > 0 && parser.depth == totalsDepth + 1) {
            val field = when (parser.name) {
                "vNF" -> MoneyField.TOTAL
                "vDesc" -> MoneyField.DISCOUNT
                "vProd" -> MoneyField.SUBTOTAL
                else -> null
            }
            if (field != null) {
                val raw = parser.nextText().trim()
                if (Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(raw)) {
                    parseMoney(raw)?.let { values += MoneyCandidate(it, field, 1) }
                }
            }
        }
        parser.nextToken()
    }
    require(values.any { it.field == MoneyField.TOTAL }) { "O XML não contém o total de uma NF-e." }
    sortedAmounts(values)
}
