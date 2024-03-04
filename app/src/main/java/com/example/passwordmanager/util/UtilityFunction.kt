package com.example.passwordmanager.util

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

class UtilityFunction {
    fun Context.toast(message: CharSequence) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    fun FragmentActivity.toast(message: CharSequence) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}