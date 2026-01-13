package com.mateopilco.ticdso.util

import android.graphics.Bitmap
import android.graphics.Color
import com.mateopilco.ticdso.presentation.viewmodel.CameraIntrinsics
import kotlin.math.abs

data class Point3D(val x: Float, val y: Float, val z: Float, val color: Int)

/**
 * Generador de nube de puntos siguiendo la lógica exacta de DSO.
 *
 * Referencias científicas:
 * - dso/src/FullSystem/PixelSelector2.cpp (makeMaps, select)
 * - dso/src/util/settings.cpp (setting_minGradHistAdd = 7)
 * - Paper: Direct Sparse Odometry (Engel et al., 2018)
 *
 * Optimizaciones implementadas:
 * 1. Selector por bloques (Grid-based selection)
 * 2. Filtro de gradiente cuadrático
 * 3. Control de densidad por frame
 */
object PointsGenerator {

    // ============ PARÁMETROS CALIBRADOS DSO ============

    /** Escala de profundidad (ajustar según tu modelo) */
    private const val DEPTH_SCALE = 2.0f

    /** Umbral de gradiente al cuadrado (DSO: 7^2 = 49) */
    private const val GRADIENT_SQ_THRESHOLD = 50

    /** Tamaño del bloque de selección (DSO usa 32x32) */
    private const val BLOCK_SIZE = 32

    /** Máximo de puntos por frame (DSO: ~2000) */
    private const val MAX_POINTS_PER_FRAME = 2000

    /** Profundidad mínima válida (metros) */
    private const val MIN_DEPTH = 0.1f

    /** Profundidad máxima válida (metros) */
    private const val MAX_DEPTH = 9.5f


    // ============ FUNCIÓN PRINCIPAL ============

    /**
     * Genera nube de puntos 3D semi-densa con filtro de gradiente.
     *
     * Estrategia DSO:
     * 1. Dividir imagen en bloques de BLOCK_SIZE x BLOCK_SIZE
     * 2. En cada bloque, seleccionar el píxel con mayor gradiente
     * 3. Solo mantener los N mejores píxeles globalmente
     * 4. Back-project a 3D usando modelo pinhole
     *
     * @param depthBitmap Mapa de profundidad (0-255 normalizado)
     * @param intrinsics Calibración de la cámara
     * @param originalBitmap Imagen RGB para calcular gradientes
     * @param step Parámetro deprecado (se ignora, usamos BLOCK_SIZE)
     * @return Lista de puntos 3D filtrados
     */
    fun generatePointCloud(
        depthBitmap: Bitmap,
        originalBitmap: Bitmap?,
        intrinsics: CameraIntrinsics,
        step: Int = 6  // Ignorado, solo por compatibilidad
    ): List<Point3D> {

        if (originalBitmap == null) return emptyList()

        val width = depthBitmap.width
        val height = depthBitmap.height

        // Escalar imagen RGB si es necesario
        val scaledOriginal = if (originalBitmap.width != width || originalBitmap.height != height) {
            Bitmap.createScaledBitmap(originalBitmap, width, height, true)
        } else {
            originalBitmap
        }

        // Pre-cargar píxeles en arrays (optimización de acceso)
        val depthPixels = IntArray(width * height)
        val rgbPixels = IntArray(width * height)
        depthBitmap.getPixels(depthPixels, 0, width, 0, 0, width, height)
        scaledOriginal.getPixels(rgbPixels, 0, width, 0, 0, width, height)

        // Calcular parámetros intrínsecos
        val fx = if(intrinsics.isCalibrated) intrinsics.fxRel * width else 256.0f
        val fy = if(intrinsics.isCalibrated) intrinsics.fyRel * height else 254.4f
        val cx = if(intrinsics.isCalibrated) intrinsics.cxRel * width else 319.5f
        val cy = if(intrinsics.isCalibrated) intrinsics.cyRel * height else 239.5f

        // === PASO 1: SELECCIÓN POR BLOQUES (GRID) ===
        val candidates = selectPixelsByGrid(
            rgbPixels = rgbPixels,
            depthPixels = depthPixels,
            width = width,
            height = height
        )

        // === PASO 2: ORDENAR Y LIMITAR ===
        val selectedPixels = candidates
            .sortedByDescending { it.gradient }
            .take(MAX_POINTS_PER_FRAME)

        // === PASO 3: BACK-PROJECTION A 3D ===
        val points = ArrayList<Point3D>(selectedPixels.size)

        for (candidate in selectedPixels) {
            val u = candidate.u
            val v = candidate.v
            val index = v * width + u

            // Leer profundidad normalizada
            val pixelDepth = depthPixels[index]
            val normalizedDepth = (Color.red(pixelDepth) / 255f) * 10f

            // Validar rango
            if (normalizedDepth < MIN_DEPTH || normalizedDepth > MAX_DEPTH) continue

            // Escalar a metros
            val z = normalizedDepth * DEPTH_SCALE

            // Modelo Pinhole Inverso
            val x = (u - cx) * z / fx
            val y = (v - cy) * z / fy

            // Color negro (estilo Pangolin)
            points.add(Point3D(x, y, z, Color.BLACK))
        }

        return points
    }


