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
import com.mateopilco.ticdso.util.Benchmarker // <--- IMPORTANTE

class MainViewModel(
    private val repository: DepthRepository = DepthRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var processingJob: Job? = null
    private var lastFrameTime = System.currentTimeMillis()
    private var frameCount = 0

    // === ESTRATEGIA KEYFRAME MEJORADA ===
    private val MAX_GLOBAL_POINTS = 200000
    private val globalPointCloud = ArrayList<Point3D>()

    // NUEVO: Lista de poses de la cámara (para dibujar trayectoria)
    private val cameraTrajectory = ArrayList<CameraPose>()

    // Guardamos keyframes cada 10 frames
    private val KEYFRAME_INTERVAL = 10
    private var framesProcessedCount = 0

    init {
        updateConfig(_uiState.value.config)
    }

    fun updateConfig(config: AppConfig) {
        _uiState.update { it.copy(config = config) }
        repository.connectToServer("${config.serverHost}:${config.serverPort}")
    }

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
                                modelVersion = "PixelFormer"
                            )
                        )
                    } else {
                        ConnectionState.Error(result.exceptionOrNull()?.message ?: "Error desconocido")
                    }
                )
            }
        }
    }

    fun setMode(mode: ImageSourceMode, source: ImageSource? = null) {
        stopCapture()

        if (mode == ImageSourceMode.NONE || source == null) {
            _uiState.update { it.copy(currentMode = ImageSourceMode.NONE) }
            return
        }

        viewModelScope.launch {
            var currentIntrinsics = CameraIntrinsics()

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

    private fun startProcessing(source: ImageSource) {
        // Limpiar mapa global y trayectoria al iniciar nuevo procesamiento
        globalPointCloud.clear()
        cameraTrajectory.clear()
        framesProcessedCount = 0

        processingJob = viewModelScope.launch {
            repository.startProcessing(source)
                .onEach { result ->
                    result.onSuccess { depthData ->
                        updateFps()
                        framesProcessedCount++

                        val currentIntrinsics = _uiState.value.intrinsics

                        // === INICIO MEDICIÓN 3D ===
                        Benchmarker.start3DProcessing() // <--- AÑADIR

                        // === LÓGICA DE MAPEO ESTILO PANGOLIN ===
                        val pointsToRender = withContext(Dispatchers.Default) {
                            if (depthData.depthBitmap != null && depthData.originalBitmap != null) {

                                // 1. KEYFRAMES: Guardar en el mapa global
                                if (framesProcessedCount % KEYFRAME_INTERVAL == 0) {

                                    // Guardar la pose en la trayectoria
                                    synchronized(cameraTrajectory) {
                                        cameraTrajectory.add(depthData.pose)
                                    }

                                    // Generar puntos filtrados por gradiente
                                    val keyframePoints = PointsGenerator.generatePointCloud(
                                        depthBitmap = depthData.depthBitmap,
                                        originalBitmap = depthData.originalBitmap,
                                        intrinsics = currentIntrinsics,
                                        step = 6 // Paso optimizado para estructura visible
                                    )

                                    // Transformar a coordenadas globales
                                    val globalKeyframePoints = keyframePoints.map {
                                        MathUtils.transformPoint(it, depthData.pose)
                                    }

                                    synchronized(globalPointCloud) {
                                        globalPointCloud.addAll(globalKeyframePoints)

                                        // Sliding window para controlar memoria
                                        if (globalPointCloud.size > MAX_GLOBAL_POINTS) {
                                            val excess = globalPointCloud.size - MAX_GLOBAL_POINTS
                                            globalPointCloud.subList(0, excess).clear()
                                        }
                                    }
                                }

                                // 2. FRAME ACTUAL: Alta densidad para visualización inmediata
                                val currentFramePoints = PointsGenerator.generatePointCloud(
                                    depthBitmap = depthData.depthBitmap,
                                    originalBitmap = depthData.originalBitmap,
                                    intrinsics = currentIntrinsics,
                                    step = 6
                                )

                                val globalCurrentPoints = currentFramePoints.map {
                                    MathUtils.transformPoint(it, depthData.pose)
                                }

                                // 3. FUSIÓN: Combinar mapa histórico + frame actual
                                val finalScene = ArrayList<Point3D>(
                                    globalPointCloud.size + globalCurrentPoints.size
                                )
                                synchronized(globalPointCloud) {
                                    finalScene.addAll(globalPointCloud)
                                }
                                finalScene.addAll(globalCurrentPoints)

                                finalScene
                            } else {
                                emptyList()
                            }
                        }

                        // Actualizar UI con puntos y trayectoria
                        _uiState.update {
                            it.copy(
                                captureState = CaptureState.Success(depthData),
                                pointCloud = pointsToRender,
                                cameraTrajectory = ArrayList(cameraTrajectory) // Copia para evitar concurrencia
                            )
                        }

                        // === FIN MEDICIÓN GLOBAL ===
                        Benchmarker.endFrame() // <--- AÑADIR
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(captureState = CaptureState.Error(error.message ?: "Error"))
                        }
                    }
                }
                .catch { e ->
                    _uiState.update {
                        it.copy(captureState = CaptureState.Error("Fatal: ${e.message}"))
                    }
                }
                .launchIn(this)
        }
    }

    fun stopCapture() {
        processingJob?.cancel()
        processingJob = null
        repository.stopProcessing()

        // Persistir el mapa global pero resetear FPS
        _uiState.update {
            it.copy(
                currentMode = ImageSourceMode.NONE,
                captureState = CaptureState.Idle,
                fps = 0f
            )
        }
    }

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

    override fun onCleared() {
        super.onCleared()
        stopCapture()
        globalPointCloud.clear()
        cameraTrajectory.clear()
    }
}