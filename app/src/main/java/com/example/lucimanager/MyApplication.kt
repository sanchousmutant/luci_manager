package com.example.lucimanager

import android.app.Application
import com.example.lucimanager.service.NotificationHelper
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Create notification channel
        NotificationHelper.createNotificationChannel(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
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
    }
}
