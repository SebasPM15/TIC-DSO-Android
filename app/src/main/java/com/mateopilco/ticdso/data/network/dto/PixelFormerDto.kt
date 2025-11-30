package com.mateopilco.ticdso.data.network.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs para mapear la respuesta JSON del servidor Flask PixelFormer.
 *
 * Estructura esperada del servidor:
 * {
 *   "status": "success",
 *   "model_info": { ... },
 *   "timing": { ... },
 *   "image_info": { ... },
 *   "depth_data": { "format": "base64_png", "data": "...", ... }
 * }
 */

/**
 * Respuesta principal del endpoint /api/v1/predict
 */
data class PixelFormerResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("model_info")
    val modelInfo: ModelInfoDto,

    @SerializedName("timing")
    val timing: TimingDto,

    @SerializedName("image_info")
    val imageInfo: ImageInfoDto,

    @SerializedName("depth_data")
    val depthData: DepthDataDto
)

/**
 * Información del modelo PixelFormer
 */
data class ModelInfoDto(
    @SerializedName("name")
    val name: String,

    @SerializedName("version")
    val version: String,

    @SerializedName("backbone")
    val backbone: String
)

/**
 * Información de tiempos de procesamiento
 */
data class TimingDto(
    @SerializedName("inference_ms")
    val inferenceMs: Double,

    @SerializedName("total_ms")
    val totalMs: Double,

    @SerializedName("timestamp")
    val timestamp: Double
)

/**
 * Información de las dimensiones de la imagen
 */
data class ImageInfoDto(
    @SerializedName("original_width")
    val originalWidth: Int,

    @SerializedName("original_height")
    val originalHeight: Int,

    @SerializedName("depth_width")
    val depthWidth: Int,

    @SerializedName("depth_height")
    val depthHeight: Int
)

/**
 * Datos del mapa de profundidad
 */
data class DepthDataDto(
    @SerializedName("format")
    val format: String,  // "base64_png"

    @SerializedName("encoding")
    val encoding: String,  // "grayscale"

    @SerializedName("data")
    val data: String,  // Base64 string del mapa de profundidad

    @SerializedName("min_depth")
    val minDepth: Float,

    @SerializedName("max_depth")
    val maxDepth: Float,

    @SerializedName("mean_depth")
    val meanDepth: Float
)

/**
 * Respuesta del endpoint /health
 */
data class HealthResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("model")
    val model: String,

    @SerializedName("ready")
    val ready: Boolean
)