package com.example.imgsteg.ui

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
import kotlin.math.ceil


class decodeFragment : Fragment() {

    private lateinit var binding : FragmentDecodeBinding
    private lateinit var bbitmap: Bitmap

    private var extraWidth : Int = 0
    private var extraHeight : Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentDecodeBinding.inflate(inflater)

        binding.chooseImgBtn.setOnClickListener {

            selectImage()
        }

        binding.revealBtn.setOnClickListener {
            Log.i(TAG, "reveal clicked")

            Log.i(TAG, "inpute should be gone")

            CoroutineScope(Dispatchers.Default).launch {
//                val hiddenMsg = lsbAlgo.decode(bbitmap)

                val pass = ""+ binding.passEditTextD.text.toString()
                val hiddenMsg = decodeImage(bbitmap,pass)
                withContext(Dispatchers.Main) {
                    binding.hiddenMsg.text = hiddenMsg
                }
            }
            Log.i(TAG, "messsage revealed")

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
            Log.i(TAG, "image set")


        }

    }

    public fun decodeImage(bitmap: Bitmap?,pass: String): String? {

        val picWidth = bitmap!!.width
        val picHeight = bitmap.height
        Log.i(TAG," image width : $picWidth | hieght : $picHeight")

        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // convert to mutable bitmap

        var imIn = Mat(picWidth, picHeight, CvType.CV_8UC4)

//        var imIn = Mat()
//
//        Core.copyMakeBorder(imInn,imIn,0,0,0,1000,Core.BORDER_CONSTANT,Scalar(128.0))

        Log.i(TAG," image in mat height : ${imIn.rows()} | width : ${imIn.cols()}")

        Utils.bitmapToMat(mbitmap, imIn)
        if (imIn.channels() < 3) {
            System.err.println("Image must have 3 channels.")
            return ""
        }
        val blockDcts: Mat = im2dcts(imIn)
        val seed = getSeedFromPass(pass)
        Log.i(TAG, "Decode Seed : $seed")
        val msgOut: String? = outguessDecode(blockDcts, seed)
        blockDcts.release()
        System.gc()
        return msgOut
    }

    private fun getSeedFromPass(pass: String): Int {
        val md: MessageDigest
        md = try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            System.err.println(e)
            return -1
        }
        md.update(pass.toByteArray())
        val buff: ByteBuffer = ByteBuffer.wrap(md.digest())
        return buff.getInt()
    }

    private val ppb = 64 // Pixels per block.


    private lateinit var lumQuant: Mat
    private lateinit var chromeQuant: Mat

    private var quantMatsInitialized = false

    /**
     * Quantization matrices used in @see im2dcts and @see dcts2im
     * are initialized here. They get initialized at first use only.
     * @see quantMatsInitialized is used to indicate this.
     * Values drawn from JPG standard ITU CCIT T.81.
     */
    private fun initQuantMats() {
        val lumVals = intArrayOf(16, 11, 10, 16, 24, 40, 51, 61,
                12, 12, 14, 19, 26, 58, 60, 55,
                14, 13, 16, 24, 40, 57, 69, 56,
                14, 17, 22, 29, 51, 87, 80, 62,
                18, 22, 37, 56, 68, 109, 103, 77,
                24, 35, 55, 64, 81, 104, 113, 92,
                49, 64, 78, 87, 103, 121, 120, 101,
                72, 92, 95, 98, 112, 100, 103, 99)
        lumQuant = MatOfInt(*lumVals)
        lumQuant.reshape(0, 8)
        lumQuant.convertTo(lumQuant, CvType.CV_32FC1)
        val chromVals = intArrayOf(17, 18, 24, 47, 99, 99, 99, 99,
                18, 21, 26, 66, 99, 99, 99, 99,
                24, 26, 56, 99, 99, 99, 99, 99,
                47, 66, 99, 99, 99, 99, 99, 99,
                99, 99, 99, 99, 99, 99, 99, 99,
                99, 99, 99, 99, 99, 99, 99, 99,
                99, 99, 99, 99, 99, 99, 99, 99,
                99, 99, 99, 99, 99, 99, 99, 99)
        chromeQuant = MatOfInt(*chromVals)
        chromeQuant = chromeQuant.reshape(0, 8)
        chromeQuant.convertTo(chromeQuant, CvType.CV_32FC1)
        quantMatsInitialized = true
    }


    private fun im2dcts(im: Mat): Mat {
        // Load quantization matrices.
        if (!quantMatsInitialized) {
            initQuantMats()
        }
        val m = ceil(im.rows() / 8.0).toInt()
        val n = ceil(im.cols()  / 8.0).toInt()
        val numBlocks = m * n
        // 3 channels, 64 pixels per block.
        val blockDctsAll = Mat(numBlocks * 3 * ppb, 1, CvType.CV_32FC1)


        extraWidth = n *8 - im.cols()
        extraHeight = m *8 - im.rows()

        // Change image to YCrCb color space
        Imgproc.cvtColor(im, im, Imgproc.COLOR_BGR2YCrCb)

        // Split color channels
        val ccs: List<Mat> = ArrayList()
        Core.split(im, ccs)
        // For each color channel.
        for (ccNo in 0..2) {
            var quantMat: Mat?
            if (ccNo == 0) {
                quantMat = lumQuant
            } else {
                quantMat = chromeQuant
            }
            val ccOriginal = ccs[ccNo]
            var cc = Mat()

            Core.copyMakeBorder(ccOriginal, cc, 0, extraHeight, 0, extraWidth, Core.BORDER_CONSTANT, Scalar(128.0))

            cc.convertTo(cc, CvType.CV_32SC1)
            // Center around 0.
            Core.add(cc, Scalar(-128.0), cc)
            cc.convertTo(cc, CvType.CV_32FC1)
            val blocks = ArrayList<Mat>()

            // Iterate over complete blocks
            for (rowInd in 0 until m) {
                for (colInd in 0 until n) {
                    val iMin = rowInd * 8
                    val iMax = iMin + 8
                    val jMin = colInd * 8
                    val jMax = jMin + 8
                    blocks.add(cc.submat(iMin, iMax, jMin, jMax))
                }
            }
            // TODO Handle incomplete blocks
            // 64 pixels per block.
            val rowStart: Int = ccNo * numBlocks * ppb
            val rowEnd: Int = rowStart + numBlocks * ppb
            val blockDcts = blockDctsAll.submat(rowStart, rowEnd, 0, 1)

            // Get the DCT coefficiens for every block, and quantize them
            for (blockNo in 0 until numBlocks) {
                val iMin: Int = blockNo * ppb
                val iMax: Int = iMin + ppb
                var blockDct = Mat()
                Core.dct(blocks[blockNo], blockDct)
                val blockDctFlat = blockDcts.submat(iMin, iMax, 0, 1)
//                Log.i(TAG,"blockdct dimenstion ${blockDct.height()} ${blockDct.width()}")
//                Log.i(TAG,"quantMat dimenstion ${quantMat!!.height()} ${quantMat.width()}")
                quantMat = quantMat!!.reshape(0, 8)
//                Log.i(TAG,"quantMat reshape dimenstion ${quantMat!!.height()} ${quantMat.width()}")
                Core.divide(blockDct, quantMat, blockDct)
                // Vectorize DCT coefficients and concatenate.
                blockDct = blockDct.reshape(0, ppb)
                blockDct.copyTo(blockDctFlat)
            }
            cc.release()
            ccs[ccNo].release()
        }

        // Convert to 8-bit signed ints for manipulating the LSB.
        blockDctsAll.convertTo(blockDctsAll, CvType.CV_8SC1)

        // Suggest to SVM to clean up memory since Mats are large, and JVM is
        // not aware of their actual size.
        System.gc()
        return blockDctsAll
    }

    private fun outguessDecode(dcts: Mat, pass: Int): String? {
        // Keep track of coefficients written to so we do not overwrite them.
        val visited: TreeSet<Int> = TreeSet<Int>()
        // Set PRNG's seed
        val rand2 = Random()
        rand2.setSeed(pass.toLong())

        var noMsg = false

        // Count the maximum number of bits that can be encoded (non-0,
        // non-1 coefficients).
        var maxbits = dcts.rows() - Core.countNonZero(dcts)
        val oneVals = Mat()
        Core.compare(dcts, Scalar(1.0), oneVals, Core.CMP_EQ)
        maxbits -= Core.countNonZero(oneVals)

        val msgBytesIn = ByteArray(maxbits)
        var bytesRead = 0
        var lastByte = 1
        var bitNo = 0
        var curByte = 0
        var bytesFound = ""
        //var ind = 1

        while (lastByte != 0 && bytesRead < msgBytesIn.size) {
            // Get next pseudo-random coefficient index.
            var ind: Int = rand2.nextInt(dcts.rows()/ TEMP_LIMIT)
            ind  += (dcts.rows()*(TEMP_LIMIT-1))/ TEMP_LIMIT
            // Make sure we do not read a message bit more than once.
            if (visited.contains(ind)) {
                continue
            } else {
                visited.add(ind)
            }
            val vall = byteArrayOf(0)
            dcts[ind, 0, vall]
            // Only read LSB if coefficient is neither 1 nor 0.
            if (vall[0] != 0.toByte() && vall[0] != 1.toByte()) {
                // Bit manipulation to extract LSB.
                //bytesFound += (", " + vall[0].toString())

                val mask = 1
                curByte = curByte or (vall[0].toInt() and mask shl (7 - bitNo))
                bitNo++
                if (bitNo == 8) { // If full byte read, add it to message.
                    msgBytesIn[bytesRead] = curByte.toByte()
                    bytesRead++
                    lastByte = curByte
                    curByte = 0
                    bitNo = 0

                    if(bytesRead >=2)
                    {
                        val k = String(msgBytesIn, StandardCharsets.US_ASCII)
                        //Log.i(TAG, " str : ${k.substring(0, bytesRead)}")
                        if(k.substring(0,2) != START_MESSAGE_CONSTANT)
                        {
                            Log.i(TAG,"Not fount @#")
                            noMsg = true
                            break
                        }
                        if(k.substring(bytesRead- END_MESSAGE_CONSTANT.length,bytesRead) == END_MESSAGE_CONSTANT)
                        {
                            Log.i(TAG,"fount #@")
                            lastByte=0

                        }
                    }
                }
            }
            //ind++
        }
        Log.i(TAG,"bytes Encountered decode : $bytesFound")

        if(noMsg) return "No message detected"
        val s = String(msgBytesIn, StandardCharsets.US_ASCII)
        return s.substring(2, bytesRead - END_MESSAGE_CONSTANT.length) // Do not return full string buffer.
    }

}