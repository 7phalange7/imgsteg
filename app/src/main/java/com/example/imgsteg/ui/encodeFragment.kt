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
import com.example.imgsteg.algo.AndroidBmpUtil
import com.example.imgsteg.databinding.FragmentEncodeBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs.*
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
import kotlin.math.abs
import kotlin.math.ceil


const val REQUEST_IMAGE_GET = 1
const val TAG = "UTK"

private var END_MESSAGE_CONSTANT = "#@"
private var START_MESSAGE_CONSTANT = "@#"
const val TEMP_LIMIT = 8

class encodeFragment : Fragment() {

    private lateinit var binding: FragmentEncodeBinding
    private lateinit var bbitmap: Bitmap
    private var inPath = ""

    private var extraWidth: Int = 0
    private var extraHeight: Int = 0

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
                //var bmap = lsbAlgo.encode(bbitmap, msg)

                var bmap = encodeImage(bbitmap, msg, pass)

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

            inPath = ""
            inPath += fullPhotoUri.path

            Log.i(TAG, "Path : $inPath")
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
            val maxSize = "Max msg size : ${maxSize(bbitmap)} KB"
            binding.maxSize.text = maxSize
            Log.i(TAG, "image set")


        }

    }


//    private fun dctencode(bitmap: Bitmap?, mssg: String) {
//        val picWidth = bitmap!!.width
//        val picHeight = bitmap.height
//        var pixels = IntArray(picWidth * picHeight)
//        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // convert to mutable bitmap
//
//        var mat = Mat(picWidth, picHeight, CvType.CV_8UC4)
//
//        Utils.bitmapToMat(mbitmap, mat)
//
//
////        val inFolder = File(
////                Environment.getExternalStorageDirectory(),""
////        )
////        inPath = inFolder.toString() + "/" + inPath.substring(18,inPath.lastIndex+1)
////        Log.i(TAG, "Path in final : $inPath")
////        var mat = imread(inPath)
////
////
////
////        val outFolder = File(
////            Environment.getExternalStoragePublicDirectory(
////                Environment.DIRECTORY_PICTURES
////            ), "TotallyNormalImages"
////        )
////        if (!outFolder.mkdirs()) {
////            Log.e(
////                "encodeWindow/runEncode",
////                "Directory not created (maybe because it exists already)"
////            )
////        }
////
////        val filename = "${UUID.randomUUID()}.jpg"
////        val pathOut = "$outFolder/$filename"
////
////        Log.i(TAG, "Path out : $pathOut")
////        val params = intArrayOf(CV_IMWRITE_PNG_COMPRESSION, 100)
////        imwrite(pathOut, mat, MatOfInt(*params))
//
//        var rbitmap = Bitmap.createBitmap(picWidth, picHeight, Bitmap.Config.ARGB_8888)
//
//        Utils.matToBitmap(mat, rbitmap)
//
//        saveImage(rbitmap)
//
//    }

    fun encodeImage(bitmap: Bitmap?, msgg: String, pass: String): Bitmap {
//        // Read image
//        val imIn: Mat = Highgui.imread(pathIn)
//        if (imIn.channels() < 3) {
//            return
//        }

        var msg = START_MESSAGE_CONSTANT + msgg + END_MESSAGE_CONSTANT

        val picWidth = bitmap!!.width
        val picHeight = bitmap.height
        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // convert to mutable bitmap


        var imIn = Mat(picWidth, picHeight, CvType.CV_8UC4)

        Utils.bitmapToMat(mbitmap, imIn)

        //Imgproc.resize(imIn,imIn,Size(1008.0,1008.0))


        //var rect = Rect(0,0,2000,1064)
//
//        var imIn = Mat()
//
//        Core.copyMakeBorder(imInn,imIn,0,0,0,1000,Core.BORDER_CONSTANT,Scalar(128.0))

        Log.i(TAG, "mat width : ${imIn.cols()} | height : ${imIn.rows()}")


        val blockDcts = im2dcts(imIn)
        if (!msg.isEmpty()) {
            // Get message's binary representation.
            val msgBin = string2bin(msg)
            // Hash password to get int used as seed for PRNG.
            val seed: Int = getSeedFromPass(pass)
            Log.i(TAG, "Encode Seed : $seed")

            // Count the maximum number of bits that can be encoded (non-0,
            // non-1 coefficients).
            var maxbits = blockDcts!!.rows() - Core.countNonZero(blockDcts)
            val oneVals = Mat()
            Core.compare(blockDcts, Scalar(1.0), oneVals, Core.CMP_EQ)
            maxbits -= Core.countNonZero(oneVals)
            println("Max bits: $maxbits")

            // Encode message in DCT coefficients.
            outguessEncode(blockDcts, msgBin.toString(), seed, maxbits)

            // Uncomment to check message extracted from coeffificents.
            // String msgOut = outguessDecode(blockDcts, seed);
            // System.out.println("Decoded: " + msgOut);


        }


        val imOutT: Mat = dcts2im(blockDcts, imIn.size())

        // Return image to original number format and color space.
        imOutT.convertTo(imOutT, imIn.type())
        Imgproc.cvtColor(imOutT, imOutT, Imgproc.COLOR_YCrCb2BGR)


        val blockDctsT = im2dcts(imOutT)
        if (!msg.isEmpty()) {
            // Get message's binary representation.
            val msgBinT = string2bin(msg)
            // Hash password to get int used as seed for PRNG.
            val seed: Int = getSeedFromPass(pass)
            Log.i(TAG, "Encode Seed : $seed")

            // Count the maximum number of bits that can be encoded (non-0,
            // non-1 coefficients).
            var maxbitsT = blockDctsT!!.rows() - Core.countNonZero(blockDctsT)
            val oneValsT = Mat()
            Core.compare(blockDctsT, Scalar(1.0), oneValsT, Core.CMP_EQ)
            maxbitsT -= Core.countNonZero(oneValsT)
            println("Max bits: $maxbitsT")

            // Encode message in DCT coefficients.
            outguessEncode(blockDctsT, msgBinT.toString(), seed, maxbitsT)

            // Uncomment to check message extracted from coeffificents.
            // String msgOut = outguessDecode(blockDcts, seed);
            // System.out.println("Decoded: " + msgOut);


        }


        val imOut: Mat = dcts2im(blockDctsT, imIn.size())


        // Return image to original number format and color space.
        imOut.convertTo(imOut, imIn.type())
        Imgproc.cvtColor(imOut, imOut, Imgproc.COLOR_YCrCb2BGR)

//        // Save Image with lossless image format.
//        val folderName = "imgsteg"
//        val fileName = "ss${UUID.randomUUID()}.png"
//
//        //val path: String = requireActivity().applicationContext.getExternalFilesDirs(null).toString()
//        val path = Environment.getExternalStorageDirectory().toString()
//        val pathOut = File(path, fileName).toString()
//
//
//
//        val params = intArrayOf(CV_IMWRITE_PNG_COMPRESSION,0)
//        imwrite(pathOut, imOut, MatOfInt(*params))
//        Log.i(TAG,"saved imout image")


//        var rect = Rect(0,0,1000,1000)
//        var Crop  = Mat(imOut,rect)
//        var imOutCrop = Crop.clone()
//        var imOutCrop = imOut.submat(0, 1000, 0, 1000)
        // var imOutCrop = imOut

        //Core.copyMakeBorder(imOut,imOutCrop,0,0,0,-1000,Core.BORDER_CONSTANT,Scalar(0.0))

        Log.i(TAG, "mat out width : ${imOut.cols()} | height : ${imOut.rows()}")




        var rbitmap = Bitmap.createBitmap(imOut.width(), imOut.height(), Bitmap.Config.ARGB_8888)
//
//        Log.i(TAG,"imIn : ${imIn.width()} x ${imIn.height()}")
//        Log.i(TAG,"imOut : ${imOut.width()} x ${imOut.height()}")

        Utils.matToBitmap(imOut, rbitmap)

//        var decFragment = decodeFragment()
//        var decodeTest : String =""+ decFragment.decodeImage(rbitmap,pass)
//
//        Log.i(TAG,"decoded text : $decodeTest")


        return rbitmap

        /*
		// Code adapted from opencv forum to output image to file with java.
		// Done to test whether writing to file was the cause for some of
		// wrongly encoded messages. Does not seem to be the cause which is
		// likely type conversions (signed 8-bit ints to 32 bit floats needed
		// to use OpenCV's dct/idct).
		byte[] b = new byte[imOut.channels() * imOut.rows() * imOut.cols()];
		imOut.get(0,0,b);
		BufferedImage image = new BufferedImage(imOut.cols(), imOut.rows(), BufferedImage.TYPE_3BYTE_BGR);
		final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		System.arraycopy(b, 0, targetPixels, 0, b.length);
		File of = new File("/home/dom/workspace/outguess/trainValid/K.bmp");
		try {
			ImageIO.write(image, "bmp", of);
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
		*/
        // Clean memory.
        imIn.release()
        imOut.release()
        blockDcts!!.release()
        System.gc()
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
        lumQuant = MatOfInt(*lumVals) //h :64 w 1
        Log.i(TAG, "lumQuant default type : ${lumQuant.type()}")
        // lumQuant.reshape(0, 8)
        // Log.i(TAG,"lumQuant reshaped dimen h :${lumQuant.height()} w : ${lumQuant.width()}")
        lumQuant.convertTo(lumQuant, CvType.CV_32FC1)
        Log.i(TAG, "lumQuant AFTER type : ${lumQuant.type()}")

        val chromVals = intArrayOf(17, 18, 24, 47, 99, 99, 99, 99,
                18, 21, 26, 66, 99, 99, 99, 99,
                24, 26, 56, 99, 99, 99, 99, 99,
                47, 66, 99, 99, 99, 99, 99, 99,
                99, 99, 99, 99, 99, 99, 99, 99,
                99, 99, 99, 99, 99, 99, 99, 99,
                99, 99, 99, 99, 99, 99, 99, 99,
                99, 99, 99, 99, 99, 99, 99, 99)
        chromeQuant = MatOfInt(*chromVals)
        // chromeQuant = chromeQuant.reshape(0, 8)
        chromeQuant.convertTo(chromeQuant, CvType.CV_32FC1)
        quantMatsInitialized = true
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
        val byteHash = md.digest()
        val buff: ByteBuffer = ByteBuffer.wrap(byteHash)
        return buff.getInt()
    }

    private fun im2dcts(im: Mat): Mat {
        // Load quantization matrices.
        if (!quantMatsInitialized) {
            initQuantMats()
        }
        val m = ceil(im.rows() / 8.0).toInt()
        val n = ceil(im.cols() / 8.0).toInt()
        val numBlocks = m * n
        // 3 channels, 64 pixels per block.
        val blockDctsAll = Mat(numBlocks * 3 * ppb, 1, CvType.CV_32FC1)


        extraWidth = n * 8 - im.cols()
        extraHeight = m * 8 - im.rows()

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

            //make it 8a X 8b
            Core.copyMakeBorder(ccOriginal, cc, 0, extraHeight, 0, extraWidth, Core.BORDER_CONSTANT, Scalar(128.0))

            Log.i(TAG, "new width : ${cc.width()} | new height : ${cc.height()}")

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

    private fun dcts2im(dctsIn: Mat, sz: Size): Mat {
        // Get quantization matrices
        if (!quantMatsInitialized) {
            initQuantMats()
        }
        val m = (sz.height.toInt() + extraHeight) / 8
        val n = (sz.width.toInt() + extraWidth) / 8
        val numBlocks = n * m
        val ccs: MutableList<Mat> = ArrayList()

        // Convert to floats for idct
        dctsIn.convertTo(dctsIn, CvType.CV_32FC1)

        // For every color channel
        for (ccNo in 0..2) {
            var quantMat: Mat
            quantMat = if (ccNo == 0) {
                lumQuant
            } else {
                chromeQuant
            }
            val cc = Mat.zeros(m * 8, n * 8, CvType.CV_32FC1)

            // 64 for the number of pixels per block.
            val rowStart = ccNo * numBlocks * ppb
            val rowEnd = rowStart + numBlocks * ppb
            val dcts = dctsIn.submat(rowStart, rowEnd, 0, 1)
            val numBlocks2 = dcts.rows() / ppb

            // Reassemble blocks into full image.
            for (blockNo in 0 until numBlocks2) {
                val iMin = blockNo * ppb
                val iMax = iMin + ppb
                var blockCoeffs = dcts.submat(iMin, iMax, 0, 1)
                blockCoeffs = blockCoeffs.reshape(0, 8)
                // Undo quantization
                quantMat = quantMat!!.reshape(0, 8)
                Core.multiply(blockCoeffs, quantMat, blockCoeffs)
                val block = Mat()
                Core.idct(blockCoeffs, block)
                // Place block in final image.
                val xMin = blockNo / n * 8
                val xMax = xMin + 8
                val yMin = blockNo % n * 8
                val yMax = yMin + 8
                block.copyTo(cc.submat(xMin, xMax, yMin, yMax))
            }
            Core.add(cc, Scalar(128.0), cc) // Return intensities to 0-255.


            //crop to original dimensions
            val rect = Rect(0, 0, cc.cols() - extraWidth, cc.rows() - extraHeight)
            val Crop = Mat(cc, rect)


            Log.i(TAG, "dct2im cc ${cc.height()} x ${cc.width()}")
            val ccOriginal = Crop.clone()
            Log.i(TAG, "ccOriginal ${ccOriginal.height()} x ${ccOriginal.width()}")
            ccs.add(ccOriginal)
        }
        // Merge color channels
        val im = Mat()
        Core.merge(ccs, im)

        // Memory clean up.
        dctsIn.release()
        ccs.clear()
        System.gc()
        return im
    }


    private fun outguessEncode(dcts: Mat, msg: String, pass: Int, maxBits: Int) {
        // Keep track of coefficients written to so we do not overwrite them.
        Log.i(TAG, " message $msg \n rows : ${dcts.rows()}")
        val visited = TreeSet<Int>()
        // Set PRNG's seed
        val rand = Random()
        //set seed as pass
        rand.setSeed(pass.toLong())
        var bitsWrit = 0
        val bitsTotal = msg.length
        //var ind = 1
        var bytesFound = ArrayList<Int>()
        var bytesNew = ArrayList<Int>()


        var it = 0

        while (bitsWrit < bitsTotal && bitsWrit < maxBits / TEMP_LIMIT) {
            // Get next pseudo-random coefficient index.
            var ind = rand.nextInt(dcts.rows() / TEMP_LIMIT)
            ind  += (dcts.rows()*(TEMP_LIMIT-1))/ TEMP_LIMIT
            // Make sure we do not overwrite a message bit.
            if (visited.contains(ind)) {
                continue
            } else {
                visited.add(ind)

            }


            val vall = byteArrayOf(0)
            dcts[ind, 0, vall]
            // Only modify coefficients if they are neither 1 nor 0.
            if (vall[0] != 0.toByte() && vall[0] != 1.toByte()) {
                // Get next message bit to write.
                //bytesFound += (", " + vall[0].toString())
                val bit = msg.substring(bitsWrit, bitsWrit + 1)

                // Change the LSB to the next message bit.
                var mask = 1 // Mask of 1's with 0 in LSB.
                mask = mask.inv()
                val newVal: Int = vall[0].toInt() and mask or Integer.decode(bit)
                // Stored encoded coefficient.
//                if((bitsWrit in 191..216) || abs(newVal-vall[0]) >1)
//                {
//                    bytesFound += " |$bitsWrit   ${vall[0]}   $newVal| "
//                }
                bytesFound.add(vall[0].toInt())
                bytesNew.add(newVal)
                dcts.put(ind, 0, newVal.toDouble())
                bitsWrit++

            }
            //ind++
        }



        Log.i(TAG, "bytes Encountered encode : $bytesFound")
        Log.i(TAG, "bytes modified(bitswrit : $bitsWrit) (bytesNewSize : ${bytesNew.size} : $bytesNew")

    }

    private fun string2bin(s: String): String? {
        val msgBytes: ByteArray = s.toByteArray()
        var msgBin: String? = ""
        for (b in msgBytes) {
            var nextChar = Integer.toBinaryString(b.toInt())
            // Zero pad the character representation so it is 8 bits.
            while (nextChar.length < 8) {
                nextChar = "0$nextChar"
            }
            msgBin += nextChar
        }
        return msgBin
    }


    private fun encode(bitmap: Bitmap?, mssg: String) {

        val pic_width = bitmap!!.width
        val pic_height = bitmap.height

        Log.d(TAG, pic_width.toString())
        Log.d(TAG, pic_height.toString())

        var pixels = IntArray(pic_width * pic_height)

        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        mbitmap.getPixels(pixels, 0, pic_width, 0, 0, pic_width, pic_height)

        var msg = mssg


        msg = START_MESSAGE_CONSTANT + msg
        msg += END_MESSAGE_CONSTANT

        var b_msg = msg.toByteArray()

        if (pixels.size < b_msg.size) {
            Toast.makeText(context, "Image size too small", Toast.LENGTH_SHORT).show()
            return
        }

        pixels = lsbEncode(pixels, b_msg)


//        for (i in 0..5) {
//            val p = pixels[i]
//
//            var R = p shr 16 and 0xff
//            var G = p shr 8 and 0xff
//            var B = p and 0xff
//
//            var sR = Integer.toBinaryString(R).padStart(8, '0')
//
//            var sG = Integer.toBinaryString(G).padStart(8, '0')
//            var sB = Integer.toBinaryString(B).padStart(8, '0')
//
//            //sB = sB.substring(0,6) + b_msg[index++]
//
//            R =0
//
//            R = Integer.parseInt(sR, 2)
//
//            Toast.makeText(context, "^ $R", Toast.LENGTH_LONG).show()
//
//
//            pixels[i] = -0x1000000 or (R shl 16) or (G shl 8) or B
//
//
//        }

        mbitmap.setPixels(pixels, 0, pic_width, 0, 0, pic_width, pic_height)

        saveImage(mbitmap)

    }

    @Throws(IOException::class)
    private fun readBytes(context: Context, uri: Uri): ByteArray? =
            context.contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }

    // Method to save an image to external storage
    private fun saveImage(bitmap: Bitmap): Uri {
        // Get the external storage directory path
        val folderName = "imgsteg"
        val fileName = "aa${UUID.randomUUID()}.jpg"

        //val path: String = requireActivity().applicationContext.getExternalFilesDirs(null).toString()
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


    private fun lsbEncode(pixels: IntArray, msgBytes: ByteArray): IntArray {

        var msgIndex = 0
        var shiftIndex = 0
        val toShiftMsg = intArrayOf(7, 6, 5, 4, 3, 2, 1, 0)
        val toShiftPixel = intArrayOf(16, 8, 0)
        var msgEnded = false



        for (index in pixels.indices) {
            val p = pixels[index]

            Log.i(TAG, " pixel before: ${pixels[index]}")
            val rgb = IntArray(3)

            for (j in 0..2) {
                if (!msgEnded) {
                    rgb[j] = (p shr toShiftPixel[j]) and 0xff  // get R/G/B acc to j

                    Log.i(TAG, " Rgb before : ${rgb[j]}")

                    // binary string of rgb component
                    var brgb = Integer.toBinaryString(rgb[j])

                    //bit from message
                    var msgdata = ((msgBytes[msgIndex].toInt() shr toShiftMsg[(shiftIndex++) % toShiftMsg.size]) and 1).toString()
                    Log.i(
                            TAG, " msgData bit : ${msgdata} | rbg[j] : ${
                        brgb.substring(
                                0,
                                brgb.length - 1
                        ) + msgdata
                    }"
                    )

                    rgb[j] = (brgb.substring(0, brgb.length - 1) + msgdata).toInt(2)

                    Log.i(TAG, " Rgb after: ${rgb[j]}")

                    if (shiftIndex % toShiftMsg.size == 0) {
                        msgIndex++
                    }
                    if (msgIndex == msgBytes.size) {
                        msgEnded = true
                    }
                }


            }


            pixels[index] = -0x1000000 or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]  // FF000000 is hex signed 2's compliment of -0x1000000
            Log.i(TAG, " pixel after: ${pixels[index]}")

            if (msgEnded) break

//            var R = (p shr 16) and 0xff
//            var G = (p shr 8) and 0xff
//            var B = p and 0xff
//
//            var bR: String = Integer.toBinaryString(R)
//            var bG = Integer.toBinaryString(G)
//            var bB = Integer.toBinaryString(B)
//
//            Log.i(TAG, " pixel : ${pixels[index]} | MsgByte : ${Integer.toBinaryString(msgBytes[index].toInt())}")
//            Log.i(TAG, " R : ${R} ,  ${bR.substring(0, 7)}")
//            Log.i(TAG, " G: ${G} ,  ${bG}")
//            Log.i(TAG, " B : ${B} ,  ${bB}")


        }


