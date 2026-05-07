package com.tamarilog.codexblanche

import androidx.multidex.MultiDexApplication
import com.tamarilog.codexblanche.data.CodexRepository
import com.tamarilog.codexblanche.sync.DriveSyncRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class CodexBlancheApplication : MultiDexApplication() {
    lateinit var repository: CodexRepository
        private set
    val driveSync by lazy { DriveSyncRepository(this) }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        repository = CodexRepository(this)
    }
}
