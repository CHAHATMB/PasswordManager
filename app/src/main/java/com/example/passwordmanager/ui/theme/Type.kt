package com.example.passwordmanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.passwordmanager.R

private val poppinsFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold)
)

private fun poppins(weight: FontWeight, size: Int, lineHeight: Int, letterSpacing: Double) = TextStyle(
    fontFamily = poppinsFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

// Material 3 type scale sizes; headlines and titles one weight heavier than the default.
val Typography = Typography(
    displayLarge = poppins(FontWeight.Normal, 57, 64, -0.25),
    displayMedium = poppins(FontWeight.Normal, 45, 52, 0.0),
    displaySmall = poppins(FontWeight.Normal, 36, 44, 0.0),
    headlineLarge = poppins(FontWeight.SemiBold, 32, 40, 0.0),
    headlineMedium = poppins(FontWeight.SemiBold, 28, 36, 0.0),
    headlineSmall = poppins(FontWeight.SemiBold, 24, 32, 0.0),
    titleLarge = poppins(FontWeight.SemiBold, 22, 28, 0.0),
    titleMedium = poppins(FontWeight.Medium, 16, 24, 0.15),
    titleSmall = poppins(FontWeight.Medium, 14, 20, 0.1),
    bodyLarge = poppins(FontWeight.Normal, 16, 24, 0.5),
    bodyMedium = poppins(FontWeight.Normal, 14, 20, 0.25),
    bodySmall = poppins(FontWeight.Normal, 12, 16, 0.4),
    labelLarge = poppins(FontWeight.Medium, 14, 20, 0.1),
    labelMedium = poppins(FontWeight.Medium, 12, 16, 0.5),
    labelSmall = poppins(FontWeight.Medium, 11, 16, 0.5)
)
