package com.josxtm.autocontrol.community

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises lifecycle state cleanup and stale-callback isolation through the real URI path. */
@RunWith(AndroidJUnit4::class)
class CsvModelTest {
    @Test fun cancellationFailureAndNextPreviewRestoreControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        lateinit var model: WorkspaceModel
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun awaitIdle() {
            val deadline = System.nanoTime() + 15_000_000_000L
            var busy = true
            while (busy && System.nanoTime() < deadline) {
                main { busy = model.busy }
                if (busy) Thread.sleep(10)
            }
            main { assertFalse(model.busy); assertNull(model.csvProgress); assertFalse(model.csvCancelling) }
        }
        val file = File(application.cacheDir, "synthetic-model.csv")
        try {
            main { model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory(application))[WorkspaceModel::class.java] }
            awaitIdle()
            file.writeText("MATRICULA;NOMBRE\n28-SYN-MODEL;synthetic\n")
            main { model.previewCsv(Uri.fromFile(file)); model.cancelCsv() }
            awaitIdle()
            main { assertTrue(model.message.contains("cancelada")); assertNull(model.csvPreview) }
            file.writeText("WRONG;NOMBRE\n28-SYN-MODEL;synthetic\n")
            main { model.previewCsv(Uri.fromFile(file)) }
            awaitIdle()
            main { assertTrue(model.message.contains("Cabeceras")); assertNull(model.csvPreview) }
            main { model.previewCsv(Uri.fromFile(File(application.cacheDir, "synthetic-absent.csv"))) }
            awaitIdle()
            main { assertTrue(model.message.contains("acceder")); assertNull(model.csvPreview) }
            val valid = "MATRICULA;NOMBRE\n28-SYN-MODEL;café\n"
            file.writeBytes(valid.toByteArray(charset("windows-1252")))
            main { model.previewCsv(Uri.fromFile(file)) }
            awaitIdle()
            main { assertEquals(1, model.csvPreview?.rows); assertTrue(model.message.contains("Vista previa")) }
            java.util.zip.GZIPOutputStream(file.outputStream()).use { it.write(valid.toByteArray()) }
            main { model.previewCsv(Uri.fromFile(file)) }
            awaitIdle()
            main { assertEquals(1, model.csvPreview?.rows) }
            java.io.RandomAccessFile(file, "rw").use { it.setLength(it.length() - 5) }
            main { model.previewCsv(Uri.fromFile(file)) }
            awaitIdle()
            main { assertTrue(model.message.contains("GZIP")); assertNull(model.csvPreview) }
        } finally {
            main { owner.viewModelStore.clear() }
            file.delete()
        }
    }
}
