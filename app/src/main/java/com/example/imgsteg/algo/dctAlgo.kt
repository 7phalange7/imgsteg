package com.example.imgsteg.algo

import android.util.Log
import com.example.imgsteg.ui.TAG
import org.opencv.core.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.ceil
import kotlin.math.pow

const val END_MESSAGE_CONSTANT = "#@"
const val START_MESSAGE_CONSTANT = "@#"

object dctAlgo {

    private const val NONE = 300

    // to compensate for dimensions that are not multiple of 8
    private var extraWidth: Int = 0
    private var extraHeight: Int = 0

    private const val ppb = 64 // Pixels per block.
    private lateinit var lumQuant: Mat
    private lateinit var chromeQuant: Mat

    private var quantMatsInitialized = false

    // function to initialize Quantization Matrices
    private fun initQuantMats() {

        val lumVals = intArrayOf(
            16, 11, 10, 16, 24, 40, 51, 61,
            12, 12, 14, 19, 26, 58, 60, 55,
            14, 13, 16, 24, 40, 57, 69, 56,
            14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77,
            24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101,
            72, 92, 95, 98, 112, 100, 103, 99
        )

        lumQuant = MatOfInt(*lumVals)
        lumQuant.convertTo(lumQuant, CvType.CV_32FC1)

        val chromVals = intArrayOf(
            17, 18, 24, 47, 99, 99, 99, 99,
            18, 21, 26, 66, 99, 99, 99, 99,
            24, 26, 56, 99, 99, 99, 99, 99,
            47, 66, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99
        )

        chromeQuant = MatOfInt(*chromVals)
        chromeQuant.convertTo(chromeQuant, CvType.CV_32FC1)

        quantMatsInitialized = true
    }


    // function to convert image MAT to DCTS
    fun imTOdcts(im: Mat): Mat {

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
            val cc = Mat()

            //make it 8a X 8b
            Core.copyMakeBorder(
                ccOriginal,
                cc,
                0,
                extraHeight,
                0,
                extraWidth,
                Core.BORDER_CONSTANT,
                Scalar(128.0)
            )


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

            val rowStart: Int = ccNo * numBlocks * ppb
            val rowEnd: Int = rowStart + numBlocks * ppb
            val blockDcts = blockDctsAll.submat(rowStart, rowEnd, 0, 1)

            // Get the DCT coefficiens for every block, and quantize them
            for (blockNo in 0 until numBlocks) {
                val iMin: Int = blockNo * ppb
                val iMax: Int = iMin + ppb
                var blockDct = Mat()

                //DCT
                Core.dct(blocks[blockNo], blockDct)

                val blockDctFlat = blockDcts.submat(iMin, iMax, 0, 1)

                quantMat = quantMat!!.reshape(0, 8)

                //Quantization
                Core.divide(blockDct, quantMat, blockDct)


                blockDct = blockDct.reshape(0, ppb)
                blockDct.copyTo(blockDctFlat)
            }
            cc.release()
            ccs[ccNo].release()
        }

        // Convert to 8-bit signed ints for manipulating the LSB.
        blockDctsAll.convertTo(blockDctsAll, CvType.CV_8SC1)

        // Memory clean up.
        System.gc()