//        var msgEnded = false
//        var msgIndex = 0
//        var shiftIndex = 0
//        val toShift = intArrayOf(7, 6, 5, 4, 3, 2, 1, 0)
//
//        for (index in pixels.indices) {
//            var msgdata = 0
//            if (index == 0) {
//                Log.d("Pixel $index", pixels[index].toString() + "")
//            }
//            var dataToOR = 0
//            for (j in 0..2) {
//                if (!msgEnded) {
//                    msgdata = (msgBytes[msgIndex].toInt() shr toShift[(shiftIndex++) % toShift.size]) and 0x01
//                    dataToOR = (dataToOR.toString() + 0.toString() + msgdata.toString()).toInt()
//                    if (shiftIndex % toShift.size == 0) {
//                        msgIndex++
//                    }
//                    if (msgIndex == msgBytes.size) {
//                        msgEnded = true
//                    }
//                }
//            }
//            val hexDataToOR = "0x$dataToOR"
//            val hexToInt = hexDataToOR.substring(2).toInt(16)
//            if (index == 0) {
//                Log.d("dataToOR", hexDataToOR + "")
//            }
//            pixels[index] = (pixels[index] and 0xfffcfcfc.toInt()) or hexToInt
//            if (index == 0) {
//                Log.d("Pixel $index", pixels[index].toString() + "")
//            }
//        }

        return pixels
    }

