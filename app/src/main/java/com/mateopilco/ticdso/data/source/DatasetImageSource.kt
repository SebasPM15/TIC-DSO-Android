package com.mateopilco.ticdso.data.source

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.mateopilco.ticdso.domain.model.VisualFrame
import com.mateopilco.ticdso.domain.source.ImageSource
import com.mateopilco.ticdso.presentation.viewmodel.CameraIntrinsics
import com.mateopilco.ticdso.util.CameraPose
import com.mateopilco.ticdso.util.MathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.coroutineContext

class DatasetImageSource(
    private val context: Context,
    private val folderUri: Uri
) : ImageSource {

    private var isRunning = false

    // Cache de poses leídas del archivo groundtruth
    private val poses = ArrayList<CameraPose>()

    /**
     * Lee el archivo 'camera.txt' para obtener fx, fy, cx, cy.
     */
    suspend fun getCalibration(): CameraIntrinsics? = withContext(Dispatchers.IO) {
        try {
            val rootDir = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext null
            val targetDir = if (rootDir.name == "images") rootDir.parentFile else rootDir

            val calibFile = targetDir?.findFile("camera.txt")

            if (calibFile != null) {
                context.contentResolver.openInputStream(calibFile.uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val line = reader.readLine() // Primera línea tiene los intrínsecos
                    if (line != null) {
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            Log.d("DatasetSource", "Calibración leída: $line")
                            return@withContext CameraIntrinsics(
                                fxRel = parts[0].toFloat(),
                                fyRel = parts[1].toFloat(),
                                cxRel = parts[2].toFloat(),
                                cyRel = parts[3].toFloat(),
                                isCalibrated = true
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DatasetSource", "Error leyendo calibración", e)
        }
        return@withContext null
    }

    /**
     * Carga la trayectoria real desde 'groundtruthSync.txt'.
     */
    private suspend fun loadPoses() {
        poses.clear()
        try {
            val rootDir = DocumentFile.fromTreeUri(context, folderUri)
            val targetDir = if (rootDir?.name == "images") rootDir.parentFile else rootDir

            // Buscamos el archivo de trayectoria
            val poseFile = targetDir?.findFile("groundtruthSync.txt")
                ?: targetDir?.findFile("groundtruth.txt")

            if (poseFile != null) {
                context.contentResolver.openInputStream(poseFile.uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    reader.forEachLine { line ->
                        // Ignoramos comentarios (#)
                        if (!line.startsWith("#")) {
                            // Formato TUM/Engel: timestamp tx ty tz qx qy qz qw
                            val parts = line.trim().split("\\s+".toRegex())
                            // Necesitamos al menos 8 valores (timestamp + 7 pose)
                            if (parts.size >= 8) {
                                val pose = MathUtils.poseFromQuaternion(
                                    tx = parts[1].toFloat(),
                                    ty = parts[2].toFloat(),
                                    tz = parts[3].toFloat(),
                                    qx = parts[4].toFloat(),
                                    qy = parts[5].toFloat(),
                                    qz = parts[6].toFloat(),
                                    qw = parts[7].toFloat()
                                )
                                poses.add(pose)
                            }
                        }
                    }
                }
                Log.d("DatasetSource", "Trayectoria cargada: ${poses.size} poses.")
            } else {
                Log.w("DatasetSource", "No se encontró groundtruthSync.txt")
            }
        } catch (e: Exception) {
            Log.e("DatasetSource", "Error cargando poses", e)
        }
    }

    // CAMBIO CRÍTICO: Renombrado a 'imageFlow' para coincidir con la interfaz ImageSource
    override val imageFlow: Flow<VisualFrame> = flow {
        Log.d("DatasetSource", "Iniciando lectura de dataset...")

        val directory = DocumentFile.fromTreeUri(context, folderUri)
        if (directory == null || !directory.isDirectory) return@flow

        // 1. Cargar imágenes
        val files = directory.listFiles()
            .filter { it.type?.startsWith("image") == true }
            .sortedBy { it.name }

        if (files.isEmpty()) return@flow

        // 2. Cargar poses antes de empezar a emitir
        loadPoses()

        Log.i("DatasetSource", "Iniciando reproducción: ${files.size} imágenes.")
        isRunning = true
        var index = 0

        while (isRunning && coroutineContext.isActive) {
            if (index >= files.size) {
                index = 0
                delay(1000)
            }

            val file = files[index]

            // Sincronización simple por índice (Imagen 0 <-> Pose 0)
            // Si no hay pose (o se acabaron), usamos Identidad (0,0,0)
            val currentPose = if (index < poses.size) poses[index] else CameraPose.identity()

            try {
                context.contentResolver.openInputStream(file.uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        // Emitimos el paquete completo: Foto + Dónde estaba la cámara
                        emit(VisualFrame(bitmap, currentPose))
                    }
                }
            } catch (e: Exception) {
                Log.e("DatasetSource", "Error frame $index", e)
            }

            index++
            delay(100) // Simular tiempo real
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun start() {
        isRunning = true
    }

    override fun stop() {
        isRunning = false
    }
}