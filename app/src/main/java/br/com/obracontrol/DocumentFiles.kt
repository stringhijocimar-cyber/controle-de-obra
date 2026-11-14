package br.com.obracontrol

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal const val MAX_DOCUMENT_BYTES = 25L * 1024 * 1024
private const val DRAFT_RETENTION_MS = 24L * 60 * 60 * 1000
private val attachmentId = Regex("[a-f0-9]{64}")

internal enum class DocumentType(val mime: String, val extension: String) {
    PDF("application/pdf", "pdf"), JPEG("image/jpeg", "jpg"), PNG("image/png", "png"),
    WEBP("image/webp", "webp"), HEIC("image/heic", "heic"), XML("application/xml", "xml");

    companion object {
        fun identify(prefix: ByteArray, name: String): DocumentType {
            val signature = prefix.toString(Charsets.ISO_8859_1)
            return when {
                "%PDF-" in signature.take(1024) -> PDF
                prefix.size >= 3 && prefix[0] == 0xff.toByte() && prefix[1] == 0xd8.toByte() && prefix[2] == 0xff.toByte() -> JPEG
                signature.startsWith("\u0089PNG\r\n\u001a\n") -> PNG
                signature.startsWith("RIFF") && signature.drop(8).startsWith("WEBP") -> WEBP
                signature.startsWith("ftyp", startIndex = 4) && signature.drop(8).take(32).chunked(4).any {
                    it in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
                } -> HEIC
                name.endsWith(".xml", ignoreCase = true) && looksLikeXml(prefix) -> XML
                else -> throw IllegalArgumentException("Formato inválido. Use PDF, XML de NF-e ou uma foto JPG, PNG, WebP ou HEIC.")
            }
        }

        private fun looksLikeXml(prefix: ByteArray): Boolean {
            val charset = when {
                prefix.size >= 2 && prefix[0] == 0xff.toByte() && prefix[1] == 0xfe.toByte() -> Charsets.UTF_16LE
                prefix.size >= 2 && prefix[0] == 0xfe.toByte() && prefix[1] == 0xff.toByte() -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
            return prefix.toString(charset).removePrefix("\uFEFF").trimStart().startsWith("<")
        }
    }
}

internal data class DocumentAttachment(val id: String, val name: String, val type: DocumentType, val size: Long) {
    val fileName get() = "$id.${type.extension}"

    fun validate() {
        require(attachmentId.matches(id) && name.isNotBlank() && name.length <= 160 &&
            name.none { it.isISOControl() || it == '/' || it == '\\' } && size in 1..MAX_DOCUMENT_BYTES) {
            "O cadastro contém um anexo inválido."
        }
    }
}

internal fun Project.documentAttachments(): List<DocumentAttachment> =
    purchases.flatMap { it.documents } + payments.flatMap { it.proofs }

internal data class CameraFile(val file: File, val uri: Uri)

class ObraFileProvider : FileProvider()

internal class DocumentFiles(private val context: Context) {
    private val directory = File(context.filesDir, "documents")
    private val captures = File(context.cacheDir, "capturas")
    private val authority get() = "${context.packageName}.documentos"

    fun startPhoto(): CameraFile {
        captures.mkdirs()
        val file = File.createTempFile("foto-", ".jpg", captures)
        return CameraFile(file, FileProvider.getUriForFile(context, authority, file))
    }

    fun cameraFile(name: String): File = File(captures, name).also {
        require(name.matches(Regex("foto-[0-9-]+\\.jpg"))) { "Captura inválida." }
    }

    fun fromPhoto(name: String): DocumentAttachment {
        val file = cameraFile(name)
        try {
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss"))
            return file.inputStream().use { save(it, "Foto-$stamp.jpg", allowXml = false) }
        } finally {
            file.delete()
        }
    }

    fun fromPicker(uri: Uri, allowXml: Boolean): DocumentAttachment {
        val resolver = context.contentResolver
        val name = resolver.displayName(uri) ?: if (resolver.getType(uri) in setOf("application/xml", "text/xml")) "Documento.xml" else "Documento"
        return resolver.openInputStream(uri)?.use { save(it, name, allowXml) }
            ?: throw IllegalArgumentException("Não foi possível abrir o arquivo selecionado.")
    }

    fun uri(attachment: DocumentAttachment): Uri = FileProvider.getUriForFile(context, authority, requireFile(attachment), attachment.name)

    fun requireFile(attachment: DocumentAttachment): File {
        attachment.validate()
        return File(directory, attachment.fileName).also {
            require(it.isFile && it.length() == attachment.size) { "Não foi possível localizar ${attachment.name}." }
        }
    }

    fun save(input: InputStream, name: String, allowXml: Boolean = true, expected: DocumentAttachment? = null): DocumentAttachment {
        directory.mkdirs()
        val draft = File.createTempFile("anexo-", ".tmp", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val maxSize = expected?.size ?: MAX_DOCUMENT_BYTES
            var size = 0L
            FileOutputStream(draft).use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    size += read
                    require(size <= maxSize) {
                        if (expected == null) "O anexo deve ter até 25 MB." else "O tamanho de um anexo não confere com o backup."
                    }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
            require(size > 0) { "O arquivo está vazio." }
            val safeName = name.substringAfterLast('/').substringAfterLast('\\').filterNot(Char::isISOControl).trim().take(150)
            val type = FileInputStream(draft).use {
                val header = ByteArray(minOf(size, 1024).toInt())
                java.io.DataInputStream(it).readFully(header)
                DocumentType.identify(header, safeName)
            }
            require(allowXml || type != DocumentType.XML) { "Para comprovantes, selecione PDF ou uma imagem." }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val display = safeName.takeIf { it.isNotBlank() && '.' in it } ?: "${safeName.ifBlank { "Anexo" }}.${type.extension}"
            val document = DocumentAttachment(hash, display, type, size)
            if (expected != null) {
                require(document.id == expected.id && document.size == expected.size && document.type == expected.type) {
                    "Um anexo está corrompido ou não corresponde ao cadastro."
                }
            }
            val target = File(directory, document.fileName)
            if (!target.exists() || target.length() != size || expected != null) {
                check(draft.renameTo(target)) { "Não foi possível salvar o anexo." }
            } else {
                target.setLastModified(System.currentTimeMillis())
            }
            return expected ?: document
        } finally {
            draft.delete()
        }
    }

    fun removeOldDrafts(project: Project) {
        val retained = project.documentAttachments().mapTo(hashSetOf()) { it.fileName }
        val cutoff = System.currentTimeMillis() - DRAFT_RETENTION_MS
        directory.listFiles()?.filter { it.name !in retained && it.lastModified() < cutoff }?.forEach(File::delete)
        captures.listFiles()?.filter { it.lastModified() < cutoff }?.forEach(File::delete)
    }
}

private fun ContentResolver.displayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { row ->
        if (row.moveToFirst() && row.columnCount > 0) row.getString(0) else null
    }
}.getOrNull()
