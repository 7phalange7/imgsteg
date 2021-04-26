package com.example.imgsteg.algo

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import com.example.imgsteg.ui.TAG
import kotlin.math.ceil

const val END_MESSAGE_CONSTANT = "#@"
const val START_MESSAGE_CONSTANT = "@#"

object lsbAlgo {




    fun encode(bitmap: Bitmap?, mssg: String): Bitmap {

        val picWidth = bitmap!!.width
        val picHeight = bitmap.height
        var pixels = IntArray(picWidth * picHeight)
        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // convert to mutable bitmap
        mbitmap.getPixels(pixels, 0, picWidth, 0, 0, picWidth, picHeight)

        var msg = mssg

        msg = START_MESSAGE_CONSTANT + msg
        msg += END_MESSAGE_CONSTANT

        var b_msg = msg.toByteArray()

        if (pixels.size < b_msg.size) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) //empty-ish bitmap
        }

        pixels = lsbEncode(pixels, b_msg)

        mbitmap.setPixels(pixels, 0, picWidth, 0, 0, picWidth, picHeight)

        return mbitmap

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
                    Log.i(TAG, " msgData bit : ${msgdata} | rbg[j] : ${brgb.substring(0, brgb.length - 1) + msgdata}")

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
        }
        return pixels
    }


    fun decode(bitmap: Bitmap?): String {


        val picWidth = bitmap!!.width
        val picHeight = bitmap.height
        var pixels = IntArray(picWidth * picHeight)
        val mbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // convert to mutable bitmap
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

                byteChar.append(brgb[brgb.lastIndex])

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
                                Log.i(TAG, "Message( ${decodedString.toString()} )  Ended at pixel no. $index")
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

        return if (hiddenMessagePresent) decodedString.toString().substring(START_MESSAGE_CONSTANT.length, decodedString.lastIndex - END_MESSAGE_CONSTANT.length + 1)
        //return if(hiddenMessagePresent) "${decodedString.lastIndex} pixels : ${pixels.size}"
        else "NO HIDDEN MESSAGE"
    }


}