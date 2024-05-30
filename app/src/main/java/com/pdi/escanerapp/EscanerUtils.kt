package com.pdi.escanerapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object EscanerUtils {
    private const val TAG = "EscanerApp/Utils"

    var matImage: Mat = Mat()

    var mBoundingBox: MatOfPoint? = null
        private set
    private var mContours: List<MatOfPoint>? = null
        private set

    var frameCounter = 0
        private set

    fun incrementFrameCounter() {
        frameCounter += 1;
    }

    fun loadBitmapAsMat(bitmap: Bitmap) {
        Utils.bitmapToMat(bitmap,matImage)
    }

    fun getMatAsBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(),mat.rows(),Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(matImage,bitmap)
        return bitmap
    }


     fun addBoundingBox(points: MatOfPoint?): Bitmap {
         if (points != null)  {
             val boxColor = Scalar(0.0, 0.0, 255.0)
             val thickness = 3
             Imgproc.drawContours(matImage, listOf(points), -1, boxColor, thickness)
         }

        val bitmap = getMatAsBitmap(matImage)
        return bitmap
    }

    fun matToGrayscale(mat: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        return gray
    }

    fun matFilterGaussian(mat: Mat, kernelSize: Double): Mat {
        Imgproc.GaussianBlur(mat, mat, Size(kernelSize,kernelSize), 0.0)
        return mat
    }

    fun matMorphClose(mat: Mat, kernelSize: Double, iterations: Int): Mat {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(kernelSize,kernelSize))
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, kernel, Point(), iterations)
        return mat
    }

    fun matCanny(mat: Mat, thr1: Double, thr2: Double): Mat {
        val edged = Mat()
        Imgproc.Canny(mat, edged, thr1, thr2)
        return edged
    }

    fun matEdgesHough(mat: Mat): Mat {

        val connectedEdged = mat.clone()
        val linesDetected = Mat()
        // Use Hough Line Transform to detect lines
        Imgproc.HoughLinesP(mat, linesDetected, 1.0, Math.PI / 180, 150, 80.0, 120.0)

        // Draw detected lines on the original image
        val linesColor = Scalar(255.0, 0.0, 0.0)
        for (i in 0 until linesDetected.rows()) {
            val line = linesDetected[i, 0]
            Imgproc.line(connectedEdged, Point(line[0], line[1]), Point(line[2], line[3]), linesColor, 3)
        }

        return connectedEdged
    }

    fun matDetectContours(mat: Mat): List<MatOfPoint> {
        val contours = mutableListOf<MatOfPoint>()

        val hierarchy = Mat()
        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        contours.sortByDescending { Imgproc.contourArea(it) }

        return contours
    }

    fun findConvexHull(contours: List<MatOfPoint>): MatOfPoint? {

        var convexHull: MatOfPoint2f? = null

        for (c in contours) {
            val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
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
                    convexHull = approxHull
                    break
                }
                break
            }
        }

        if (convexHull == null) return null

        return MatOfPoint(*convexHull.toArray())
    }




    private fun orderPoints(pts: MatOfPoint2f): Mat {
        val rect = Mat.zeros(4, 2, CvType.CV_32F)
        val points = pts.toArray()
        val s = Mat.zeros(4, 1, CvType.CV_32F)
        for (i in 0 until 4) {
            s.put(i, 0, points[i].x + points[i].y)
        }
        var minMaxLocResult = Core.minMaxLoc(s)
        val idxMinSum = minMaxLocResult.minLoc.y
        val idxMaxSum = minMaxLocResult.maxLoc.y

        val minSum = points[idxMinSum.toInt()]
        val maxSum = points[idxMaxSum.toInt()]

        rect.put(0, 0, minSum.x, minSum.y)
        rect.put(2, 0, maxSum.x, maxSum.y)

        val diff = Mat.zeros(4, 1, CvType.CV_32F)
        for (i in 0 until 4) {
            diff.put(i, 0, points[i].x - points[i].y)
        }

        minMaxLocResult = Core.minMaxLoc(diff)
        val idxMinDiff = minMaxLocResult.minLoc.y
        val idxMaxDiff = minMaxLocResult.maxLoc.y

        val minDiff = points[idxMinDiff.toInt()]
        val maxDiff = points[idxMaxDiff.toInt()]
        rect.put(1, 0, minDiff.x, minDiff.y)
        rect.put(3, 0, maxDiff.x, maxDiff.y)

        return rect
    }

    fun fourPointTransform(image: Mat, pts: MatOfPoint2f): Mat {
        val rect = orderPoints(pts)
        val tl: Mat = rect.rowRange(0,1)
        val tr: Mat = rect.rowRange(1,2)
        val br: Mat = rect.rowRange(2,3)
        val bl: Mat = rect.rowRange(3,4)
//
        val widthA = Core.norm(br,bl)
        val widthB = Core.norm(tr,tl)
//        val widthA = Core.norm(bl,br)
//        val widthB = Core.norm(tl,tr)
        val maxWidth = widthA.coerceAtLeast(widthB).toFloat()

        val heightA = Core.norm(tr,br)
        val heightB = Core.norm(tl,bl)
        val maxHeight = heightA.coerceAtLeast(heightB).toFloat()

        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((maxWidth - 1).toDouble(), 0.0),
            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
            Point(0.0, (maxHeight - 1).toDouble())
        )

        val matrix = Imgproc.getPerspectiveTransform(rect, dst)
        val warped = Mat()
        Imgproc.warpPerspective(image, warped, matrix, Size(maxWidth.toDouble(), maxHeight.toDouble()))

        val flipped = Mat()
        Core.flip(warped,flipped,1)
