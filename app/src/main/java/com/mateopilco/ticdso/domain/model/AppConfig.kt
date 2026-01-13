package com.mateopilco.ticdso.domain.model

/**
 * Configuración de conexión al servidor.
 * Usado por la UI para mostrar/editar la IP y por el Repo para conectar.
 */
data class AppConfig(
    val serverHost: String = "192.168.3.42", // Tu IP por defecto
    val serverPort: Int = 5000,
    val timeout: Long = 60000L
)