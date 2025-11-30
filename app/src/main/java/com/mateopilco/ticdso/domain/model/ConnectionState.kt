package com.mateopilco.ticdso.domain.model

/**
 * Representa el estado de la conexión con el backend Flask.
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val serverInfo: ServerInfo) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Datos informativos del servidor una vez conectado.
 */
data class ServerInfo(
    val host: String,
    val port: Int,
    val modelVersion: String,
    val isHealthy: Boolean = true
)