package com.mateopilco.ticdso.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.mateopilco.ticdso.data.network.PixelFormerApi
import com.mateopilco.ticdso.data.network.RetrofitClient
import com.mateopilco.ticdso.domain.model.DepthData
import com.mateopilco.ticdso.domain.model.VisualFrame
import com.mateopilco.ticdso.domain.repository.DepthRepository
import com.mateopilco.ticdso.domain.source.ImageSource
import com.mateopilco.ticdso.util.BitmapUtils
import com.mateopilco.ticdso.util.CameraPose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import com.mateopilco.ticdso.util.Benchmarker // <--- IMPORTANTE

class DepthRepositoryImpl : DepthRepository {

    private val api: PixelFormerApi
        get() = RetrofitClient.api

    private var currentSource: ImageSource? = null

    override fun connectToServer(ip: String) {
        try {
            val cleanIp = ip.replace("http://", "").split(":")[0]
            val port = 5000
            RetrofitClient.setBaseUrl(cleanIp, port)
            Log.d("DepthRepository", "Configuración actualizada a $cleanIp:$port")
        } catch (e: Exception) {
            Log.e("DepthRepository", "Error configurando API", e)
        }
    }

    override suspend fun checkServerHealth(): Result<Boolean> {
        return try {
            val response = api.checkHealth()
            if (response.ready && (response.status == "healthy" || response.status == "success")) {
                Result.success(true)
            } else {
                Result.failure(Exception("Servidor no listo: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun startProcessing(source: ImageSource): Flow<Result<DepthData>> {
        stopProcessing()
        currentSource = source
        source.start()

        // El source.imageFlow ahora emite objetos VisualFrame (Bitmap + Pose)
        return source.imageFlow
            .onStart { Log.d("DepthRepo", "Iniciando flujo de imágenes") }
            .map { frame ->
                // Pasamos la imagen y la pose que viene del dataset
                performPrediction(frame.bitmap, frame.pose)
            }
            .catch { e ->
                Log.e("DepthRepo", "Error en flujo de procesamiento", e)
                emit(Result.failure(e))
            }
    }

    override fun stopProcessing() {
        currentSource?.stop()
        currentSource = null
    }

    /**
     * Método público de la interfaz (para fotos sueltas).
     * Asume posición 0,0,0 (Identidad).
     */
    override suspend fun predictDepth(image: Bitmap): Result<DepthData> {
        return performPrediction(image, CameraPose.identity())
    }

    /**
     * Lógica central de predicción.
     * Recibe la imagen para enviar al servidor y la pose para pasarla al resultado.
     */
    private suspend fun performPrediction(image: Bitmap, pose: CameraPose): Result<DepthData> {
        // === INICIO MEDICIÓN RED ===
        Benchmarker.startFrame()

        return try {
            // 1. Optimización de imagen
            val scaledBitmap = Bitmap.createScaledBitmap(image, 640, 480, true)
            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val byteArray = stream.toByteArray()

            val requestBody = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", "frame.jpg", requestBody)

            // --- Fase de RED (Latencia Pura) ---
            // Aquí empieza realmente la comunicación
            Benchmarker.startNetwork()

            // 2. Llamada al Servidor
            val response = api.predictDepth(body)

            // === FIN MEDICIÓN RED ===
            Benchmarker.endNetwork()   // <--- AÑADIR

            // 3. Mapeo de respuesta
            if (response.status == "success") {
                val depthMapBitmap = BitmapUtils.base64ToBitmap(response.depthData.data)

                val depthData = DepthData(
                    depthBitmap = depthMapBitmap,
                    originalBitmap = image,
                    inferenceTime = response.timing.inferenceMs / 1000.0,
                    imageShape = Pair(
                        response.imageInfo.depthWidth,
                        response.imageInfo.depthHeight
                    ),
                    modelVersion = "${response.modelInfo.name} ${response.modelInfo.version}",

                    // ¡AQUÍ ESTÁ LA CLAVE! Pasamos la pose recibida (del dataset) al ViewModel
                    pose = pose
                )

                Result.success(depthData)
            } else {
                Result.failure(Exception("Error del servidor: ${response.status}"))
            }

        } catch (e: Exception) {
            Log.e("DepthRepo", "Error en predicción", e)
            Result.failure(e)
        }
    }
}