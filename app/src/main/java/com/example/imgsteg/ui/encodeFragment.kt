package com.example.imgsteg.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.imgsteg.algo.END_MESSAGE_CONSTANT
import com.example.imgsteg.algo.START_MESSAGE_CONSTANT
import com.example.imgsteg.algo.dctAlgo
import com.example.imgsteg.algo.dctAlgo.dctLsbEncode
import com.example.imgsteg.algo.dctAlgo.dctsTOim
import com.example.imgsteg.algo.dctAlgo.getSeedFromPass
import com.example.imgsteg.algo.dctAlgo.imTOdcts
import com.example.imgsteg.algo.dctAlgo.otpEncrypt
import com.example.imgsteg.databinding.FragmentEncodeBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime


const val REQUEST_IMAGE_GET = 1
const val TAG = "UTK"

@ExperimentalTime
class encodeFragment : Fragment() {

    private lateinit var binding: FragmentEncodeBinding
    private lateinit var bbitmap: Bitmap

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        binding = FragmentEncodeBinding.inflate(inflater)



        binding.chooseImgBtn.setOnClickListener {

            selectImage()
        }

        binding.hideBtn.setOnClickListener {

            val msg = binding.dataInput.text.toString()
            val pass = "" + binding.passEditText.text.toString()


            CoroutineScope(Default).launch {

                val bmap = encodeImage(bbitmap, msg, pass)

                withContext(Main) {
                    if (bmap.height > 1) {
                        saveImage(bmap)
                    } else {
                        Toast.makeText(context, "Image size too small", Toast.LENGTH_SHORT).show()
                    }
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
                ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(
                                requireContext().contentResolver,
                                fullPhotoUri
                        )
                )
            } else {
                MediaStore.Images.Media.getBitmap(requireContext().contentResolver, fullPhotoUri)
            }

            bbitmap = bitmap
            binding.coverImage.setImageBitmap(bitmap)
        }

    }

    fun encodeImage(bitmap: Bitmap?, msgg: String, pass: String): Bitmap {

        var msg = START_MESSAGE_CONSTANT + msgg + END_MESSAGE_CONSTANT

        val picWidth = bitmap!!.width
        val picHeight = bitmap.height
        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // convert to mutable bitmap


        var imIn = Mat(picWidth, picHeight, CvType.CV_8UC4)

        // convert bitmap image to MAT
        Utils.bitmapToMat(mbitmap, imIn)

        // Change image to YCrCb color space
        Imgproc.cvtColor(imIn, imIn, Imgproc.COLOR_BGR2YCrCb)

        //get DCT coefficients from image mat
        val blockDcts = imTOdcts(imIn)

        if (!msg.isEmpty()) {
            //get integer seed from user password
            val seed: Int = getSeedFromPass(pass)

            // encrypt message with One Time Pad (random key) encryption
            val otpMsg = otpEncrypt(msg,seed)


            // Count the maximum number of bits that can be encoded (non-0, non-1 coefficients).
            var maxbits = blockDcts!!.rows() - Core.countNonZero(blockDcts)
            val oneVals = Mat()
            Core.compare(blockDcts, Scalar(1.0), oneVals, Core.CMP_EQ)
            maxbits -= Core.countNonZero(oneVals)


            // Encode message in DCT coefficients.
            val elapsed : Duration = measureTime {
                dctLsbEncode(blockDcts, otpMsg, seed, maxbits)
            }

            Log.v(TAG,"time elapsed Encode = $elapsed")
        }

        //get image mat back from DCT coefficients
        val imOut: Mat = dctsTOim(blockDcts, imIn.size())

        // Return image to original number format and color space.
        imOut.convertTo(imOut, imIn.type())
        Imgproc.cvtColor(imOut, imOut, Imgproc.COLOR_YCrCb2BGR)

        //output bitmap
        val rbitmap = Bitmap.createBitmap(imOut.width(), imOut.height(), Bitmap.Config.ARGB_8888)

        // convert output image mat to bitmap
        Utils.matToBitmap(imOut, rbitmap)

        //free memory
        imIn.release()
        imOut.release()
        blockDcts!!.release()
        System.gc()

        return rbitmap
    }

    // Method to save an image to external storage
    private fun saveImage(bitmap: Bitmap): Uri {
        // Get the external storage directory path
        val fileName = "${UUID.randomUUID()}.png" //random filename

        val path = Environment.getExternalStorageDirectory().toString()
        val file = File(path, fileName)

        Log.i(TAG, "save path : ${file.toString()}")

        try {
            // AndroidBmpUtil().save(bitmap, file.toString())
            val fileOutputStream: OutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            Toast.makeText(context, "Image Saved :)", Toast.LENGTH_SHORT).show()
        } catch (e2: IOException) {
            Toast.makeText(context, "Error saving!", Toast.LENGTH_SHORT).show()
            e2.printStackTrace()
        }
        return Uri.parse(file.absolutePath)
    }
}