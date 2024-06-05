package com.pdi.escanerapp

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class OcrManager(private val context: Context) {

    companion object {
        private const val TESSDATA = "tessdata"
        private const val TAG = "EscanerApp/OCR"
    }

    private var tessBaseAPI: TessBaseAPI? = null

    fun initTesseract() {
        tessBaseAPI = TessBaseAPI()
        val dataPath = context.filesDir.toString() + "/tesseract/"
        val tessDataPath = dataPath + TESSDATA
        checkFile(File(tessDataPath))
        tessBaseAPI?.init(dataPath, "spa") // Specify the language(s) here
    }

    private fun checkFile(dir: File) {
        if (!dir.exists() && dir.mkdirs()) {
            copyFiles()
        }
        if (dir.exists()) {
            val dataFilePath = "$dir/spa.traineddata"
            val dataFile = File(dataFilePath)
            if (!dataFile.exists()) {
                copyFiles()
            }
        }
    }

    private fun copyFiles() {
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("tessdata/spa.traineddata")
            val outDir = context.filesDir.toString() + "/tesseract/tessdata/"
            val outFile = File(outDir, "spa.traineddata")
            val out = FileOutputStream(outFile)
            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }
            inputStream.close()
            out.flush()
            out.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getOCRResult(bitmap: Bitmap): String? {
        tessBaseAPI?.setImage(bitmap)
        return tessBaseAPI?.utF8Text
    }

    fun stop() {
        tessBaseAPI?.stop()
    }

    fun onDestroy() {
        tessBaseAPI?.recycle()
    }
}
