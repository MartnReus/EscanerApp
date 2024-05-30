package com.pdi.escanerapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.opencv.android.CameraActivity
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.CvType.CV_8UC1
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

class CameraActivity : CameraActivity(), CvCameraViewListener2 {

    companion object {
        private const val TAG = "EscanerApp/Camera"
    }

    private lateinit var mOpenCvCameraView: CameraBridgeViewBase;
    private lateinit var mTakePictureButton: Button;
    private lateinit var mCapturedFrame: Mat;
    private var mBoundingBox: MatOfPoint? = null;
    private var mContours: List<MatOfPoint>? = null;
    private var frameCounter = 0


    init {
        Log.i(TAG,"Instantiated new ${this::class.java.simpleName}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG,"called onCreate")

        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show();
            return;
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_camera)

        mOpenCvCameraView = findViewById(R.id.camera_view)
        mOpenCvCameraView.setMaxFrameSize(1920,1080);
        mOpenCvCameraView.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView.setCvCameraViewListener(this)

        if (ActivityCompat.checkSelfPermission(
                this,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG,"Camera Permission: ${true}")
            mOpenCvCameraView.setCameraPermissionGranted()
        } else {
            Log.i(TAG,"Camera Permission: ${false}")
            (Toast.makeText(this, "Error while checking camera permission, try again", Toast.LENGTH_LONG)).show();
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        mTakePictureButton = findViewById(R.id.btn_take_picture)
        mTakePictureButton.setOnClickListener {
            captureAndReturnFrame()
        }

    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG,"called onPause")
        mOpenCvCameraView.disableView()
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG,"called onResume")
        mOpenCvCameraView.enableView()

    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        Log.i(TAG,"called onCameraViewStarted")
    }

    override fun onCameraViewStopped() {
        Log.i(TAG,"called onCameraViewStopped")
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        mCapturedFrame = inputFrame.rgba()

        val processedFrame = processLiveFrame(inputFrame.rgba())
        frameCounter++

        return processedFrame
    }
    private fun processLiveFrame(frame: Mat, thr: Double = 190.0, thr2: Double = 230.0, fixPosition: Boolean = false): Mat {
        val image = frame.clone()

//        val ratio = image.rows() / 500.0
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

//        val boundedImage = Mat()
//        Imgproc.resize(connected_edged, boundedImage, Size(orig.cols().toDouble(), orig.rows().toDouble()))
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

//        val imagewithcontours: mat = if (mcontours != null) {
//            plotcontours(image, mcontours)
//        } else {
//            image.clone()
//        }
//        return imagewithcontours

//        Log.i(TAG,"Size contours: ${contours.size}")

        var boundingBox: MatOfPoint2f? = null
        var screenCnt: MatOfPoint2f? = null
        for (c in maxContours) {
//            val matC = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
            Log.i(TAG,"Size contours: ${approx}")
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
            return Mat(1, 1, CV_8UC1, Scalar(1.0))
        }

        return Mat(1, 1, CV_8UC1, Scalar(1.0))
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

    private fun fourPointTransform(image: Mat, pts: MatOfPoint2f): Mat {
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

    //    private fun processLiveFrame(frame: Mat): Mat{
//
//        var image = frame
//        // Resize the image
//        val newWidth = (image.width() / ratio).toInt()
//        val newSize = Size(newWidth.toDouble(), 500.0)
//        val resizedImage = Mat()
//        Imgproc.resize(image, resizedImage, newSize)
//
//        // Convert to grayscale
//        val gray = Mat()
//        Imgproc.cvtColor(resizedImage, gray, Imgproc.COLOR_BGR2GRAY)
//
//        // Apply Gaussian blur
//        val blurred = Mat()
//        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
//
//        // Apply morphological transformation
//        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
//        val morphed = Mat()
//        Imgproc.morphologyEx(blurred, morphed, Imgproc.MORPH_CLOSE, kernel, Point(-1.0, -1.0), 3)
//
//        // Detect edges
//        val edged = Mat()
//        Imgproc.Canny(morphed, edged, thr, thr2)
//
//        // Find contours
//        val contours = ArrayList<MatOfPoint>()
//        val hierarchy = Mat()
//        Imgproc.findContours(edged.clone(), contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
//
//        // Sort contours by area in descending order and take the top 5
//        contours.sortByDescending { Imgproc.contourArea(it) }
//        return contours.take(5)
//
//
//
//        return frame
//    }
    private fun captureAndReturnFrame() {
//        val proccessedFrame = proccessFrame(mCapturedFrame)

        var finalFrame = mCapturedFrame
        if (mBoundingBox != null)
            finalFrame = fourPointTransform(mCapturedFrame, MatOfPoint2f(*mBoundingBox?.toArray()))

//        Imgproc.GaussianBlur(finalFrame, finalFrame, Size(3.0, 3.0), 0.0)


        val bitmap = convertMatToBitmap(finalFrame)
//        val rotatedBitmap = rotateBitmap(bitmap,90f)
        val filePath = saveBitmapToFile(bitmap)

        val resultIntent= Intent()
        resultIntent.putExtra("image_path",filePath)
        setResult(Activity.RESULT_OK,resultIntent)
        finish()
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
    }

    private fun convertMatToBitmap(mat: Mat): Bitmap {
        val bmp = Bitmap.createBitmap(mat.cols(),mat.rows(),Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat,bmp)
        return bmp
    }

    private fun saveBitmapToFile(bitmap: Bitmap): String? {
        val fileName = "captured_image.png"
        val file = File(getExternalFilesDir(null),fileName)
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
    fun proccessFrame(inputFrame: Mat): Mat {

        // Convertir a blanco y negro

        val bwFrame = Mat()
        Imgproc.cvtColor(inputFrame,bwFrame,Imgproc.COLOR_RGBA2GRAY)

        // Convertirlo a 32 bits
        val bwFrame32= Mat()
        bwFrame.convertTo(bwFrame32, CvType.CV_32F, 1.0 / 255.0)

        val kernelH = Mat(3,3, CvType.CV_32F)
        val kernelV = Mat(3,3, CvType.CV_32F)

        val kernelDataV = floatArrayOf(
            -1.0f,0.0f,1.0f,
            -2.0f,0.0f,2.0f,
            -1.0f,0.0f,1.0f
        )

        val kernelDataH = floatArrayOf(
            -1.0f,-2.0f,-1.0f,
            0.0f,0.0f,0.0f,
            1.0f,2.0f,1.0f
        )
        kernelH.put(0,0,kernelDataH)
        kernelV.put(0,0,kernelDataV)


        val edgesX = Mat()
        val edgesY = Mat()
        Imgproc.filter2D(bwFrame32,edgesX,-1,kernelH,Point(-1.0,-1.0))
        Imgproc.filter2D(bwFrame32,edgesY,-1,kernelV,Point(-1.0,-1.0))

        val filteredFrame32 = Mat()
        val filteredFrame32Abs = Mat()
        val filteredFrame = Mat()

        Core.add(edgesX,edgesY,filteredFrame32)
        Core.absdiff(filteredFrame32, Scalar(0.0),filteredFrame32Abs)

        val minMaxResult = Core.minMaxLoc(filteredFrame32Abs)
        val alpha = 255.0 / (minMaxResult.maxVal - minMaxResult.minVal)
        val beta = -minMaxResult.minVal * alpha
        filteredFrame32Abs.convertTo(filteredFrame, CvType.CV_8U, alpha, beta)
        return filteredFrame
    }

}