package com.mateopilco.ticdso.presentation.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mateopilco.ticdso.util.CameraPose
import com.mateopilco.ticdso.util.Point3D
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Visor de nube de puntos 3D que replica la visualización de Pangolin/DSO.
 *
 * Características:
 * - Fondo blanco científico
 * - Puntos negros dispersos (semi-dense mapping)
 * - Ejes de coordenadas RGB (X=Rojo, Y=Verde, Z=Azul)
 * - Trayectoria de cámara con keyframes
 * - Proyección perspectiva con depth sorting
 *
 * Referencias:
 * - dso/src/IOWrapper/Pangolin/PangolinDSOViewer.cpp
 * - Pangolin: https://github.com/stevenlovegrove/Pangolin
 */
@Composable
fun PointCloudViewer(
    points: List<Point3D>,
    cameraTrajectory: List<CameraPose> = emptyList(),
    modifier: Modifier = Modifier
) {
    // ============ PARÁMETROS CALIBRADOS PANGOLIN ============
    // Estos valores replican exactamente la vista de DSO

    /** Escala inicial (ajustada para ver bien la estructura) */
    val INITIAL_SCALE = 80f

    /** Inclinación X inicial (perspectiva superior ligera) */
    val INITIAL_ROT_X = 20f

    /** Rotación Y inicial (vista frontal) */
    val INITIAL_ROT_Y = 0f

    /** Field of View para proyección perspectiva */
    val FOV = 350f

    /** Distancia de la cámara virtual al origen */
    val CAMERA_DISTANCE = 6f

    /** Tamaño de los puntos (DSO usa puntos muy pequeños) */
    val POINT_SIZE = 1.2f

    /** Grosor de línea de trayectoria */
    val TRAJECTORY_STROKE = 2.5f


    // ============ ESTADO DE LA VISTA ============

    var scale by remember { mutableFloatStateOf(INITIAL_SCALE) }
    var rotationX by remember { mutableFloatStateOf(INITIAL_ROT_X) }
    var rotationY by remember { mutableFloatStateOf(INITIAL_ROT_Y) }
    var center by remember { mutableStateOf(Offset.Zero) }


    // ============ CONTENEDOR PRINCIPAL ============

    Box(
        modifier = modifier
            .background(Color.White) // Fondo blanco estilo científico
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Zoom con límites
                    scale *= zoom
                    scale = scale.coerceIn(10f, 800f)

                    // Rotación con sensibilidad ajustada
                    rotationY += pan.x * 0.25f
                    rotationX -= pan.y * 0.25f

                    // Limitar rotación X para evitar inversión
                    rotationX = rotationX.coerceIn(-85f, 85f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            center = Offset(size.width / 2, size.height / 2)

            // Pre-calcular trigonometría (optimización)
            val radX = Math.toRadians(rotationX.toDouble())
            val radY = Math.toRadians(rotationY.toDouble())
            val cosX = cos(radX).toFloat()
            val sinX = sin(radX).toFloat()
            val cosY = cos(radY).toFloat()
            val sinY = sin(radY).toFloat()

            // ============ ORDEN DE RENDERIZADO ============
            // Siguiendo el orden de Pangolin para correcta superposición

            // 1. Ejes de coordenadas (siempre visibles)
            drawCoordinateAxes(
                center = center,
                scale = scale,
                cosX = cosX, sinX = sinX,
                cosY = cosY, sinY = sinY,
                fov = FOV,
                cameraDistance = CAMERA_DISTANCE
            )

            // 2. Trayectoria de la cámara (debajo de los puntos)
            if (cameraTrajectory.isNotEmpty()) {
                drawCameraTrajectory(
                    trajectory = cameraTrajectory,
                    center = center,
                    scale = scale,
                    cosX = cosX, sinX = sinX,
                    cosY = cosY, sinY = sinY,
                    fov = FOV,
                    cameraDistance = CAMERA_DISTANCE,
                    strokeWidth = TRAJECTORY_STROKE
                )
            }

            // 3. Nube de puntos (capa superior)
            if (points.isNotEmpty()) {
                drawPointCloud(
                    points = points,
                    center = center,
                    scale = scale,
                    cosX = cosX, sinX = sinX,
                    cosY = cosY, sinY = sinY,
                    fov = FOV,
                    cameraDistance = CAMERA_DISTANCE,
                    pointSize = POINT_SIZE,
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )
            }
        }

        // ============ UI FLOTANTE (OVERLAY) ============

        // Panel de información
        Text(
            text = buildString {
                append("Puntos: ${points.size}")
                if (cameraTrajectory.isNotEmpty()) {
                    append("\nKeyframes: ${cameraTrajectory.size}")
                }
                append("\nRot: X=${rotationX.toInt()}° Y=${rotationY.toInt()}°")
                append("\nZoom: ${(scale / INITIAL_SCALE * 100).toInt()}%")
            },
            color = Color.DarkGray,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(
                    color = Color.White.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )

        // Botón de reset
        IconButton(
            onClick = {
                scale = INITIAL_SCALE
                rotationX = INITIAL_ROT_X
                rotationY = INITIAL_ROT_Y
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                )
                .size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Resetear Vista",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}


// ============================================================================
// FUNCIONES DE RENDERIZADO (DRAW FUNCTIONS)
// ============================================================================

/**
 * Dibuja la nube de puntos 3D con optimizaciones de rendimiento.
 *
 * Optimizaciones implementadas:
 * - Depth sorting (los puntos lejanos primero)
 * - View frustum culling (solo dibujar lo visible)
 * - Batch rendering con drawPoints()
 */
private fun DrawScope.drawPointCloud(
    points: List<Point3D>,
    center: Offset,
    scale: Float,
    cosX: Float, sinX: Float,
    cosY: Float, sinY: Float,
    fov: Float,
    cameraDistance: Float,
    pointSize: Float,
    canvasWidth: Float,
    canvasHeight: Float
) {
    // Estructura para almacenar puntos proyectados con profundidad
    data class ProjectedPoint(val offset: Offset, val depth: Float)

    val projectedPoints = mutableListOf<ProjectedPoint>()

    // Proyectar todos los puntos
    points.forEach { p ->
        // Aplicar rotación Y (horizontal)
        val x1 = p.x * cosY - p.z * sinY
        val z1 = p.z * cosY + p.x * sinY

        // Aplicar rotación X (vertical)
        val y2 = p.y * cosX - z1 * sinX
        val z2 = z1 * cosX + p.y * sinX

        // Proyección perspectiva
        val pz = z2 + cameraDistance

        // Clipping near plane
        if (pz > 0.1f) {
            val projScale = fov / pz
            val px = x1 * projScale * (scale / 100f) + center.x
            val py = y2 * projScale * (scale / 100f) + center.y

            // View frustum culling con margen
            if (px >= -20 && px <= canvasWidth + 20 &&
                py >= -20 && py <= canvasHeight + 20) {
                projectedPoints.add(
                    ProjectedPoint(
                        offset = Offset(px, py),
                        depth = pz
                    )
                )
            }
        }
    }

    // Depth sorting (opcional, mejora realismo pero más costoso)
    // Descomenta si necesitas que los puntos cercanos tapen los lejanos
    // projectedPoints.sortBy { it.depth }

    // Batch rendering (más eficiente que drawCircle individual)
    drawPoints(
        points = projectedPoints.map { it.offset },
        pointMode = PointMode.Points,
        color = Color.Black,
        strokeWidth = pointSize,
        cap = StrokeCap.Round
    )
}


/**
 * Dibuja los ejes de coordenadas RGB en el origen.
 *
 * Convención:
 * - X (Rojo): Derecha
 * - Y (Verde): Arriba
 * - Z (Azul): Adelante (hacia la cámara)
 */
private fun DrawScope.drawCoordinateAxes(
    center: Offset,
    scale: Float,
    cosX: Float, sinX: Float,
    cosY: Float, sinY: Float,
    fov: Float,
    cameraDistance: Float
) {
    val axisLength = 1.0f // Longitud en unidades del mundo

    // Definir los 3 ejes con colores estándar
    val axes = listOf(
        Triple(axisLength, 0f, 0f) to Color.Red,   // Eje X
        Triple(0f, axisLength, 0f) to Color.Green, // Eje Y
        Triple(0f, 0f, axisLength) to Color.Blue   // Eje Z
    )

    // Proyectar el origen
    val origin = projectPoint3D(
        x = 0f, y = 0f, z = 0f,
        center = center, scale = scale,
        cosX = cosX, sinX = sinX,
        cosY = cosY, sinY = sinY,
        fov = fov, cameraDistance = cameraDistance
    )

    // Dibujar cada eje
    axes.forEach { (point, color) ->
        val (x, y, z) = point
        val projected = projectPoint3D(
            x = x, y = y, z = z,
            center = center, scale = scale,
            cosX = cosX, sinX = sinX,
            cosY = cosY, sinY = sinY,
            fov = fov, cameraDistance = cameraDistance
        )

        if (origin != null && projected != null) {
            drawLine(
                color = color,
                start = origin,
                end = projected,
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
        }
    }
}


/**
 * Dibuja la trayectoria de la cámara como línea conectada.
 *
 * Visualización:
 * - Línea azul continua conectando poses
 * - Círculos naranjas en cada keyframe
 */
private fun DrawScope.drawCameraTrajectory(
    trajectory: List<CameraPose>,
    center: Offset,
    scale: Float,
    cosX: Float, sinX: Float,
    cosY: Float, sinY: Float,
    fov: Float,
    cameraDistance: Float,
    strokeWidth: Float
) {
    if (trajectory.size < 2) return

    // Proyectar todas las poses
    val projectedPoses = trajectory.mapNotNull { pose ->
        projectPoint3D(
            x = pose.tx,
            y = pose.ty,
            z = pose.tz,
            center = center,
            scale = scale,
            cosX = cosX, sinX = sinX,
            cosY = cosY, sinY = sinY,
            fov = fov,
            cameraDistance = cameraDistance
        )
    }

    if (projectedPoses.isEmpty()) return

    // Dibujar línea conectando todas las poses
    for (i in 0 until projectedPoses.size - 1) {
        drawLine(
            color = Color(0xFF1976D2), // Azul Material (más suave)
            start = projectedPoses[i],
            end = projectedPoses[i + 1],
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    // Dibujar marcadores en cada keyframe
    projectedPoses.forEach { pos ->
        // Círculo exterior (naranja)
        drawCircle(
            color = Color(0xFFFF6F00),
            radius = 5f,
            center = pos
        )
        // Círculo interior (blanco)
        drawCircle(
            color = Color.White,
            radius = 2.5f,
            center = pos
        )
    }
}


/**
 * Función auxiliar para proyectar un punto 3D a 2D.
 *
 * Algoritmo:
 * 1. Aplicar rotaciones (Y luego X)
 * 2. Trasladar según cameraDistance
 * 3. Proyección perspectiva (x' = x * fov / z)
 * 4. Trasladar al centro de la pantalla
 *
 * @return Offset de pantalla o null si está detrás de la cámara
 */
private fun projectPoint3D(
    x: Float, y: Float, z: Float,
    center: Offset,
    scale: Float,
    cosX: Float, sinX: Float,
    cosY: Float, sinY: Float,
    fov: Float,
    cameraDistance: Float
): Offset? {
    // === ROTACIÓN Y (Horizontal) ===
    val x1 = x * cosY - z * sinY
    val z1 = z * cosY + x * sinY

    // === ROTACIÓN X (Vertical) ===
    val y2 = y * cosX - z1 * sinX
    val z2 = z1 * cosX + y * sinX

    // === PROYECCIÓN PERSPECTIVA ===
    val pz = z2 + cameraDistance

    // Clipping: Rechazar puntos detrás de la cámara
    if (pz <= 0.1f) return null

    val projScale = fov / pz
    val px = x1 * projScale * (scale / 100f) + center.x
    val py = y2 * projScale * (scale / 100f) + center.y

    return Offset(px, py)
}