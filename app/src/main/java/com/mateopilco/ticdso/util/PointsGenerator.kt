package com.mateopilco.ticdso.util

import android.graphics.Bitmap
import android.graphics.Color
import com.mateopilco.ticdso.presentation.viewmodel.CameraIntrinsics
import kotlin.math.abs

data class Point3D(val x: Float, val y: Float, val z: Float, val color: Int)

object PointsGenerator {

    private const val DEPTH_SCALE = 2.0f

    // === MATEMÁTICA DE DSO ===
    // En el archivo C++ (settings.cpp), el umbral base (setting_minGradHistAdd) es 7.
    // Como vamos a usar gradiente al cuadrado (dx^2 + dy^2), 7^2 = 49.
    // Usamos 50 como base para filtrar "paredes lisas" igual que DSO.
    private const val GRADIENT_SQ_THRESHOLD = 50

    fun generatePointCloud(
        depthBitmap: Bitmap,
        intrinsics: CameraIntrinsics,
        originalBitmap: Bitmap? = null,
        step: Int = 4
    ): List<Point3D> {
        val points = ArrayList<Point3D>()

        if (originalBitmap == null) return emptyList()
        val width = depthBitmap.width
        val height = depthBitmap.height

        val scaledOriginal = if (originalBitmap.width != width || originalBitmap.height != height) {
            Bitmap.createScaledBitmap(originalBitmap, width, height, true)
        } else {
            originalBitmap
        }

        val depthPixels = IntArray(width * height)
        val rgbPixels = IntArray(width * height)

        depthBitmap.getPixels(depthPixels, 0, width, 0, 0, width, height)
        scaledOriginal.getPixels(rgbPixels, 0, width, 0, 0, width, height)

        // Parámetros Intrínsecos
        val fx = if(intrinsics.isCalibrated) intrinsics.fxRel * width else 256.0f
        val fy = if(intrinsics.isCalibrated) intrinsics.fyRel * height else 254.4f
        val cx = if(intrinsics.isCalibrated) intrinsics.cxRel * width else 319.5f
        val cy = if(intrinsics.isCalibrated) intrinsics.cyRel * height else 239.5f

        for (v in 0 until height - step step step) {
            for (u in 0 until width - step step step) {

                val index = v * width + u

                // === 1. GRADIENTE AL CUADRADO (Fórmula DSO) ===
                val pixelC = rgbPixels[index]
                val pixelR = rgbPixels[index + step] // Derecha
                val pixelD = rgbPixels[index + step * width] // Abajo

                // Usamos canal Verde como intensidad (es lo estándar en visión rápida)
                val valC = (pixelC shr 8) and 0xFF
                val valR = (pixelR shr 8) and 0xFF
                val valD = (pixelD shr 8) and 0xFF

                val dx = valC - valR
                val dy = valC - valD

                // Esta es la métrica que usa DSO (absSquaredGrad)
                val gradientSq = (dx * dx) + (dy * dy)

                // === 2. FILTRO ===
                if (gradientSq > GRADIENT_SQ_THRESHOLD) {

                    val pixelColorDepth = depthPixels[index]
                    val normalizedDepth = (Color.red(pixelColorDepth) / 255f) * 10f

                    if (normalizedDepth > 0.1f && normalizedDepth < 9.5f) {

                        val z = normalizedDepth * DEPTH_SCALE
                        val x = (u - cx) * z / fx
                        val y = (v - cy) * z / fy

                        // Color Negro (Estilo Pangolin)
                        points.add(Point3D(x, y, z, Color.BLACK))
                    }
                }
            }
        }
        return points
    }
}