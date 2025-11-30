package com.mateopilco.ticdso.domain.model

/**
 * Estados del proceso de captura e inferencia.
 */
sealed class CaptureState {
    // Esperando acción del usuario
    object Idle : CaptureState()

    // Enviando imagen o esperando respuesta del servidor
    data class Processing(val progress: Float = 0f) : CaptureState()

    // Inferencia exitosa (contiene el mapa de profundidad)
    data class Success(val depthData: DepthData) : CaptureState()

    // Algo falló (red, servidor, cámara)
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : CaptureState()
}