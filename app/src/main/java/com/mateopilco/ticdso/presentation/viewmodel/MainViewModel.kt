package com.mateopilco.ticdso.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateopilco.ticdso.data.repository.DepthRepositoryImpl
import com.mateopilco.ticdso.data.source.DatasetImageSource
import com.mateopilco.ticdso.domain.model.AppConfig
import com.mateopilco.ticdso.domain.model.CaptureState
import com.mateopilco.ticdso.domain.model.ConnectionState
import com.mateopilco.ticdso.domain.model.ImageSourceMode
import com.mateopilco.ticdso.domain.model.ServerInfo
import com.mateopilco.ticdso.domain.repository.DepthRepository
import com.mateopilco.ticdso.domain.source.ImageSource
import com.mateopilco.ticdso.util.CameraPose
import com.mateopilco.ticdso.util.MathUtils
import com.mateopilco.ticdso.util.Point3D
import com.mateopilco.ticdso.util.PointsGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel principal siguiendo arquitectura MVVM + Clean Architecture.
 *
 * Responsabilidades:
 * 1. Gestión de estado de UI (MainUiState)
 * 2. Coordinación del pipeline de reconstrucción 3D
 * 3. Gestión del mapa global con estrategia Keyframe
 * 4. Cálculo de FPS y métricas de rendimiento
 *
 * Estrategia de mapeo:
 * - KEYFRAMES: Se guardan en el mapa global cada N frames
 * - FRAME ACTUAL: Se renderiza con alta densidad para feedback inmediato
 * - SLIDING WINDOW: Mantiene máximo 200k puntos en memoria
 */
