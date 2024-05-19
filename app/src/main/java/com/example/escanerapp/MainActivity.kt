package com.example.escanerapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.SurfaceControlViewHost
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.escanerapp.CameraActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EscanerApp/Main"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        Log.i(TAG,"called onCreate")
        setContentView(R.layout.activity_main)
        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            val intent = Intent(this,CameraActivity::class.java)
            startActivity(intent)
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