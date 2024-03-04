package com.example.passwordmanager.ui.theme.shapes

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection


class BottomNavCustomShape(val radius: Float): Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {

        return Outline.Generic(Path().apply {
//            val radius = size.width/6f
            val smallRadius = radius/100f * 10f
            reset()
            moveTo(0f, 0f)
            lineTo(size.width/3f, 0f)
            arcTo(
                rect = Rect(
                    left = (size.width/2f - radius) - (2* smallRadius) ,
                    top = 0f,
                    right = size.width/2f - radius,
                    bottom = 2 * smallRadius
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            arcTo(
                    rect = Rect(
                        left = size.width/2f - radius ,
                        top = -radius + smallRadius,
                        right = size.width/2f + radius,
                        bottom = radius + smallRadius
                    ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = -180f,
            forceMoveTo = false
            )
            arcTo(
                rect = Rect(
                    left = (size.width/2f + radius) ,
                    top = 0f,
                    right = size.width/2f + radius  + (2* smallRadius),
                    bottom = 2 * smallRadius
                ),
                startAngleDegrees = -180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(0f, 0f)

            close()
        })
    }
}