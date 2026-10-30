package com.example.passwordmanager.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.ReaderException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Larger images are subsampled while decoding, so a huge photo can't exhaust memory.
private const val MAX_IMAGE_SIDE_PX = 2048

/** Reads a QR code from an image picked through the Storage Access Framework. */
class QrImageReader(private val contentResolver: ContentResolver) {

    /** The QR code's text, or null when the image can't be read or holds no QR code. */
    suspend fun read(uri: Uri): String? = withContext(Dispatchers.Default) {
        val bitmap = try {
            decodeBitmap(uri)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } ?: return@withContext null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
        bitmap.recycle()
        try {
            QRCodeReader().decode(image, mapOf(DecodeHintType.TRY_HARDER to true)).text
        } catch (e: ReaderException) {
            null
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds, decodeStream returns null and only fills in the size.
        val input = contentResolver.openInputStream(uri) ?: return null
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIDE_PX) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}
