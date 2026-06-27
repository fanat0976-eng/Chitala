package com.chitala.reader

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedReader
import java.io.BufferedInputStream
import java.io.InputStreamReader

object FileReader {

    data class FileContent(
        val title: String,
        val content: String,
        val type: FileType,
        val pages: List<String> = emptyList()
    )

    enum class FileType {
        TEXT, PDF, DOCX, XLSX, CSV, MARKDOWN, UNKNOWN
    }

    fun read(context: Context, uri: Uri, fileName: String): FileContent {
        val type = detectType(fileName)
        return try {
            when (type) {
                FileType.TEXT, FileType.CSV, FileType.MARKDOWN -> readText(context, uri, fileName, type)
                FileType.DOCX -> readDocx(context, uri, fileName)
                FileType.XLSX -> readXlsx(context, uri, fileName)
                FileType.PDF -> FileContent(fileName, "", FileType.PDF)
                FileType.UNKNOWN -> readText(context, uri, fileName, type)
            }
        } catch (e: OutOfMemoryError) {
            FileContent(fileName, "Файл слишком большой для чтения", type)
        } catch (e: Exception) {
            FileContent(fileName, "Ошибка чтения: ${e.message}", type)
        }
    }

    private fun detectType(fileName: String): FileType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt", "log", "ini", "cfg", "conf", "json", "xml", "yaml", "yml",
            "py", "js", "kt", "java", "cpp", "c", "h", "html", "css", "sh", "bat" -> FileType.TEXT
            "md", "markdown" -> FileType.MARKDOWN
            "csv", "tsv" -> FileType.CSV
            "pdf" -> FileType.PDF
            "docx" -> FileType.DOCX
            "xlsx", "xls" -> FileType.XLSX
            else -> FileType.UNKNOWN
        }
    }

    private fun readText(context: Context, uri: Uri, fileName: String, type: FileType): FileContent {
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(BufferedInputStream(stream), "UTF-8")).use { it.readText() }
        } ?: "Ошибка чтения файла"
        return FileContent(fileName, content, type)
    }

    private fun readDocx(context: Context, uri: Uri, fileName: String): FileContent {
        val content = context.contentResolver.openInputStream(uri)?.use { rawStream ->
            val stream = BufferedInputStream(rawStream)
            val doc = XWPFDocument(stream)
            val sb = StringBuilder()

            for (paragraph in doc.paragraphs) {
                val text = paragraph.text
                if (text.isNotBlank()) {
                    sb.appendLine(text)
                }
            }

            for (table in doc.tables) {
                sb.appendLine()
                for (row in table.rows) {
                    val cells = row.tableCells.map { it.text.trim() }
                    sb.appendLine(cells.joinToString(" | "))
                }
            }

            doc.close()
            stream.close()
            sb.toString()
        } ?: "Ошибка чтения DOCX"
        return FileContent(fileName, content, FileType.DOCX)
    }

    private fun readXlsx(context: Context, uri: Uri, fileName: String): FileContent {
        val content = context.contentResolver.openInputStream(uri)?.use { rawStream ->
            val stream = BufferedInputStream(rawStream)
            val workbook = XSSFWorkbook(stream)
            val sb = StringBuilder()

            for (sheet in workbook) {
                sb.appendLine("=== ${sheet.sheetName} ===")
                for (row in sheet) {
                    val cells = row.map { cell -> cell?.toString()?.trim() ?: "" }
                    sb.appendLine(cells.joinToString(" | "))
                }
                sb.appendLine()
            }
            workbook.close()
            stream.close()
            sb.toString()
        } ?: "Ошибка чтения XLSX"
        return FileContent(fileName, content, FileType.XLSX)
    }

    fun getSupportedExtensions(): List<String> {
        return listOf("txt", "md", "csv", "json", "xml", "docx", "xlsx", "pdf")
    }

    fun getMimeType(): Array<String> {
        return arrayOf(
            "text/*",
            "application/json",
            "application/xml",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }
}
