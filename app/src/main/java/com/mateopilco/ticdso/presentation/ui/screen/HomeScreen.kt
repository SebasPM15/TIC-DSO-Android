package com.mateopilco.ticdso.presentation.ui.screen

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mateopilco.ticdso.data.source.CameraImageSource
import com.mateopilco.ticdso.data.source.DatasetImageSource
import com.mateopilco.ticdso.domain.model.*
import com.mateopilco.ticdso.presentation.ui.component.PointCloudViewer
import com.mateopilco.ticdso.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    // Launcher para permisos de cámara
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.setMode(
                ImageSourceMode.CAMERA,
                CameraImageSource(context, lifecycleOwner)
            )
        }
    }

    // Launcher para seleccionar la carpeta del dataset
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setMode(
                ImageSourceMode.DATASET,
                DatasetImageSource(context, it)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TIC-DSO: Reconstrucción 3D", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. TARJETA DE CONFIGURACIÓN (IP/Puerto)
            ConfigCard(
                config = uiState.config,
                connectionState = uiState.connectionState,
                onConfigChange = { viewModel.updateConfig(it) },
                onConnect = { viewModel.testConnection() }
            )

            // 2. TARJETA DE SELECCIÓN DE FUENTE
            SourceSelectionCard(
                currentMode = uiState.currentMode,
                onCameraClick = {
                    val permissions = mutableListOf(Manifest.permission.CAMERA)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                },
                onDatasetClick = { folderLauncher.launch(null) }
            )

            // 3. ÁREA DE VISUALIZACIÓN 3D + PANELES INFERIORES (ESTILO PANGOLIN)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Color.LightGray, RoundedCornerShape(8.dp))
            ) {
                if (uiState.pointCloud.isNotEmpty()) {
                    // ===== CAMBIO CLAVE: Pasar trayectoria al visor =====
                    PointCloudViewer(
                        points = uiState.pointCloud,
                        cameraTrajectory = uiState.cameraTrajectory, // ← NUEVO
                        modifier = Modifier.fillMaxSize()
                    )

                    // B. PANELES DE VISUALIZACIÓN (Abajo)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 1. VISOR RGB
                        uiState.captureState.let { state ->
                            if (state is CaptureState.Success && state.depthData.originalBitmap != null) {
                                PangolinPreviewCard(
                                    title = "RGB",
                                    bitmap = state.depthData.originalBitmap
                                )
                            }
                        }

                        // 2. VISOR DEPTH
                        uiState.captureState.let { state ->
                            if (state is CaptureState.Success && state.depthData.depthBitmap != null) {
                                PangolinPreviewCard(
                                    title = "Depth",
                                    bitmap = state.depthData.depthBitmap
                                )
                            }
                        }
                    }

                } else {
                    // --- ESTADO SIN PUNTOS ---
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (uiState.captureState) {
                            is CaptureState.Processing -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(Modifier.height(8.dp))
                                    Text("Iniciando motor...", color = Color.Gray)
                                }
                            }
                            is CaptureState.Success -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800))
                                    Spacer(Modifier.height(8.dp))
                                    Text("0 puntos generados", color = Color.Black)
                                    Text(
                                        "Intenta mover la cámara o mejorar la luz.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            else -> {
                                Text("Selecciona una fuente para iniciar", color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // 4. BOTÓN DE DETENER
            if (uiState.currentMode != ImageSourceMode.NONE) {
                Button(
                    onClick = { viewModel.stopCapture() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Detener Simulación")
                }
            }
        }
    }
}

@Composable
fun ConfigCard(
    config: AppConfig,
    connectionState: ConnectionState,
    onConfigChange: (AppConfig) -> Unit,
    onConnect: () -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conexión PixelFormer", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = config.serverHost,
                    onValueChange = { onConfigChange(config.copy(serverHost = it)) },
                    label = { Text("IP Servidor") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = config.serverPort.toString(),
                    onValueChange = {
                        onConfigChange(config.copy(serverPort = it.toIntOrNull() ?: 5000))
                    },
                    label = { Text("Puerto") },
                    modifier = Modifier.width(80.dp)
                )
            }
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = connectionState !is ConnectionState.Connecting
            ) {
                if (connectionState is ConnectionState.Connecting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                val btnText = when(connectionState) {
                    is ConnectionState.Connected -> "✅ Conectado (Reconectar)"
                    is ConnectionState.Error -> "❌ Reintentar"
                    else -> "Conectar"
                }
                Text(btnText)
            }
        }
    }
}

@Composable
fun SourceSelectionCard(
    currentMode: ImageSourceMode,
    onCameraClick: () -> Unit,
    onDatasetClick: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onCameraClick,
            modifier = Modifier.weight(1f),
            enabled = currentMode == ImageSourceMode.NONE
        ) {
            Icon(Icons.Default.CameraAlt, null)
            Spacer(Modifier.width(8.dp))
            Text("Cámara")
        }
        Button(
            onClick = onDatasetClick,
            modifier = Modifier.weight(1f),
            enabled = currentMode == ImageSourceMode.NONE
        ) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(Modifier.width(8.dp))
            Text("Dataset")
        }
    }
}

/**
 * Componente de vista previa estilo Pangolin.
 * Muestra las imágenes RGB y Depth en mini-paneles en la parte inferior.
 */
@Composable
fun PangolinPreviewCard(title: String, bitmap: android.graphics.Bitmap) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(105.dp)
            .border(1.dp, Color.White, RoundedCornerShape(4.dp)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Etiqueta semi-transparente
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}