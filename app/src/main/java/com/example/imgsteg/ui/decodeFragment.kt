package com.example.imgsteg.ui

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.imgsteg.algo.*
import com.example.imgsteg.algo.dctAlgo.dctLsbDecode
import com.example.imgsteg.algo.dctAlgo.getSeedFromPass
import com.example.imgsteg.algo.dctAlgo.imTOdcts
import com.example.imgsteg.databinding.FragmentDecodeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.ceil
import kotlin.math.log
import kotlin.math.pow
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@ExperimentalTime
class decodeFragment : Fragment() {

    private lateinit var binding: FragmentDecodeBinding
    private lateinit var bbitmap: Bitmap

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentDecodeBinding.inflate(inflater)

        binding.chooseImgBtn.setOnClickListener {
            selectImage()
        }

        binding.revealBtn.setOnClickListener {
            CoroutineScope(Dispatchers.Default).launch {

                val pass = "" + binding.passEditTextD.text.toString()

                val hiddenMsg = decodeImage(bbitmap, pass)

                withContext(Dispatchers.Main) {
                    binding.hiddenMsg.text = hiddenMsg
                }
            }
        }
        return binding.root

    }


    fun selectImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        if (intent.resolveActivity((activity as AppCompatActivity).packageManager) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_GET)
            Log.i(TAG, "intent started")
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_GET && resultCode == Activity.RESULT_OK && data != null) {
            Log.i(TAG, "Result Ok")
            val fullPhotoUri: Uri = data.data!!
            Log.i(TAG, "image received")

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(requireContext().contentResolver, fullPhotoUri))
            } else {
                MediaStore.Images.Media.getBitmap(requireContext().contentResolver, fullPhotoUri)
            }

            bbitmap = bitmap
            binding.coverImage.setImageBitmap(bitmap)
        }

    }

    fun decodeImage(bitmap: Bitmap?, pass: String): String? {

        val picWidth = bitmap!!.width
        val picHeight = bitmap.height
        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // convert to mutable bitmap

        var imIn = Mat(picWidth, picHeight, CvType.CV_8UC4)

        // convert bitmap image to MAT
        Utils.bitmapToMat(mbitmap, imIn)

        // Change image to YCrCb color space
        Imgproc.cvtColor(imIn, imIn, Imgproc.COLOR_BGR2YCrCb)

        //get DCT coefficients from image mat
        val blockDcts: Mat = imTOdcts(imIn)

        // get integer seed from user password
        val seed = getSeedFromPass(pass)

        // read the dct lsb
        val (msgOut,elapsed) = measureTimedValue {
            dctLsbDecode(blockDcts, seed)
        }

        Log.v(TAG,"time elapsed Decode = $elapsed")

        blockDcts.release()
        System.gc()

        return msgOut
    }
}