        return blockDctsAll
    }


    // function to convert DCTS to image MAT
    fun dctsTOim(dctsIn: Mat, sz: Size): Mat {

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

                //IDCT
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
            val ccOriginal = Crop.clone()
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


    // function to embed into DCT lsbs
    fun dctLsbEncode(dcts: Mat, binMsg: String, pass: Int, maxBits: Int) {

        var bitsWrit = 0 //no. of bits written
        val bitsTotal = binMsg.length
        var ind = 0 //index of dct

        while (bitsWrit < bitsTotal && bitsWrit < maxBits) {
            //read the dct coefficient
            val vall = byteArrayOf(0)
            dcts[ind, 0, vall]
            val dc = vall[0].toInt()

            // Only modify coefficients if they are neither 1 nor 0.
            if (dc != 0 && dc != 1) {

                // bits to be embedded acc to magnitude of dct
                var bitsToChange = if ((dc in 2..7) || (dc in -8..-1)) 1
                else if ((dc in 8..15) || (dc in -16..-9)) 2
                else if ((dc in 16..31) || (dc in -32..-17)) 3
                else if ((dc in 32..63) || (dc in -64..-33)) 4
                else 5

                var bitsFromMsg = if (bitsToChange < (binMsg.length - bitsWrit)) bitsToChange
                else (binMsg.length - bitsWrit)

                val bit = binMsg.substring(bitsWrit, bitsWrit + bitsFromMsg)


                var mask: Int =
                    2.0.pow(bitsToChange.toDouble()).toInt() - 1 // Mask of 1's with 0 in LSB.
                mask = mask.inv()

                // Embed into LSB
                val newVal: Int =
                    dc and mask or (Integer.parseInt(bit, 2) shl (bitsToChange - bitsFromMsg))

                //write the dct coefficient
                dcts.put(ind, 0, newVal.toDouble())

                bitsWrit += bitsToChange

            }
            ind++
        }
    }


    // function to read the message from embedded DCT lsbs
    fun dctLsbDecode(dcts: Mat, pass: Int): String? {

        // Set PRNG's seed
        val rand = Random()
        rand.setSeed(pass.toLong())

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
        var ind = 0 // index of dct
        var extraBits = NONE
        var extrabitsNo = 0

        while (lastByte != 0 && bytesRead < maxbits) {

            // read dct
            val vall = byteArrayOf(0)
            dcts[ind, 0, vall]
            val dc = vall[0].toInt()

            // Only read LSB if coefficient is neither 1 nor 0.
            if (dc != 0 && dc != 1) {


                if (extraBits != NONE) {
                    val noOfBits = extrabitsNo
                    curByte = curByte or (extraBits shl (8 - noOfBits))
                    bitNo = noOfBits
                    extraBits = NONE
                }

                val bitsToChange = if ((dc in 2..7) || (dc in -8..-1)) 1
                else if ((dc in 8..15) || (dc in -16..-9)) 2
                else if ((dc in 16..31) || (dc in -32..-17)) 3
                else if ((dc in 32..63) || (dc in -64..-33)) 5
                else 5

                val mask: Int = 2.0.pow(bitsToChange.toDouble()).toInt() - 1

                // Bit manipulation to extract LSB
                if (bitNo + bitsToChange <= 8) {
                    curByte = curByte or (dc and mask shl (8 - bitNo - bitsToChange))
                    bitNo += bitsToChange
                } else {
                    val extra = bitsToChange + bitNo - 8
                    extraBits = dc and (2.0.pow(extra.toDouble()).toInt() - 1)
                    curByte = curByte or ((dc and mask) shr extra)
                    bitNo = 8
                    extrabitsNo = extra
                }


                if (bitNo == 8) { // If full byte read, add it to message.
                    val curByteDecrypt = curByte xor rand.nextInt(127)
                    msgBytesIn[bytesRead] = curByteDecrypt.toByte()
                    bytesRead++
                    curByte = 0
                    bitNo = 0

                    if (bytesRead >= 2) {
                        val k = String(msgBytesIn, StandardCharsets.US_ASCII)
//                        Log.i(TAG, " str : ${k.substring(0, bytesRead)}")
                        if (k.substring(0, 2) != START_MESSAGE_CONSTANT) {
                            noMsg = true
                            break
                        }
                        if (k.substring(
                                bytesRead - END_MESSAGE_CONSTANT.length,
                                bytesRead
                            ) == END_MESSAGE_CONSTANT
                        ) {
                            lastByte = 0
                        }
                    }
                }
            }
            ind++
        }
        if (noMsg) return "No message detected"
        val s = String(msgBytesIn, StandardCharsets.US_ASCII)

        // return the string after removing the start and end constants
        return s.substring(START_MESSAGE_CONSTANT.length, bytesRead - END_MESSAGE_CONSTANT.length)
    }


    //Utility Functions

    // function to convert string to binary
    fun stringTObin(s: String): String? {
        val msgBytes: ByteArray = s.toByteArray()
        var msgBin: String? = ""
        for (b in msgBytes) {
            var nextChar = Integer.toBinaryString(b.toInt())
            if (nextChar.length > 8)
                nextChar = nextChar.substring(nextChar.lastIndex - 8, nextChar.lastIndex)

            // Zero pad the character representation so it is 8 bits.
            while (nextChar.length < 8) {
                nextChar = "0$nextChar"
            }
            msgBin += nextChar
        }
        return msgBin
    }


    // function get integer seed from user password
    fun getSeedFromPass(pass: String): Int {
        val md: MessageDigest = try {
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


    // funcntion to encrypt a string using one time pad (random key) encryption
    fun otpEncrypt(msg: String, seed: Int): String {

        var otpMsg = ""

        //set seed for random function
        val rand = Random()
        rand.setSeed(seed.toLong())

        for (char in msg) {
            val randInt = rand.nextInt(127)

            val xorByte = char.toInt() xor randInt

            otpMsg = "${otpMsg}${xorByte.toChar()}"
        }

        return stringTObin(otpMsg).toString()

    }

}