package com.mateopilco.ticdso.util

import android.util.Patterns

object NetworkUtils {
    /**
     * Valida si una cadena es una dirección IP válida (IPv4)
     */
    fun isValidIp(ip: String): Boolean {
        return Patterns.IP_ADDRESS.matcher(ip).matches()
    }

    /**
     * Valida si un puerto está en rango válido
     */
    fun isValidPort(port: String): Boolean {
        return try {
            val p = port.toInt()
            p in 1..65535
        } catch (e: Exception) {
            false
        }
    }
}