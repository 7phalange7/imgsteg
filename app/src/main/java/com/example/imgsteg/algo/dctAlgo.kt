package com.example.imgsteg.algo

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat

object dctAlgo {

    fun encode(bitmap: Bitmap, mssg: String): Bitmap {

        //obtain Mat form bitmap
        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // convert to mutable bitmap
        var inMat = Mat(mbitmap.width,mbitmap.height, CvType.CV_8UC4)
        Utils.bitmapToMat(mbitmap, inMat)

        var msg = START_MESSAGE_CONSTANT + mssg + END_MESSAGE_CONSTANT


        return mbitmap

    }
}