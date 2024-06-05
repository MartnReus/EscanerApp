package com.pdi.escanerapp

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
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
    var mConvexHull: MatOfPoint2f? = null
    lateinit var mContours: List<MatOfPoint>
    lateinit var tess: TessBaseAPI;

    fun loadBitmapAsMat(bitmap: Bitmap) {
        Utils.bitmapToMat(bitmap, matImage)
    }

    fun getMatAsBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(matImage, bitmap)
        return bitmap
    }


    fun addContours(contours: List<MatOfPoint>): Bitmap {
        val boxColor = Scalar(0.0, 0.0, 255.0)
        val thickness = 3
        Imgproc.drawContours(matImage, contours, -1, boxColor, thickness)

        val bitmap = getMatAsBitmap(matImage)
        return bitmap
    }

    fun addBoundingBox(points: MatOfPoint?): Bitmap {
        if (points != null) {
            val boxColor = Scalar(0.0, 255.0, 0.0)
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
        Imgproc.GaussianBlur(mat, mat, Size(kernelSize, kernelSize), 0.0)
        return mat
    }

    fun matMorphClose(mat: Mat, kernelSize: Double, iterations: Int): Mat {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(kernelSize, kernelSize))
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
            Imgproc.line(
                connectedEdged,
                Point(line[0], line[1]),
                Point(line[2], line[3]),
                linesColor,
                3
            )
        }

        return connectedEdged
    }

    fun matDetectContours(mat: Mat): List<MatOfPoint> {
        var contours = mutableListOf<MatOfPoint>()

        val hierarchy = Mat()
        Imgproc.findContours(
            mat,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        contours.sortByDescending { Imgproc.contourArea(it) }

        contours = contours.filter {
            Imgproc.arcLength(MatOfPoint2f(*it.toArray()), true) > 1100
        }.toMutableList()

        return contours
    }

    fun findConvexHull(contours: List<MatOfPoint>): MatOfPoint? {

        var convexHull: MatOfPoint2f? = null

        for (c in contours) {
            val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
//            Log.i(TAG,"Perimeter: $peri")
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
            if (approx.rows() > 1) {
                val rotatedRect = Imgproc.minAreaRect(MatOfPoint2f(*c.toArray()))

                val box = MatOfPoint2f()
                Imgproc.boxPoints(rotatedRect, box)
                rotatedRect.points(box.toArray())
                val hull = MatOfInt()
                val approxPoints = approx.toArray()
                Imgproc.convexHull(MatOfPoint(*approxPoints), hull)

                val hullPoints = hull.toArray().map { index ->
                    approxPoints[index]
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

        mConvexHull = convexHull
        if (mConvexHull == null) {
            return null
        }

        return MatOfPoint(*mConvexHull?.toArray())
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
        val tl: Mat = rect.rowRange(0, 1)
        val tr: Mat = rect.rowRange(1, 2)
        val br: Mat = rect.rowRange(2, 3)
        val bl: Mat = rect.rowRange(3, 4)
//
        val widthA = Core.norm(br, bl)
        val widthB = Core.norm(tr, tl)
//        val widthA = Core.norm(bl,br)
//        val widthB = Core.norm(tl,tr)
        val maxWidth = widthA.coerceAtLeast(widthB).toFloat()

        val heightA = Core.norm(tr, br)
        val heightB = Core.norm(tl, bl)
        val maxHeight = heightA.coerceAtLeast(heightB).toFloat()

        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((maxWidth - 1).toDouble(), 0.0),
            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
            Point(0.0, (maxHeight - 1).toDouble())
        )

        val matrix = Imgproc.getPerspectiveTransform(rect, dst)
        val warped = Mat()
        Imgproc.warpPerspective(
            image,
            warped,
            matrix,
            Size(maxWidth.toDouble(), maxHeight.toDouble())
        )

        val flipped = Mat()
        Core.flip(warped, flipped, 1)
//        Core.transpose(warped,flipped)

        return flipped
    }

    fun initializeTesseract(context: Context): Int {
        tess = TessBaseAPI()
        val dataPath = File(context.filesDir, "tessseract")
        val returnedValue = checkFile(dataPath,context)

        return returnedValue
    }
    private fun checkFile(dir: File, context: Context): Int {
        try {
            if (!dir.exists() && dir.mkdirs()) {
                copyFiles(context)
            }
            if (dir.exists()) {
                val dataFilePath = "$dir/spa.traineddata"
                val dataFile = File(dataFilePath)
                if (!dataFile.exists()) {
                    copyFiles(context)
                }
            }
        } catch (e: Exception) {
            return -1
        }

        return 0
    }

    private fun copyFiles(context: Context) {
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

    fun ocrRead(image: Bitmap): String {
        return ""
    }

}