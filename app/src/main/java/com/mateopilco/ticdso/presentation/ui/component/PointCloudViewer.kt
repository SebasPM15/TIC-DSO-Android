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

/**
 * Visor de nube de puntos 3D estilo Pangolin.
 * Replica la visualización científica de DSO con:
 * - Fondo blanco
 * - Puntos negros dispersos (filtrados por gradiente)
 * - Ejes de coordenadas RGB
 * - Trayectoria de la cámara
 */
@Composable
fun PointCloudViewer(
    points: List<Point3D>,
    cameraTrajectory: List<CameraPose> = emptyList(),
    modifier: Modifier = Modifier
) {
    // === PARÁMETROS CALIBRADOS PARA PANGOLIN ===
    // Estos valores fueron ajustados para replicar la vista de DSO
    val INITIAL_SCALE = 100f  // Más grande para ver mejor la estructura
    val INITIAL_ROT_X = 15f   // Inclinación ligera (como Pangolin)
    val INITIAL_ROT_Y = 0f
    val FOV = 400f            // Field of view más realista
    val CAMERA_DISTANCE = 8f  // Distancia de la cámara virtual

    var scale by remember { mutableFloatStateOf(INITIAL_SCALE) }
    var rotationX by remember { mutableFloatStateOf(INITIAL_ROT_X) }
    var rotationY by remember { mutableFloatStateOf(INITIAL_ROT_Y) }
    var center by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .background(Color.White) // Fondo blanco estilo científico
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    scale = scale.coerceIn(10f, 1000f)

                    // Sensibilidad ajustada para control fino
                    rotationY += pan.x * 0.2f
                    rotationX -= pan.y * 0.2f

                    // Limitar rotación X para evitar vuelcos
                    rotationX = rotationX.coerceIn(-89f, 89f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            center = Offset(size.width / 2, size.height / 2)

            // Pre-calcular matrices de rotación
            val radX = Math.toRadians(rotationX.toDouble())
            val radY = Math.toRadians(rotationY.toDouble())
            val cosX = cos(radX).toFloat()
            val sinX = sin(radX).toFloat()
            val cosY = cos(radY).toFloat()
            val sinY = sin(radY).toFloat()

            // === 1. DIBUJAR EJES DE COORDENADAS (Estilo Pangolin) ===
            drawCoordinateAxes(
                center = center,
                scale = scale,
                cosX = cosX, sinX = sinX,
                cosY = cosY, sinY = sinY,
                fov = FOV,
                cameraDistance = CAMERA_DISTANCE
            )

            // === 2. DIBUJAR TRAYECTORIA DE LA CÁMARA ===
            if (cameraTrajectory.isNotEmpty()) {
                drawCameraTrajectory(
                    trajectory = cameraTrajectory,
                    center = center,
                    scale = scale,
                    cosX = cosX, sinX = sinX,
                    cosY = cosY, sinY = sinY,
                    fov = FOV,
                    cameraDistance = CAMERA_DISTANCE
                )
            }

            // === 3. DIBUJAR NUBE DE PUNTOS ===
            if (points.isEmpty()) return@Canvas

            val projectedPoints = mutableListOf<Offset>()

            points.forEach { p ->
                // Aplicar rotación Y (horizontal)
                val x1 = p.x * cosY - p.z * sinY
                val z1 = p.z * cosY + p.x * sinY

                // Aplicar rotación X (vertical)
                val y2 = p.y * cosX - z1 * sinX
                val z2 = z1 * cosX + p.y * sinX

                // Proyección perspectiva
                val pz = z2 + CAMERA_DISTANCE

                if (pz > 0.1f) {
                    val projScale = FOV / pz
                    val px = x1 * projScale * (scale / 100f) + center.x
                    val py = y2 * projScale * (scale / 100f) + center.y

                    // Culling (solo dibujar lo visible)
                    if (px >= -10 && px <= size.width + 10 &&
                        py >= -10 && py <= size.height + 10) {
                        projectedPoints.add(Offset(px, py))
                    }
                }
            }

            // Dibujar puntos con tamaño pequeño (estilo disperso)
            drawPoints(
                points = projectedPoints,
                pointMode = PointMode.Points,
                color = Color.Black,
                strokeWidth = 1.5f, // Puntos pequeños para estructura fina
                cap = StrokeCap.Round
            )
        }

        // === UI FLOTANTE ===
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
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.White.copy(alpha = 0.8f), MaterialTheme.shapes.small)
                .padding(6.dp)
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
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .size(40.dp)
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

/**
 * Dibuja los ejes de coordenadas RGB (X=Rojo, Y=Verde, Z=Azul)
 * Igual que Pangolin muestra siempre en la esquina
 */
private fun DrawScope.drawCoordinateAxes(
    center: Offset,
    scale: Float,
    cosX: Float, sinX: Float,
    cosY: Float, sinY: Float,
    fov: Float,
    cameraDistance: Float
) {
    val axisLength = 1.5f // Longitud de los ejes en unidades del mundo

    // Definir los 3 ejes
    val axes = listOf(
        Triple(axisLength, 0f, 0f) to Color.Red,   // X
        Triple(0f, axisLength, 0f) to Color.Green, // Y
        Triple(0f, 0f, axisLength) to Color.Blue   // Z
    )

    val origin = projectPoint3D(
        x = 0f, y = 0f, z = 0f,
        center = center, scale = scale,
        cosX = cosX, sinX = sinX,
        cosY = cosY, sinY = sinY,
        fov = fov, cameraDistance = cameraDistance
    )

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
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Dibuja la trayectoria de la cámara como línea conectada
 */
private fun DrawScope.drawCameraTrajectory(
    trajectory: List<CameraPose>,
    center: Offset,
    scale: Float,
    cosX: Float, sinX: Float,
    cosY: Float, sinY: Float,
    fov: Float,
    cameraDistance: Float
) {
    if (trajectory.size < 2) return

    val projectedPoses = trajectory.mapNotNull { pose ->
        projectPoint3D(
            x = pose.tx, y = pose.ty, z = pose.tz,
            center = center, scale = scale,
            cosX = cosX, sinX = sinX,
            cosY = cosY, sinY = sinY,
            fov = fov, cameraDistance = cameraDistance
        )
    }

    // Dibujar línea conectando todas las poses
    for (i in 0 until projectedPoses.size - 1) {
        drawLine(
            color = Color(0xFF2196F3), // Azul brillante
            start = projectedPoses[i],
            end = projectedPoses[i + 1],
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }

    // Dibujar círculos en cada keyframe
    projectedPoses.forEach { pos ->
        drawCircle(
            color = Color(0xFFFF5722), // Naranja
            radius = 4f,
            center = pos
        )
    }
}

/**
 * Función helper para proyectar un punto 3D a 2D
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
    // Rotación Y
    val x1 = x * cosY - z * sinY
    val z1 = z * cosY + x * sinY

    // Rotación X
    val y2 = y * cosX - z1 * sinX
    val z2 = z1 * cosX + y * sinX

    // Proyección
    val pz = z2 + cameraDistance
    if (pz <= 0.1f) return null

    val projScale = fov / pz
    val px = x1 * projScale * (scale / 100f) + center.x
    val py = y2 * projScale * (scale / 100f) + center.y

    return Offset(px, py)
}