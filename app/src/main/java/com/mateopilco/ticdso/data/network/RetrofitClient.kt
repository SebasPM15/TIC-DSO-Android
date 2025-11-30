package com.mateopilco.ticdso.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // URL por defecto
    private var baseUrl = "http://192.168.3.36:5000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

    // Instancia privada de Retrofit
    private var retrofit: Retrofit = buildRetrofit()

    // Instancia cacheada de la API
    private var _api: PixelFormerApi? = null

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Actualiza la URL base, reconstruye Retrofit y limpia la cache de la API
     */
    fun setBaseUrl(host: String, port: Int) {
        baseUrl = "http://$host:$port/"
        retrofit = buildRetrofit()
        _api = null // Invalidamos la API anterior para forzar su recreación
    }

    /**
     * Acceso a la API. Si no existe o cambió la URL, se crea una nueva.
     */
    val api: PixelFormerApi
        get() {
            if (_api == null) {
                _api = retrofit.create(PixelFormerApi::class.java)
            }
            return _api!!
        }
}