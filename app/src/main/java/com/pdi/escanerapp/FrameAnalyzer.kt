package com.pdi.escanerapp

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.widget.ImageView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class FrameAnalyzer(private val previewView: ImageView): ImageAnalysis.Analyzer {
    companion object {
        private const val TAG = "EscanerApp/Utils"
    }

    private var mBoundingBox: MatOfPoint? = null;
    private var mContours: List<MatOfPoint>? = null;

    private lateinit var bitmapBuffer: Bitmap

    var frameCounter = 0
        private set

    fun incrementFrameCounter() {
        frameCounter += 1;
    }

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

        if (frameCounter % 5 == 0) {
            val processedBitmap = processBitmap(rotatedBitmap, image.width, image.height)
            updatePreview(processedBitmap)
        } else {
            val boundedBitmap = addBoundingBox(rotatedBitmap)
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

        val mat = Mat(height,width,CvType.CV_8UC3)
        Utils.bitmapToMat(bitmap,mat)
        val processedMat = processLiveFrame(mat)
        val finalBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(processedMat,finalBitmap)

        // Update the preview with the processed bitmap
        return finalBitmap
    }

    private fun updatePreview(bitmap: Bitmap) {
        previewView.post {
            previewView.setImageBitmap(bitmap)
        }
    }
    private fun processLiveFrame(frame: Mat, thr: Double = 190.0, thr2: Double = 230.0, fixPosition: Boolean = false): Mat {
        val image = frame.clone()

        val orig = image.clone()
//        Imgproc.resize(image, image, Size((image.cols() / ratio), 500.0))

        val gray = Mat()
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val morphed = Mat()
        Imgproc.morphologyEx(gray, morphed, Imgproc.MORPH_CLOSE, kernel, Point(), 3)

        val edged = Mat()
        Imgproc.Canny(morphed, edged, thr, thr2)
        val connected_edged = edged.clone()
        var linesDetected = Mat()
        // Use Hough Line Transform to detect lines
        Imgproc.HoughLinesP(edged, linesDetected, 1.0, Math.PI / 180, 150, 80.0, 120.0)

        // Draw detected lines on the original image
        val linesColor = Scalar(255.0, 0.0, 0.0)
        for (i in 0 until linesDetected.rows()) {
            val line = linesDetected[i, 0]
            Imgproc.line(connected_edged, Point(line[0], line[1]), Point(line[2], line[3]), linesColor, 3)
        }

        val boundedImage = Mat()
        Imgproc.resize(connected_edged, boundedImage, Size(orig.cols().toDouble(), orig.rows().toDouble()))
//        return boundedImage

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edged.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        contours.sortByDescending { Imgproc.contourArea(it) }
        val maxContours = contours.subList(0, minOf(contours.size, 5))

        if (frameCounter % 5 == 0) {
            mContours = contours
//            mContours = maxContours
        }

//        val imageWithContours: Mat = if (mContours != null) {
//            plotContours(image, mContours)
//        } else {
//            image.clone()
//        }
//        return imageWithContours

        var boundingBox: MatOfPoint2f? = null
        var screenCnt: MatOfPoint2f? = null
        for (c in maxContours) {
//            val matC = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
//            Log.i(TAG,"Size contours: $approx")
            if (approx.rows() > 1) {
                val rotatedRect = Imgproc.minAreaRect(MatOfPoint2f(*c.toArray()))
                val box = MatOfPoint2f()
                Imgproc.boxPoints(rotatedRect,box)
                rotatedRect.points(box.toArray())
                val hull = MatOfInt()
                val approxPoints = approx.toArray()
                Imgproc.convexHull(MatOfPoint(*approxPoints), hull)

                val hullPoints = hull.toArray().map {
                        index -> approxPoints[index]
                }.toTypedArray()
                val approxHull = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*hullPoints), approxHull, 0.02 * peri, true)
                if (approxHull.rows() == 4) {
                    boundingBox = approxHull
                    screenCnt = approxHull
                    break
                }
                break
            }
        }
        if (frameCounter % 2 == 0 && boundingBox != null) {
            mBoundingBox = MatOfPoint(*boundingBox.toArray())
        }

        val imageWithBoundingBox: Mat = if (mBoundingBox != null) {
            plotBoundingBox(image, mBoundingBox)
        } else {
            image.clone()
        }

        if (!fixPosition) {
//            Imgproc.resize(imageWithBoundingBox, boundedImage, Size(orig.cols().toDouble(), orig.rows().toDouble()))
            return imageWithBoundingBox
        }

        if (screenCnt == null) {
            return Mat(1, 1, CvType.CV_8UC1, Scalar(1.0))
        }

        return Mat(1, 1, CvType.CV_8UC1, Scalar(1.0))
    }

    private fun plotBoundingBox(image: Mat, box: MatOfPoint?, boxColor: Scalar = Scalar(0.0, 0.0, 255.0), thickness: Int = 3): Mat {
        val imageWithBox = image.clone()
        if (box != null)
            Imgproc.drawContours(imageWithBox, listOf(box), -1, boxColor, thickness)
        return imageWithBox
    }

    private fun plotContours(image: Mat, contours: List<MatOfPoint>?, boxColor: Scalar = Scalar(0.0, 0.0, 255.0), thickness: Int = 3): Mat {
        val imageWithContours = image.clone()
        if (contours != null)
            Imgproc.drawContours(imageWithContours, contours, -1, boxColor, thickness)
        return imageWithContours
    }

    private fun addBoundingBox(bitmap: Bitmap): Bitmap {

        if (mBoundingBox == null) {
            return bitmap
        }

        val imageMat = Mat()
        Utils.bitmapToMat(bitmap,imageMat)

        val boxColor = Scalar(0.0, 0.0, 255.0)
        val thickness = 3

        Imgproc.drawContours(imageMat, listOf(mBoundingBox), -1, boxColor, thickness)
//        Imgproc.drawContours(imageMat, mContours, -1, boxColor, thickness)
        Utils.matToBitmap(imageMat,bitmap)

        return bitmap
    }

}