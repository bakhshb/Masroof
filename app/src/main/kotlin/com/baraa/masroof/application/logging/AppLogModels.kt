package com.baraa.masroof.application.logging

enum class AppLogLevel {
    INFO,
    WARN,
    ERROR,
}

data class AppLogEntry(
    val id: Long,
    val timestampEpochMs: Long,
    val category: String,
    val level: AppLogLevel,
    val message: String,
)
