package com.mateopilco.ticdso.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

object BitmapUtils {

    /**
     * Convierte un Bitmap a MultipartBody.Part para enviar por Retrofit
     */
    fun bitmapToMultipart(bitmap: Bitmap, paramName: String = "image", fileName: String = "capture.jpg"): MultipartBody.Part {
        val stream = ByteArrayOutputStream()
        // Comprimir a JPEG calidad 80 para equilibrio velocidad/calidad
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()

        val requestFile = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(paramName, fileName, requestFile)
    }

    /**
     * Decodifica un string Base64 a Bitmap
     */
    fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Rota un bitmap (necesario porque CameraX a veces entrega imágenes rotadas)
     */
    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}