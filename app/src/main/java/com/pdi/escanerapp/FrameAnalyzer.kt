package com.pdi.escanerapp

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.widget.ImageView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.pdi.escanerapp.EscanerUtils.matImage
import org.opencv.core.MatOfPoint

class FrameAnalyzer(private val previewView: ImageView): ImageAnalysis.Analyzer {
    companion object {
        private const val TAG = "EscanerApp/Utils"
    }

    private lateinit var bitmapBuffer: Bitmap

    private var frameCounter = 0

    private fun incrementFrameCounter() {
        frameCounter += 1;
        if (frameCounter % 10 == 0) Log.i(TAG,"Frame nro: $frameCounter")
        if (frameCounter > 100) frameCounter = 1
    }

    private var mConvexHull: MatOfPoint? = null

    override fun analyze(image: ImageProxy) {
        if (!::bitmapBuffer.isInitialized) {
            // The image rotation and RGB image buffer are initialized only once
            // the analyzer has started running
            val imageRotationDegrees = image.imageInfo.rotationDegrees
            bitmapBuffer = Bitmap.createBitmap(
                image.width, image.height, Bitmap.Config.ARGB_8888)
        }
        try {
        val bitmapBuffer = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        image.use {
            val buffer = image.planes[0].buffer
            bitmapBuffer.copyPixelsFromBuffer(buffer)
            buffer.rewind()
        }
        incrementFrameCounter()
        val rotatedBitmap = rotateBitmap(bitmapBuffer)

        if (frameCounter % 10 == 0) {
            val processedBitmap = processBitmap(rotatedBitmap, image.width, image.height)
            updatePreview(processedBitmap)
        } else {
            var boundedBitmap = rotatedBitmap
            if (mConvexHull != null) {
                EscanerUtils.loadBitmapAsMat(rotatedBitmap)
                boundedBitmap = EscanerUtils.addBoundingBox(mConvexHull)
            }
            updatePreview(boundedBitmap)
        }

        } catch(exc: Exception) {
            Log.e("FrameAnalyzer","Error: $exc")
        } finally {
            image.close()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            postRotate(90f)
        }

        val newBitmap = Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        return newBitmap
    }


    private fun processBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val filterKernelSize = 5.0

        val morphKernelSize = 5.0
        val morphItNumber = 3

        val thr1 = 150.0
        val thr2 = 230.0

        EscanerUtils.loadBitmapAsMat(bitmap)
        matImage = EscanerUtils.matToGrayscale(matImage)
        matImage = EscanerUtils.matFilterGaussian(matImage,filterKernelSize)
        matImage = EscanerUtils.matMorphClose(matImage,morphKernelSize,morphItNumber)
        matImage = EscanerUtils.matCanny(matImage,thr1,thr2)
//        matImage = EscanerUtils.matEdgesHough(matImage)
        val contours = EscanerUtils.matDetectContours(matImage)
        mConvexHull = EscanerUtils.findConvexHull(contours)

        EscanerUtils.loadBitmapAsMat(bitmap)
        if (mConvexHull != null) EscanerUtils.addBoundingBox(mConvexHull)

        val finalBitmap = EscanerUtils.getMatAsBitmap(matImage)
        return finalBitmap
    }

    private fun updatePreview(bitmap: Bitmap) {
        previewView.post {
            previewView.setImageBitmap(bitmap)
        }
    }
}