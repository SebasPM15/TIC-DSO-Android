package com.mateopilco.ticdso.data.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.mateopilco.ticdso.domain.model.VisualFrame
import com.mateopilco.ticdso.domain.source.ImageSource
import com.mateopilco.ticdso.util.CameraPose
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Implementación de ImageSource que utiliza la cámara del dispositivo (CameraX).
 * Emite frames visuales en tiempo real para el procesamiento.
 */
class CameraImageSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : ImageSource {

    private var provider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()

    // CORRECCIÓN: Renombrado de 'frameFlow' a 'imageFlow' para cumplir con la interfaz ImageSource
    override val imageFlow: Flow<VisualFrame> = callbackFlow {
        Log.d("CameraSource", "Iniciando flujo de cámara...")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                provider = cameraProviderFuture.get()
                val cameraProvider = provider ?: return@addListener

                // 1. Configurar el Análisis de Imagen
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    // Convertir el frame YUV a Bitmap
                    val bitmap = imageProxy.toBitmap()

                    if (bitmap != null) {
                        // Empaquetamos el Bitmap en un VisualFrame.
                        // Usamos Pose Identidad (0,0,0) porque la cámara del celular (aún) no tiene tracking.
                        val frame = VisualFrame(
                            bitmap = bitmap,
                            pose = CameraPose.identity()
                        )

                        // Emitir al flujo
                        trySend(frame)
                    } else {
                        Log.w("CameraSource", "No se pudo convertir el frame a Bitmap")
                    }

                    imageProxy.close()
                }

                // 2. Seleccionar cámara trasera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // 3. Vincular al ciclo de vida
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )

                Log.d("CameraSource", "Cámara vinculada exitosamente")

            } catch (exc: Exception) {
                Log.e("CameraSource", "Error al iniciar cámara", exc)
                close(exc)
            }

        }, ContextCompat.getMainExecutor(context))

        awaitClose {
            Log.d("CameraSource", "Deteniendo cámara...")
            provider?.unbindAll()
        }
    }

    override suspend fun start() {
        Log.d("CameraSource", "Estrategia Cámara activada")
    }

    override fun stop() {
        provider?.unbindAll()
        Log.d("CameraSource", "Estrategia Cámara detenida")
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
            val out = ByteArrayOutputStream()

            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
            val imageBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            val rotationDegrees = this.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }

            return bitmap
        } catch (e: Exception) {
            Log.e("CameraSource", "Error en conversión YUV->Bitmap", e)
            return null
        }
    }
}