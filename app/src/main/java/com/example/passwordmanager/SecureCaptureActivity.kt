package com.example.passwordmanager

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

const val EXTRA_SCANNER_ALTERNATIVE = "com.example.passwordmanager.SCANNER_ALTERNATIVE"

/** Another way to add an account, chosen on the scanner instead of scanning. */
enum class ScannerAlternative { Gallery, ManualEntry }

/** The QR scanner, kept out of screenshots and the recents preview like the rest of the app. */
class SecureCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        super.onCreate(savedInstanceState)
    }

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.secure_capture)
        findViewById<View>(R.id.scanner_gallery).setOnClickListener { finishWith(ScannerAlternative.Gallery) }
        findViewById<View>(R.id.scanner_manual_entry).setOnClickListener { finishWith(ScannerAlternative.ManualEntry) }
        return findViewById(R.id.zxing_barcode_scanner)
    }

    // A cancelled result keeps its intent through ScanContract, so the choice arrives in originalIntent.
    private fun finishWith(alternative: ScannerAlternative) {
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_SCANNER_ALTERNATIVE, alternative.name))
        finish()
    }
}
