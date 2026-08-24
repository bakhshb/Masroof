package com.baraa.masroof.application.logging

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AppLogService(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val maxEntries: Int = 500,
) {
    private val lock = Any()
    private val logFile: File = File(context.filesDir, LOG_FILE_NAME)
    private var nextId = 1L
    private val entries = ArrayDeque<AppLogEntry>()

    init {
        loadFromDisk()
    }

    fun log(level: AppLogLevel, category: String, message: String) {
        val entry = AppLogEntry(
            id = synchronized(lock) { nextId++ },
            timestampEpochMs = System.currentTimeMillis(),
            category = category.trim().ifEmpty { "general" },
            level = level,
            message = AppLogRedactor.redact(message.trim()),
        )
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > maxEntries) {
                entries.removeFirst()
            }
            appendLineToDisk(entry)
        }
    }

    fun info(category: String, message: String) = log(AppLogLevel.INFO, category, message)

    fun warn(category: String, message: String) = log(AppLogLevel.WARN, category, message)

    fun error(category: String, message: String) = log(AppLogLevel.ERROR, category, message)

    fun readAll(): List<AppLogEntry> =
        synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            if (logFile.exists()) {
                logFile.delete()
            }
        }
    }

    fun exportTo(uri: Uri): Result<Unit> =
        runCatching {
            val text = buildExportText(readAll())
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open export destination")
        }

    fun exportFileName(timestampEpochMs: Long = System.currentTimeMillis()): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
        val stamp = formatter.format(Instant.ofEpochMilli(timestampEpochMs).atZone(zoneId))
        return "masroof-log-$stamp.txt"
    }

    private fun appendLineToDisk(entry: AppLogEntry) {
        logFile.parentFile?.mkdirs()
        logFile.appendText(
            "${entry.id}\t${entry.timestampEpochMs}\t${entry.level.name}\t${entry.category}\t${entry.message}\n",
            Charsets.UTF_8,
        )
    }

    private fun loadFromDisk() {
        if (!logFile.exists()) return
        val loaded = logFile.readLines()
            .mapNotNull(::parseLine)
            .takeLast(maxEntries)
        synchronized(lock) {
            entries.clear()
            entries.addAll(loaded)
            nextId = (entries.maxOfOrNull { it.id } ?: 0L) + 1L
        }
    }

    private fun parseLine(line: String): AppLogEntry? {
        val parts = line.split('\t', limit = 5)
        if (parts.size < 5) return null
        val level = runCatching { AppLogLevel.valueOf(parts[2]) }.getOrNull() ?: return null
        val id = parts[0].toLongOrNull() ?: return null
        val timestamp = parts[1].toLongOrNull() ?: return null
        return AppLogEntry(
            id = id,
            timestampEpochMs = timestamp,
            category = parts[3],
            level = level,
            message = parts[4],
        )
    }

    private fun buildExportText(entries: List<AppLogEntry>): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
        val header = buildString {
            appendLine("Masroof diagnostic log")
            appendLine("Exported: ${formatter.format(Instant.now().atZone(zoneId))}")
            appendLine("Entries: ${entries.size}")
            appendLine()
        }
        return header + entries.joinToString(separator = "\n") { formatEntry(it, formatter) }
    }

    private fun formatEntry(
        entry: AppLogEntry,
        formatter: DateTimeFormatter,
    ): String {
        val timestamp = formatter.format(Instant.ofEpochMilli(entry.timestampEpochMs).atZone(zoneId))
        return "$timestamp ${entry.level.name} [${entry.category}] ${entry.message}"
    }

    companion object {
        const val LOG_FILE_NAME: String = "masroof-app.log"
    }
}