//        Core.transpose(warped,flipped)

        return flipped
    }

    fun processLiveFrame(frame: Mat, thr: Double = 150.0, thr2: Double = 230.0, fixPosition: Boolean = false): Mat {
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
        if (boundingBox != null) {
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

    fun saveBitmapToFile(bitmap: Bitmap, context: Context): String? {
        val fileName = "captured_image.png"
        val file = File(context.getExternalFilesDir(null),fileName)
        return try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out)
            out.flush()
            out.close()
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
//    private fun captureAndReturnFrame() {
////        val proccessedFrame = proccessFrame(mCapturedFrame)
//
//        var finalFrame = mCapturedFrame
//        if (mBoundingBox != null)
//            finalFrame = fourPointTransform(mCapturedFrame, MatOfPoint2f(*mBoundingBox?.toArray()))
//
////        Imgproc.GaussianBlur(finalFrame, finalFrame, Size(3.0, 3.0), 0.0)
//
//
//        val bitmap = convertMatToBitmap(finalFrame)
////        val rotatedBitmap = rotateBitmap(bitmap,90f)
//        val filePath = saveBitmapToFile(bitmap)
//
//        val resultIntent = Intent()
//        resultIntent.putExtra("image_path", filePath)
//        setResult(Activity.RESULT_OK, resultIntent)
//        finish()
//    }
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

//    private fun orderPoints(pts: MatOfPoint2f): MatOfPoint2f {
//        val rect = MatOfPoint2f()
//        val size = Size(4.0, 2.0)
//        rect.create(size, CvType.CV_32F)
//
//        val s = pts.sum(axis = 1)
//        rect.put(0, 0, *pts[pts.indexOf(minMaxLoc(s).minLoc.x)])
//        rect.put(2, 0, *pts[pts.indexOf(minMaxLoc(s).maxLoc.x)])
//
//        val diff = Core.subtract(pts.col(0), pts.col(1))
//        rect.put(1, 0, *pts[pts.indexOf(minMaxLoc(diff).minLoc.x)])
//        rect.put(3, 0, *pts[pts.indexOf(minMaxLoc(diff).maxLoc.x)])
//
//        return rect
//    }


}