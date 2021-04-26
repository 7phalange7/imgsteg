package com.example.imgsteg

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.OpenCVLoader


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenCVLoader.initDebug()
        setContentView(R.layout.activity_main)

    }


}