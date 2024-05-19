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
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
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
        return inputFrame.rgba()
    }

    private fun captureAndReturnFrame() {
        val proccessedFrame = proccessFrame(mCapturedFrame)
        val bitmap = convertMatToBitmap(proccessedFrame)
        val rotatedBitmap = rotateBitmap(bitmap,90f)
        val filePath = saveBitmapToFile(rotatedBitmap)

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