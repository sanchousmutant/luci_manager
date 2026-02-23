package com.example.lucimanager

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(throwable)
        }
    }

    private fun handleUncaughtException(throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        try {
            val file = File(filesDir, "crash_log.txt")
            file.writeText(stackTrace)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        exitProcess(1)
    }
}
