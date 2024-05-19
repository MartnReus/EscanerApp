package com.pdi.escanerapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

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