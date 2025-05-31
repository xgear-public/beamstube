package com.awebo.ytext.util

import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Interface for logging messages with different severity levels.
 */
interface Logger {
    fun debug(message: String, vararg args: Any?)
    fun info(message: String, vararg args: Any?)
    fun warn(message: String, vararg args: Any?)
    fun error(message: String, vararg args: Any?, error: Throwable? = null)
    fun isDebugEnabled(): Boolean
}

/**
 * Default implementation of Logger that writes to both console and file.
 */
class DefaultLogger(private val logFile: File) : Logger {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    private val logLock = ReentrantLock()
    private val initialized = AtomicBoolean(false)
    private var outputStream: OutputStream? = null
    private var printStream: PrintStream? = null

    init {
        initializeStreams()
    }

    private fun initializeStreams() {
        if (!initialized.getAndSet(true)) {
            try {
                logFile.parentFile?.mkdirs()
                outputStream = logFile.outputStream().buffered()
                printStream = PrintStream(outputStream!!, true, "UTF-8")
            } catch (e: Exception) {
                System.err.println("Failed to initialize logger: ${e.message}")
                initialized.set(false)
            }
        }
    }

    override fun debug(message: String, vararg args: Any?) {
        if (isDebugEnabled()) {
            log("DEBUG", message, args)
        }
    }

    override fun info(message: String, vararg args: Any?) {
        log("INFO ", message, args)
    }

    override fun warn(message: String, vararg args: Any?) {
        log("WARN ", message, args)
    }

    override fun error(message: String, vararg args: Any?, error: Throwable?) {
        val fullMessage = if (error != null) {
            "$message\n${error.stackTraceToString()}"
        } else {
            message
        }
        log("ERROR", fullMessage, args)
    }

    override fun isDebugEnabled(): Boolean = true // Enable debug logs by default, can be made configurable

    private fun log(level: String, message: String, args: Array<out Any?>) {
        val timestamp = dateFormat.format(Date())
        val threadName = Thread.currentThread().name
        val formattedMessage = if (args.isNotEmpty()) message.format(*args) else message
        val logEntry = "$timestamp [$threadName] $level - $formattedMessage\n"

        // Write to console
        when (level.trim()) {
            "ERROR" -> System.err.print(logEntry)
            else -> print(logEntry)
        }

        // Write to file if initialized
        if (initialized.get()) {
            logLock.withLock {
                try {
                    printStream?.print(logEntry)
                    printStream?.flush()
                } catch (e: Exception) {
                    System.err.println("Failed to write to log file: ${e.message}")
                    initialized.set(false)
                    closeStreams()
                }
            }
        }
    }

    private fun closeStreams() {
        printStream?.close()
        outputStream?.close()
    }

    fun close() {
        closeStreams()
        initialized.set(false)
    }
}

/**
 * Creates a new logger instance that writes to the specified file.
 * @param logFile The file to write logs to
 * @return A new Logger instance
 */
fun createLogger(logFile: File): Logger = DefaultLogger(logFile)
