package com.mateopilco.ticdso.domain.source

import com.mateopilco.ticdso.domain.model.VisualFrame
import kotlinx.coroutines.flow.Flow

interface ImageSource {
    // CAMBIO: Ahora el flujo es de VisualFrame
    val imageFlow: Flow<VisualFrame>

    suspend fun start()
    fun stop()
}