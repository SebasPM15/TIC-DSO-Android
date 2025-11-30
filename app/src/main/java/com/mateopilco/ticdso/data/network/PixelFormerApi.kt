package com.mateopilco.ticdso.data.network

import com.mateopilco.ticdso.data.network.dto.HealthResponse
import com.mateopilco.ticdso.data.network.dto.PixelFormerResponse
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Interfaz Retrofit para comunicarse con el servidor Flask PixelFormer.
 *
 * Base URL: http://192.168.3.36:5000/
 *
 * Endpoints disponibles:
 * - GET  /health           -> Verificar estado del servidor
 * - POST /api/v1/predict   -> Predecir profundidad de una imagen
 */
interface PixelFormerApi {

    /**
     * Verifica el estado del servidor Flask.
     *
     * Endpoint: GET /health
     *
     * Respuesta esperada:
     * {
     *   "status": "healthy",
     *   "model": "PixelFormer Large07",
     *   "ready": true
     * }
     */
    @GET("health")
    suspend fun checkHealth(): HealthResponse

    /**
     * Envía una imagen al servidor para predecir su mapa de profundidad.
     *
     * Endpoint: POST /api/v1/predict
     *
     * @param image Imagen en formato MultipartBody.Part (JPEG)
     *
     * Respuesta esperada:
     * {
     *   "status": "success",
     *   "model_info": { ... },
     *   "timing": { ... },
     *   "depth_data": {
     *     "format": "base64_png",
     *     "data": "iVBORw0KGgoAAAANSUhEUgAA...",
     *     "min_depth": 0.5,
     *     "max_depth": 10.0,
     *     "mean_depth": 3.2
     *   }
     * }
     */
    @Multipart
    @POST("api/v1/predict")
    suspend fun predictDepth(
        @Part image: MultipartBody.Part
    ): PixelFormerResponse
}