package to.eyed.inferno.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** A real, app-private file attachment. Text extraction is optional and never replaces the original file. */
data class FileAttachment(
    val id: String,
    val path: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val extractedText: String? = null,
)

class FileAttachmentStore(private val context: Context) {
    private val root = File(context.filesDir, "attachments")

    suspend fun importFile(uri: Uri): FileAttachment = withContext(Dispatchers.IO) {
        root.mkdirs()
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
        val mime = resolver.getType(uri) ?: mimeFromName(name)
        val id = UUID.randomUUID().toString()
        val target = File(root, "$id.bin")
        val input = resolver.openInputStream(uri) ?: error("Could not open $name")
        try {
            input.use { source ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_FILE_BYTES) { "File exceeds the 32 MB attachment limit" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val size = target.length()
            FileAttachment(id, target.path, name, mime, size, extractText(target, name, mime))
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    fun resolve(id: String, name: String, mime: String, size: Long): FileAttachment? {
        if (!id.matches(Regex("[A-Za-z0-9-]{16,64}"))) return null
        val file = File(root, "$id.bin")
        if (!file.isFile) return null
        return FileAttachment(id, file.path, name, mime, size, null)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        if (id.matches(Regex("[A-Za-z0-9-]{16,64}"))) File(root, "$id.bin").delete()
        Unit
    }

    /** Stable, hidden metadata appended to the stored message row; the original bytes stay in files/attachments. */
    fun encodeMarker(files: List<FileAttachment>): String {
        if (files.isEmpty()) return ""
        val rows = files.joinToString(";") { f ->
            "${f.id}~${encode(f.displayName)}~${encode(f.mimeType)}~${f.sizeBytes}"
        }
        return "<!--INFERNO_FILES:$rows-->"
    }

    fun decodeMarker(content: String): Pair<String, List<FileAttachment>> {
        val match = Regex("""\s*<!--INFERNO_FILES:([A-Za-z0-9~_;=-]+)-->$""").find(content)
            ?: return content to emptyList()
        val files = match.groupValues[1].split(';').mapNotNull { row ->
            val p = row.split('~')
            if (p.size != 4) null else resolve(
                p[0], decode(p[1]) ?: return@mapNotNull null, decode(p[2]) ?: "application/octet-stream",
                p[3].toLongOrNull() ?: 0L,
            )
        }
        return content.removeRange(match.range).trimEnd() to files
    }

    fun idsFromContent(content: String): List<String> {
        val marker = Regex("""<!--INFERNO_FILES:([A-Za-z0-9~_;=-]+)-->$""").find(content) ?: return emptyList()
        return marker.groupValues[1].split(';').mapNotNull { it.substringBefore('~').takeIf { id -> id.matches(Regex("[A-Za-z0-9-]{16,64}")) } }
    }

    fun pendingKey(file: FileAttachment): String =
        "${file.id}~${encode(file.displayName)}~${encode(file.mimeType)}~${file.sizeBytes}"

    fun restorePendingKey(key: String): FileAttachment? {
        val p = key.split('~')
        if (p.size != 4) return null
        return resolve(p[0], decode(p[1]) ?: return null, decode(p[2]) ?: "application/octet-stream", p[3].toLongOrNull() ?: 0L)
    }

    private fun encode(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun decode(value: String): String? = runCatching { String(Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8) }.getOrNull()

    suspend fun extractText(file: FileAttachment): String? = withContext(Dispatchers.IO) { extractText(File(file.path), file.displayName, file.mimeType) }

    private fun extractText(file: File, name: String, mime: String): String? {
        if (file.length() > MAX_TEXT_BYTES) return null
        val ext = name.substringAfterLast('.', "").lowercase()
        val textLike = mime.startsWith("text/") || ext in TEXT_EXTENSIONS || mime in setOf("application/json", "application/xml", "application/javascript")
        if (!textLike) return null
        return runCatching {
            file.readText(Charsets.UTF_8).take(MAX_EXTRACTED_CHARS).takeIf { text ->
                text.none { it == '\u0000' } && text.isNotBlank()
            }
        }.getOrNull()
    }

    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "xml", "svg" -> "application/xml"
        "html", "htm" -> "text/html"
        "md", "txt", "csv", "log" -> "text/plain"
        "js", "mjs" -> "application/javascript"
        "py" -> "text/x-python"
        "kt", "java", "c", "cpp", "h", "hpp", "lua", "luau", "css", "sql", "sh" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    companion object {
        const val MAX_FILE_BYTES = 32L * 1024 * 1024
        private const val MAX_TEXT_BYTES = 2L * 1024 * 1024
        private const val MAX_EXTRACTED_CHARS = 200_000
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "css", "js", "mjs", "ts",
            "py", "kt", "kts", "java", "c", "cc", "cpp", "h", "hpp", "lua", "luau", "yaml", "yml", "toml",
            "log", "sh", "sql", "rs", "go", "rb", "php", "ini", "conf",
        )
    }
}
