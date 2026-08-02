package com.example.messaggingblemesh.qrcode

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder

object QrCodeUtils {
    fun generatorQRbitmap(content: String): ImageBitmap? {
        return try{
            val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, null)
            val bitmap: Bitmap = BarcodeEncoder().createBitmap(matrix)
            bitmap.asImageBitmap()
        }catch(e : Exception){
            e.printStackTrace()
            null
        }
    }
}