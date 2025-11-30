package com.mateopilco.ticdso.domain.model

import android.graphics.Bitmap
import com.mateopilco.ticdso.util.CameraPose

/**
 * Paquete de datos que emite la fuente.
 * Contiene la imagen visual y (opcionalmente) dónde estaba la cámara en ese momento.
 */
data class VisualFrame(
    val bitmap: Bitmap,
    val pose: CameraPose = CameraPose.identity()
)