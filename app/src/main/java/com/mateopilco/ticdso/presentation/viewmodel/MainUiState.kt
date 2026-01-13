package com.mateopilco.ticdso.presentation.viewmodel

import com.mateopilco.ticdso.domain.model.AppConfig
import com.mateopilco.ticdso.domain.model.CaptureState
import com.mateopilco.ticdso.domain.model.ConnectionState
import com.mateopilco.ticdso.domain.model.ImageSourceMode
import com.mateopilco.ticdso.util.CameraPose
import com.mateopilco.ticdso.util.Point3D

/**
 * Estado inmutable de la UI principal.
 *
 * Sigue el patrón UDF (Unidirectional Data Flow):
 * - La UI solo lee este estado
 * - Solo el ViewModel puede modificarlo
 */
data class MainUiState(
    // === CONFIGURACIÓN ===
    val config: AppConfig = AppConfig(),

    // === ESTADOS DE CONEXIÓN ===
    val connectionState: ConnectionState = ConnectionState.Disconnected,

    // === ESTADOS DE CAPTURA ===
    val currentMode: ImageSourceMode = ImageSourceMode.NONE,
    val captureState: CaptureState = CaptureState.Idle,

    // === DATOS 3D ===
    val pointCloud: List<Point3D> = emptyList(),
    val cameraTrajectory: List<CameraPose> = emptyList(),

    // === CALIBRACIÓN ===
    val intrinsics: CameraIntrinsics = CameraIntrinsics(),

    // === MÉTRICAS ===
    val fps: Float = 0f,
    val totalKeyframes: Int = 0,
    val totalPoints: Int = 0
)

/**
 * Parámetros intrínsecos de la cámara.
 *
 * Siguiendo el modelo Pinhole Camera:
 * - fx, fy: Distancias focales (en píxeles)
 * - cx, cy: Centro óptico (punto principal)
 *
 * Los valores relativos (*Rel) son proporciones respecto al tamaño de la imagen.
 */
data class CameraIntrinsics(
    val fxRel: Float = 0.5f,      // fx / width
    val fyRel: Float = 0.5f,      // fy / height
    val cxRel: Float = 0.5f,      // cx / width
    val cyRel: Float = 0.5f,      // cy / height
    val isCalibrated: Boolean = false  // true si se leyó de camera.txt
)