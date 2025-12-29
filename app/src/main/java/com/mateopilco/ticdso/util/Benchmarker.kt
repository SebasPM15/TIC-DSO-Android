package com.mateopilco.ticdso.util

import android.util.Log

object Benchmarker {
    private const val TAG = "TIC_BENCHMARK"

    // Variables temporales para el frame actual
    private var tStartTotal: Long = 0
    private var tStartNetwork: Long = 0
    private var tEndNetwork: Long = 0
    private var tStartProcessing: Long = 0

    // Banderas para saber si estamos en medio de una medición válida
    private var isMeasuring = false

    fun startFrame() {
        isMeasuring = true
        tStartTotal = System.currentTimeMillis()
        // Log para debug visual rápido
        // Log.d(TAG, "--- INICIO FRAME ---")
    }

    fun startNetwork() {
        if (!isMeasuring) return
        tStartNetwork = System.currentTimeMillis()
    }

    fun endNetwork() {
        if (!isMeasuring) return
        tEndNetwork = System.currentTimeMillis()
    }

    fun start3DProcessing() {
        if (!isMeasuring) return
        tStartProcessing = System.currentTimeMillis()
    }

    fun endFrame() {
        if (!isMeasuring) return
        val tEndTotal = System.currentTimeMillis()

        // Cálculo de deltas
        val totalTime = tEndTotal - tStartTotal
        val networkTime = tEndNetwork - tStartNetwork

        // El procesamiento 3D es desde que empezó hasta que terminó todo (aprox)
        // o mejor, desde que terminó la red hasta el final (incluye decode + 3D)
        val processingTime = tEndTotal - tStartProcessing

        // === ESTE ES EL FORMATO CSV PARA COPIAR A EXCEL ===
        // Formato: TOTAL, RED, PROCESAMIENTO_3D
        Log.i(TAG, "CSV_DATA,$totalTime,$networkTime,$processingTime")

        isMeasuring = false
    }
}