package com.example.passwordmanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.passwordmanager.R

val poppinsFontFamily = FontFamily(
    Font(R.font.poppins_black),
    Font(R.font.poppins_bold),
    Font(R.font.poppins_extrabold),
    Font(R.font.poppins_light),
    Font(R.font.poppins_extralight),
    Font(R.font.poppins_medium),
    Font(R.font.poppins_regular),
    Font(R.font.poppins_thin),
    Font(R.font.poppins_semibold),
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
    labelMedium = TextStyle(
        fontFamily = poppinsFontFamily ,
        fontWeight = FontWeight.Medium
    )
)