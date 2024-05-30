package com.pdi.escanerapp

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.pdi.escanerapp.EscanerUtils.matImage
import com.pdi.escanerapp.databinding.ActivityCamera2Binding
import org.opencv.android.OpenCVLoader
import org.opencv.core.MatOfPoint2f
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit
class Camera2Activity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityCamera2Binding

    private val mFileName = "captured_image.jpg";
    private lateinit var mExternalUri: String;

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val TAG = "EscanerApp/Camera"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCamera2Binding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show();
            return;
        }
        viewBinding.btnTakePicture.setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(android.util.Size(1024,576),
//                        ResolutionStrategy(android.util.Size(1280,720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER )
                )
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()

            val frameAnalyzer = FrameAnalyzer(viewBinding.previewView)
            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, frameAnalyzer)
                }

            // Select back camera as default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this,cameraSelector,
//                    preview,
                    imageCapture,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }


        },ContextCompat.getMainExecutor(this))

    }
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, mFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EscanerApp")
            }
        }

        // Check if the file already exists and get its URI if it does
        val imageUri = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            arrayOf(mFileName, "Pictures/EscanerApp/"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            } else {
                null
            }
        }

        // If the image URI exists, delete the existing file
        imageUri?.let { contentResolver.delete(it, null, null) }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Setup image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: return

                    try {
                        val filePath = getFilePathFromUri(savedUri)

                        val inputStream = contentResolver.openInputStream(savedUri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()

                        // Edit the bitmap
                        val editedBitmap = processBitmap(bitmap)

                        // Save the edited bitmap with the same name
                        saveBitmapToFile(editedBitmap, savedUri)
                        val msg = "Photo capture succeeded: $savedUri"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, msg)


                        val resultIntent= Intent()
                        resultIntent.putExtra("image_path",filePath)
                        setResult(Activity.RESULT_OK,resultIntent)
                        finish()
                    } catch (e: IOException) {
                        Log.e(TAG, "Error loading bitmap from URI: ${e.message}", e)
                    }
                }            }
        )
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return it.getString(columnIndex)
            }
        }
        return null
    }

    private fun saveBitmapToFile(bitmap: Bitmap, uri: Uri) {
        try {
            val outputStream = contentResolver.openOutputStream(uri)!!
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error saving bitmap to file: ${e.message}", e)
        }
    }

    private fun processBitmap(bitmap: Bitmap): Bitmap {
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
        val convexHull = EscanerUtils.findConvexHull(contours)

        EscanerUtils.loadBitmapAsMat(bitmap)


        matImage = EscanerUtils.fourPointTransform(matImage, MatOfPoint2f(*convexHull?.toArray()))

        val finalBitmap = EscanerUtils.getMatAsBitmap(matImage)
        return finalBitmap
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

}