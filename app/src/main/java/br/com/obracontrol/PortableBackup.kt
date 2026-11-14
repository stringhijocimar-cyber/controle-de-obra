package br.com.obracontrol

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val MAX_ARCHIVE_BYTES = 250L * 1024 * 1024
private const val PROJECT_PATH = "projeto.json"
private const val DOCUMENT_PATH = "documentos/"

internal class PortableBackup(private val files: DocumentFiles) {
    fun write(output: OutputStream, project: Project) {
        val documents = project.documentAttachments().distinctBy { it.fileName }
        require(documents.sumOf { it.size } <= MAX_ARCHIVE_BYTES) { "Os anexos excedem 250 MB para um único backup." }
        // Verify before writing, so a missing document cannot produce a backup that looks complete.
        val sources = documents.associateWith(files::requireFile)
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(PROJECT_PATH))
            zip.write(ProjectCodec.encode(project).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            sources.forEach { (document, file) ->
                zip.putNextEntry(ZipEntry(DOCUMENT_PATH + document.fileName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun read(source: InputStream): Project = BufferedInputStream(source).use { input ->
        input.mark(4)
        val zipped = input.read() == 0x50 && input.read() == 0x4b
        input.reset()
        if (zipped) readZip(input) else ProjectCodec.decode(readBackup(input)).also { project ->
            project.documentAttachments().forEach(files::requireFile)
        }
    }

    private fun readZip(input: InputStream): Project = ZipInputStream(input).use { zip ->
        require(zip.nextEntry?.name == PROJECT_PATH) { "Selecione um backup ZIP gerado pelo ObraControl." }
        val project = ProjectCodec.decode(readBackup(zip))
        zip.closeEntry()
        val expected = project.documentAttachments().associateBy { DOCUMENT_PATH + it.fileName }.toMutableMap()
        require(expected.values.sumOf { it.size } <= MAX_ARCHIVE_BYTES) { "Os anexos do backup excedem 250 MB." }
        while (true) {
            val entry = zip.nextEntry ?: break
            val document = expected.remove(entry.name)
            require(!entry.isDirectory && document != null) { "O backup contém um anexo inesperado ou repetido." }
            files.save(zip, document.name, expected = document)
            zip.closeEntry()
        }
        require(expected.isEmpty()) { "O backup está incompleto. Selecione o arquivo com todos os documentos." }
        project
    }
}
