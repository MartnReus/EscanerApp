package com.pdi.escanerapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EscanerApp/Main"
        const val REQUEST_IMAGE_CAPTURE = 1
    }

    private lateinit var imageView: ImageView
    private val cameraActivityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
        result ->
            if (result.resultCode == RESULT_OK) {
                val filePath = result.data?.getStringExtra("image_path")
                if (filePath != null) {
                    val bitmap = BitmapFactory.decodeFile(filePath)
                    imageView.setImageBitmap(bitmap)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.image_view)
        val btnOpenCamera = findViewById<Button>(R.id.btn_open_camera)

        btnOpenCamera.setOnClickListener {
            val intent = Intent(this,CameraActivity::class.java)
            cameraActivityResultLauncher.launch(intent)
        }
    }

    override fun onPause() {
        super.onPause()
//        Log.i(TAG,"called onPause")
    }

    override fun onResume() {
        super.onResume()
//        Log.i(TAG,"called onResume")
    }



}