//    private fun decode(bitmap: Bitmap): String {
//        val pic_width = bitmap!!.width
//        val pic_height = bitmap.height
//
//        Log.d(TAG, pic_width.toString())
//        Log.d(TAG, pic_height.toString())
//
//        var pixels = IntArray(pic_width * pic_height)
//
//        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
//
//        mbitmap.getPixels(pixels, 0, pic_width, 0, 0, pic_width, pic_height)
//
//        Log.i(TAG, "pixel array formed ${pixels.size}")
//
//        var strByteArray = ""
//
//        for (i in pixels.indices) {
////            if (i < 5 || i > pixels.size - 5) {
////                Log.i(TAG, "pixel no. ${1 + i}")
////            }
//            val p = pixels[i]
//            var R = (p shr 16 and 0xff) and 1
//            var G = (p shr 8 and 0xff) and 1
//            var B = (p and 0xff) and 1
//
//            if (i % 5000 == 0) {
//                Log.i(TAG, "pixel no.${1 + i} $R$G$B")
//
//            }
//            strByteArray += "$R$G$B"
//            //pixels[i] = -0x1000000 or (R shl 16) or (G shl 8) or B
//
//
//        }
//
//        Log.i(TAG, "traversed pixels")
//
////        var byteArray: ByteArray = strByteArray.toByteArray()
////        var msg: String = byteArray.toString(Charset.defaultCharset())
////
////        Log.i(TAG, "got full lsb msg")
////
////        var msgFound = false
////        for (i in 0 until msg.length) {
////            if (msg.substring(i, i + 5) == "#t3g0") {
////                msg = msg.substring(0, i - 1)
////                msgFound = true
////                break
////            }
////        }
////
////        if (!msgFound) {
////            msg = "No Msg Found"
////        }
////
////        Log.i(TAG, "got msg")
//
//        mbitmap.setPixels(pixels, 0, pic_width, 0, 0, pic_width, pic_height)
//
//        binding.coverImage.setImageBitmap(mbitmap)
//
//        return strByteArray.substring(0,20)
//
//    }

    private fun decode(bitmap: Bitmap): String {


        val picWidth = bitmap!!.width
        val picHeight = bitmap.height
        var pixels = IntArray(picWidth * picHeight)
        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        mbitmap.getPixels(pixels, 0, picWidth, 0, 0, picWidth, picHeight)


        //check if image has the hidden msg by checking START_MESSAGE_CONSTANT
        val startMsgIndexes = ceil(8.0 / 3 * START_MESSAGE_CONSTANT.length).toInt()
        var count = 0
        var decodedString = StringBuilder()
        var rgb = IntArray(3)
        val toShiftPixel = intArrayOf(16, 8, 0)
        var byteChar = StringBuilder()
        var hiddenMessagePresent = false

        for (index in 0..startMsgIndexes) {
            val p = pixels[index]

            for (j in 0..2) {
                rgb[j] = (p shr toShiftPixel[j]) and 0xff  // get R/G/B acc to j

                Log.i(TAG, " Rgb before : ${rgb[j]}")

                // binary string of rgb component
                var brgb = Integer.toBinaryString(rgb[j])

                byteChar.append(brgb[7])

                Log.i(TAG, " byte : ${byteChar}")
                count++

                if (count == 8) {
                    count = 0

                    decodedString.append(byteChar.toString().toInt(2).toChar())
                    if (decodedString.length == 2) {
                        if (decodedString.toString() == START_MESSAGE_CONSTANT) {
                            Log.i(TAG, "Image Contains Hidden Message")
                            hiddenMessagePresent = true
                            break
                        }
                    }

                    byteChar.setLength(0)
                }

            }

            if (hiddenMessagePresent) break

        }

        if (hiddenMessagePresent) {
            decodedString.setLength(0)
            count = 0
            byteChar.setLength(0)

            var msgEnded = false

            for (index in pixels.indices) {
                val p = pixels[index]

//                if(index % 50000 ==0)
//                    Log.i(TAG, "Message( ${decodedString.lastIndex} )  pixel no. $index")


                for (j in 0..2) {
                    rgb[j] = (p shr toShiftPixel[j]) and 0xff  // get R/G/B acc to j

//                    if(index % 50000 ==0)
//                    Log.i(TAG, " Rgb before : ${rgb[j]}")

                    // binary string of rgb component
                    var brgb = Integer.toBinaryString(rgb[j])

                    byteChar.append(brgb[brgb.lastIndex])

//                    if(index % 50000 ==0)
//                    Log.i(TAG, " byte : $byteChar")
                    count++

                    if (count == 8) {
                        count = 0

                        decodedString.append(byteChar.toString().toInt(2).toChar())
                        if (decodedString.length >= 4) {
                            if (decodedString.toString().takeLast(END_MESSAGE_CONSTANT.length) == END_MESSAGE_CONSTANT) {
                                Log.i(
                                        TAG,
                                        "Message( ${decodedString.toString()} )  Ended at pixel no. $index"
                                )
                                msgEnded = true
                                break
                            }
                        }

                        byteChar.setLength(0)
                    }

                }

                if (msgEnded) break

            }

        }

        return if (hiddenMessagePresent) decodedString.toString().substring(
                START_MESSAGE_CONSTANT.length,
                decodedString.lastIndex - END_MESSAGE_CONSTANT.length + 1
        )
        //return if(hiddenMessagePresent) "${decodedString.lastIndex} pixels : ${pixels.size}"
        else "NO HIDDEN MESSAGE"


        //        var selectedBitmap = mBitmap.copy(Bitmap.Config.ARGB_8888, true)
//        val dataStringDecoded = StringBuilder()
//        val decodedText = StringBuilder()
//        var shiftIndex = 3
//        val toShiftDecode = intArrayOf(16, 8, 0)
//        var count = 0
//        var flagBreak = false
//        var msgdataString: Byte
//        var dataForString: Int
//        var checkStart = false
//        val pic_width = selectedBitmap.width
//        val pic_height = selectedBitmap.height
//        val pixels = IntArray(pic_width * pic_height)
//        selectedBitmap.getPixels(pixels, 0, pic_width, 0, 0, pic_width, pic_height)
//        for (h in 0..6) {
//            dataForString = pixels[h] and 0x00030303
//            for (j in 0..2) {
//                msgdataString = (dataForString shr toShiftDecode[(shiftIndex++) % toShiftDecode.size] and 0x01).toByte()
//                val app: Int = (msgdataString.toInt()) and 1
//                if (app == 1) {
//                    dataStringDecoded.append(1)
//                    count++
//                } else {
//                    dataStringDecoded.append(0)
//                    count++
//                }
//                if (count == 8) {
//                    Log.d("BinaryString:", dataStringDecoded.toString());
//                    count = 0
//                    decodedText.append(dataStringDecoded.toString().toInt(2).toChar())
//                    dataStringDecoded.setLength(0)
//                    Log.d("finalString:", decodedText.toString())
//                    if (decodedText.length >= 2) {
//                        Log.d("BREAK", decodedText[decodedText.length - 2] + "" + decodedText[decodedText.length - 1])
//                        if (decodedText[decodedText.length - 2].toString() + "" + decodedText[decodedText.length - 1] == START_MESSAGE_CONSTANT) {
//                            checkStart = true
//                        }
//                    }
//                }
//            }
//        }
//        return if (checkStart == true) {
//            decodedText.setLength(0)
//            shiftIndex = 0
//            count = 0
//            dataStringDecoded.setLength(0)
//            for (index in pixels.indices) {
//                dataForString = pixels[index] and 0x00030303
//                for (j in 0..2) {
//                    msgdataString = (dataForString shr toShiftDecode[(shiftIndex++) % toShiftDecode.size] and 0x01).toByte()
//                    val app: Int = (msgdataString.toInt()) and 1
//                    if (app == 1) {
//                        dataStringDecoded.append(1)
//                        count++
//                    } else {
//                        dataStringDecoded.append(0)
//                        count++
//                    }
//                    if (count == 8) {
//                        Log.d("BinaryString:", dataStringDecoded.toString())
//                        count = 0
//                        decodedText.append(dataStringDecoded.toString().toInt(2).toChar())
//                        dataStringDecoded.setLength(0)
//                        // Log.d("finalString:", decodedText.toString())
//                        if (decodedText.length > 2) {
//                            Log.d("BREAK", decodedText[decodedText.length - 2] + "" + decodedText[decodedText.length - 1])
//                            if (decodedText[decodedText.length - 2] + "" + decodedText[decodedText.length - 1] == END_MESSAGE_CONSTANT) {
//                                Log.d("FlAG before", flagBreak.toString() + "")
//                                flagBreak = true
//                            }
//                        }
//                    }
//                    if (index == 0) {
//                        Log.d("BinaryString:", dataStringDecoded.toString())
//                        Log.d("Forj=$j", msgdataString.toString() + "")
//                    }
//                }
//                if (index == 0) {
//                    Log.d("dataForString:", dataForString.toString() + "")
//                }
//                if (flagBreak == true) {
//                    Log.d("I am in $index", flagBreak.toString() + "")
//                    break
//
//                }
//            }
//            decodedText.deleteCharAt(1)
//            decodedText.deleteCharAt(0)
//            decodedText.deleteCharAt(decodedText.length - 1)
//            decodedText.deleteCharAt(decodedText.length - 1)
//            Log.d("FINAL VALA STRING :)", decodedText.toString())
//            decodedText.toString()
//        } else {
//            "There is nothing to show!"
//        }
    }

    fun maxSize(bitmap: Bitmap?):Int {
//        // Read image
//        val imIn: Mat = Highgui.imread(pathIn)
//        if (imIn.channels() < 3) {
//            return
//        }



        val picWidth = bitmap!!.width
        val picHeight = bitmap.height
        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // convert to mutable bitmap


        var imIn = Mat(picWidth, picHeight, CvType.CV_8UC4)

        Utils.bitmapToMat(mbitmap, imIn)

        //Imgproc.resize(imIn,imIn,Size(1008.0,1008.0))


        //var rect = Rect(0,0,2000,1064)
//
//        var imIn = Mat()
//
//        Core.copyMakeBorder(imInn,imIn,0,0,0,1000,Core.BORDER_CONSTANT,Scalar(128.0))

       // Log.i(TAG, "mat width : ${imIn.cols()} | height : ${imIn.rows()}")


        val blockDcts = im2dcts(imIn)


            // Count the maximum number of bits that can be encoded (non-0,
            // non-1 coefficients).
            var maxbits = blockDcts!!.rows() - Core.countNonZero(blockDcts)
            val oneVals = Mat()
            Core.compare(blockDcts, Scalar(1.0), oneVals, Core.CMP_EQ)
            maxbits -= Core.countNonZero(oneVals)


        return maxbits/(8*1024)
    }


}