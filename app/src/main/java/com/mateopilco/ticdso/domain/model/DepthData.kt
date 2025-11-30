package com.mateopilco.ticdso.domain.model

import android.graphics.Bitmap
import com.mateopilco.ticdso.util.CameraPose

/**
 * Modelo de datos de profundidad procesado.
 * Contiene toda la información necesaria para visualizar un frame:
 * 1. Profundidad (Bitmap)
 * 2. Color Original (Bitmap RGB)
 * 3. Posición en el mundo (Pose)
 */
data class DepthData(
    val depthBitmap: Bitmap?,      // El mapa de calor (Profundidad)
    val originalBitmap: Bitmap? = null, // La foto original (Color) para el preview
    val inferenceTime: Double,
    val imageShape: Pair<Int, Int>,
    val modelVersion: String = "PixelFormer Large07",

    // CAMBIO CLAVE: La posición de la cámara en el momento de esta foto.
    // Necesario para "pegar" los puntos en el lugar correcto del mapa global.
    val pose: CameraPose = CameraPose.identity()
)