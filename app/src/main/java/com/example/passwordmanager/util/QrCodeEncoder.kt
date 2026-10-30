package com.example.passwordmanager.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

// Large enough to stay sharp at full screen width; scaled down without smoothing on screen.
private const val QR_SIZE_PX = 768

object QrCodeEncoder {

    /** Black modules on white with a quiet zone, so it scans in dark mode too. */
    fun encode(text: String): Bitmap = BarcodeEncoder().encodeBitmap(text, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX)
}
