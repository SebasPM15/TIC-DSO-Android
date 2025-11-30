package com.mateopilco.ticdso.domain.repository

import android.graphics.Bitmap
import com.mateopilco.ticdso.domain.model.DepthData // <--- CAMBIO CLAVE: Usamos el modelo de dominio
import com.mateopilco.ticdso.domain.source.ImageSource
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del Repositorio.
 * Define QUÉ se puede hacer, sin decir CÓMO.
 * La UI y los ViewModels solo deben conocer esta interfaz.
 */
interface DepthRepository {

    /**
     * Configura la conexión inicial con el servidor (URL base).
     */
    fun connectToServer(ip: String)

    /**
     * Verifica si el servidor está vivo y listo.
     */
    suspend fun checkServerHealth(): Result<Boolean>

    /**
     * Inicia el procesamiento continuo de una fuente de imágenes (Cámara o Dataset).
     * Devuelve un Flow que emite DepthData (Modelo de Dominio) ya procesado.
     */
    // CORRECCIÓN: Return type cambiado de PixelFormerResponse a DepthData
    suspend fun startProcessing(source: ImageSource): Flow<Result<DepthData>>

    /**
     * Detiene cualquier procesamiento activo.
     */
    fun stopProcessing()

    /**
     * Envía una única imagen para predicción (útil para pruebas puntuales).
     */
    // CORRECCIÓN: Return type cambiado de PixelFormerResponse a DepthData
    suspend fun predictDepth(image: Bitmap): Result<DepthData>
}