package com.mateopilco.ticdso.presentation.viewmodel

import com.mateopilco.ticdso.domain.model.AppConfig
import com.mateopilco.ticdso.domain.model.CaptureState
import com.mateopilco.ticdso.domain.model.ConnectionState
import com.mateopilco.ticdso.domain.model.ImageSourceMode
import com.mateopilco.ticdso.util.CameraPose
import com.mateopilco.ticdso.util.Point3D

/**
 * Parámetros intrínsecos de la cámara.
 * Valores relativos (0.0-1.0) que se escalan según las dimensiones de la imagen.
 */
data class CameraIntrinsics(
    val fxRel: Float = 0.5f,  // Focal X relativa
    val fyRel: Float = 0.5f,  // Focal Y relativa
    val cxRel: Float = 0.5f,  // Centro X relativo
    val cyRel: Float = 0.5f,  // Centro Y relativo
    val isCalibrated: Boolean = false
)

/**
 * Estado inmutable de la UI principal.
 * Contiene toda la información necesaria para renderizar la escena 3D estilo Pangolin.
 */
data class MainUiState(
    // Configuración del sistema
    val config: AppConfig = AppConfig(),
    val intrinsics: CameraIntrinsics = CameraIntrinsics(),

    // Estados de conexión y captura
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val captureState: CaptureState = CaptureState.Idle,

    // Modo de entrada actual
    val currentMode: ImageSourceMode = ImageSourceMode.NONE,

    // Datos de visualización 3D (Estilo Pangolin)
    val pointCloud: List<Point3D> = emptyList(),
    val cameraTrajectory: List<CameraPose> = emptyList(), // NUEVO: Trayectoria de la cámara

    // Métricas de rendimiento
    val fps: Float = 0f,

    // Mensajes de error (opcional)
    val errorMessage: String? = null
)