class MainViewModel(
    private val repository: DepthRepository = DepthRepositoryImpl()
) : ViewModel() {

    // ============ ESTADO DE LA UI ============

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // ============ CONTROL DE PROCESAMIENTO ============

    private var processingJob: Job? = null
    private var lastFrameTime = System.currentTimeMillis()
    private var frameCount = 0

    // ============ ESTRATEGIA KEYFRAME (DSO-like) ============

    /** Máximo de puntos en el mapa global (ajustar según RAM disponible) */
    private val MAX_GLOBAL_POINTS = 200_000

    /** Mapa global persistente (coordenadas del mundo) */
    private val globalPointCloud = ArrayList<Point3D>()

    /** Trayectoria de la cámara (para visualizar recorrido) */
    private val cameraTrajectory = ArrayList<CameraPose>()

    /** Intervalo de guardado (1 de cada 10 frames se convierte en keyframe) */
    private val KEYFRAME_INTERVAL = 10

    /** Contador interno de frames procesados */
    private var framesProcessedCount = 0


    // ============ INICIALIZACIÓN ============

    init {
        updateConfig(_uiState.value.config)
    }


    // ============ CONFIGURACIÓN DEL SERVIDOR ============

    /**
     * Actualiza la configuración del servidor Flask.
     */
    fun updateConfig(config: AppConfig) {
        _uiState.update { it.copy(config = config) }
        repository.connectToServer("${config.serverHost}:${config.serverPort}")
    }

    /**
     * Prueba la conexión con el endpoint /health.
     */
    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = ConnectionState.Connecting) }

            val result = repository.checkServerHealth()

            _uiState.update { state ->
                state.copy(
                    connectionState = if (result.isSuccess) {
                        ConnectionState.Connected(
                            ServerInfo(
                                host = state.config.serverHost,
                                port = state.config.serverPort,
                                modelVersion = "PixelFormer Large07"
                            )
                        )
                    } else {
                        ConnectionState.Error(
                            result.exceptionOrNull()?.message ?: "Error desconocido"
                        )
                    }
                )
            }
        }
    }


    // ============ CONTROL DE CAPTURA ============

    /**
     * Cambia el modo de captura (Cámara/Dataset).
     *
     * @param mode Modo seleccionado
     * @param source Fuente de imágenes (CameraImageSource o DatasetImageSource)
     */
    fun setMode(mode: ImageSourceMode, source: ImageSource? = null) {
        stopCapture()

        if (mode == ImageSourceMode.NONE || source == null) {
            _uiState.update { it.copy(currentMode = ImageSourceMode.NONE) }
            return
        }

        viewModelScope.launch {
            var currentIntrinsics = CameraIntrinsics()

            // Si es dataset, cargar calibración desde camera.txt
            if (source is DatasetImageSource) {
                _uiState.update { it.copy(captureState = CaptureState.Processing(0f)) }
                val calib = source.getCalibration()
                if (calib != null) {
                    currentIntrinsics = calib
                }
            }

            _uiState.update {
                it.copy(
                    currentMode = mode,
                    intrinsics = currentIntrinsics,
                    captureState = CaptureState.Processing(0f)
                )
            }

            startProcessing(source)
        }
    }

    /**
     * Inicia el pipeline de procesamiento continuo.
     */
    private fun startProcessing(source: ImageSource) {
        // Limpiar estado anterior
        globalPointCloud.clear()
        cameraTrajectory.clear()
        framesProcessedCount = 0

        processingJob = viewModelScope.launch {
            repository.startProcessing(source)
                .onEach { result ->
                    result.onSuccess { depthData ->

                        // Actualizar FPS
                        updateFps()
                        framesProcessedCount++

                        val currentIntrinsics = _uiState.value.intrinsics

                        // === PROCESAMIENTO 3D EN HILO SEPARADO ===
                        val pointsToRender = withContext(Dispatchers.Default) {
                            processDepthData(
                                depthData = depthData,
                                intrinsics = currentIntrinsics
                            )
                        }

                        // === ACTUALIZAR UI (MAIN THREAD) ===
                        _uiState.update {
                            it.copy(
                                captureState = CaptureState.Success(depthData),
                                pointCloud = pointsToRender,
                                cameraTrajectory = ArrayList(cameraTrajectory), // Copia segura
                                totalKeyframes = cameraTrajectory.size,
                                totalPoints = pointsToRender.size
                            )
                        }

                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(
                                captureState = CaptureState.Error(
                                    error.message ?: "Error procesando frame"
                                )
                            )
                        }
                    }
                }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            captureState = CaptureState.Error("Fatal: ${e.message}")
                        )
                    }
                }
                .launchIn(this)
        }
    }


    // ============ LÓGICA DE MAPEO (CORE) ============

    /**
     * Procesa un frame de profundidad siguiendo estrategia DSO.
     *
     * Algoritmo:
     * 1. Si es KEYFRAME (cada N frames):
     *    - Generar puntos filtrados por gradiente
     *    - Guardar pose en trayectoria
     *    - Agregar al mapa global
     *    - Aplicar sliding window
     *
     * 2. FRAME ACTUAL:
     *    - Generar puntos para visualización inmediata
     *
     * 3. FUSIÓN:
     *    - Combinar mapa histórico + frame actual
     *
     * @return Lista de puntos 3D en coordenadas globales
     */
    private fun processDepthData(
        depthData: com.mateopilco.ticdso.domain.model.DepthData,
        intrinsics: CameraIntrinsics
    ): List<Point3D> {

        if (depthData.depthBitmap == null || depthData.originalBitmap == null) {
            return emptyList()
        }

        // === ESTRATEGIA 1: KEYFRAMES (MAPA GLOBAL) ===
        if (framesProcessedCount % KEYFRAME_INTERVAL == 0) {

            // Guardar pose de la cámara en la trayectoria
            synchronized(cameraTrajectory) {
                cameraTrajectory.add(depthData.pose)
            }

            // Generar puntos filtrados (DSO selector)
            val keyframePoints = PointsGenerator.generatePointCloud(
                depthBitmap = depthData.depthBitmap,
                originalBitmap = depthData.originalBitmap,
                intrinsics = intrinsics,
                step = 6  // Ignorado por el nuevo generador
            )

            // Transformar a coordenadas globales
            val globalKeyframePoints = keyframePoints.map { point ->
                MathUtils.transformPoint(point, depthData.pose)
            }

            // Agregar al mapa global con sliding window
            synchronized(globalPointCloud) {
                globalPointCloud.addAll(globalKeyframePoints)

                // Control de memoria (FIFO)
                if (globalPointCloud.size > MAX_GLOBAL_POINTS) {
                    val excess = globalPointCloud.size - MAX_GLOBAL_POINTS
                    globalPointCloud.subList(0, excess).clear()
                }
            }
        }

        // === ESTRATEGIA 2: FRAME ACTUAL (FEEDBACK INMEDIATO) ===
        val currentFramePoints = PointsGenerator.generatePointCloud(
            depthBitmap = depthData.depthBitmap,
            originalBitmap = depthData.originalBitmap,
            intrinsics = intrinsics,
            step = 6  // Ignorado
        )

        val globalCurrentPoints = currentFramePoints.map { point ->
            MathUtils.transformPoint(point, depthData.pose)
        }

        // === ESTRATEGIA 3: FUSIÓN (RENDER FINAL) ===
        val finalScene = ArrayList<Point3D>(
            globalPointCloud.size + globalCurrentPoints.size
        )

        synchronized(globalPointCloud) {
            finalScene.addAll(globalPointCloud)
        }
        finalScene.addAll(globalCurrentPoints)

        return finalScene
    }


    // ============ CONTROL DE CAPTURA ============

    /**
     * Detiene la captura pero mantiene el mapa global.
     */
    fun stopCapture() {
        processingJob?.cancel()
        processingJob = null
        repository.stopProcessing()

        _uiState.update {
            it.copy(
                currentMode = ImageSourceMode.NONE,
                captureState = CaptureState.Idle,
                fps = 0f
            )
        }
    }


    // ============ MÉTRICAS DE RENDIMIENTO ============

    /**
     * Calcula FPS basado en tiempo transcurrido.
     */
    private fun updateFps() {
        frameCount++
        val now = System.currentTimeMillis()
        val diff = now - lastFrameTime

        if (diff >= 1000) {
            val fps = (frameCount * 1000f) / diff
            _uiState.update { it.copy(fps = fps) }
            frameCount = 0
            lastFrameTime = now
        }
    }


    // ============ LIMPIEZA ============

    override fun onCleared() {
        super.onCleared()
        stopCapture()
        globalPointCloud.clear()
        cameraTrajectory.clear()
    }
}