    // ============ SELECTOR POR BLOQUES (CORE DSO) ============

    /**
     * Candidato de píxel con su gradiente calculado.
     */
    private data class PixelCandidate(
        val u: Int,
        val v: Int,
        val gradient: Int  // Gradiente al cuadrado
    )

    /**
     * Selecciona píxeles dividiendo la imagen en bloques.
     * En cada bloque, elige el píxel con mayor gradiente.
     *
     * Esto garantiza:
     * - Distribución espacial uniforme
     * - Reducción drástica de puntos redundantes
     * - Foco en estructuras (bordes, esquinas)
     */
    private fun selectPixelsByGrid(
        rgbPixels: IntArray,
        depthPixels: IntArray,
        width: Int,
        height: Int
    ): List<PixelCandidate> {

        val candidates = ArrayList<PixelCandidate>()

        // Calcular número de bloques
        val numBlocksX = (width + BLOCK_SIZE - 1) / BLOCK_SIZE
        val numBlocksY = (height + BLOCK_SIZE - 1) / BLOCK_SIZE

        // Iterar sobre cada bloque
        for (by in 0 until numBlocksY) {
            for (bx in 0 until numBlocksX) {

                // Límites del bloque actual
                val x0 = bx * BLOCK_SIZE
                val y0 = by * BLOCK_SIZE
                val x1 = minOf(x0 + BLOCK_SIZE, width - 1)
                val y1 = minOf(y0 + BLOCK_SIZE, height - 1)

                // Encontrar píxel con mayor gradiente en este bloque
                var maxGradient = 0
                var bestU = x0
                var bestV = y0

                for (v in y0 until y1) {
                    for (u in x0 until x1) {

                        // Verificar que hay píxeles adyacentes para calcular gradiente
                        if (u >= width - 1 || v >= height - 1) continue

                        val index = v * width + u

                        // === CÁLCULO DE GRADIENTE (DSO Style) ===
                        val pixelC = rgbPixels[index]
                        val pixelR = rgbPixels[index + 1]        // Derecha
                        val pixelD = rgbPixels[index + width]    // Abajo

                        // Usar canal verde (más sensible a luminancia)
                        val valC = (pixelC shr 8) and 0xFF
                        val valR = (pixelR shr 8) and 0xFF
                        val valD = (pixelD shr 8) and 0xFF

                        val dx = valC - valR
                        val dy = valC - valD

                        // Gradiente al cuadrado (más eficiente que sqrt)
                        val gradientSq = (dx * dx) + (dy * dy)

                        // Actualizar mejor píxel del bloque
                        if (gradientSq > maxGradient) {
                            maxGradient = gradientSq
                            bestU = u
                            bestV = v
                        }
                    }
                }

                // Solo agregar si supera el umbral
                if (maxGradient > GRADIENT_SQ_THRESHOLD) {
                    candidates.add(
                        PixelCandidate(
                            u = bestU,
                            v = bestV,
                            gradient = maxGradient
                        )
                    )
                }
            }
        }

        return candidates
    }
}