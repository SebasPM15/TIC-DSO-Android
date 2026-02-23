# TIC-DSO — Reconstrucción 3D Monocular en Android

> Proyecto de Tesis de Ingeniería de Software  
> Autor: **Mateo Sebastián Pilco Pérez**  
> Versión: `2.0` (Post-Optimización DSO) — Diciembre 2025

---

## 📌 Descripción General

**TIC-DSO** es una aplicación Android que implementa un pipeline de **reconstrucción 3D monocular en tiempo real**, integrando:

- **PixelFormer** (modelo de estimación de profundidad monocular, Agarwal et al., WACV 2023) corriendo en un servidor Flask remoto.
- **Grid-based Pixel Selector** adaptado de DSO (*Direct Sparse Odometry*, Engel et al., IEEE TPAMI 2018).
- Visualización 3D interactiva estilo **Pangolin** implementada con Jetpack Compose Canvas.

La app permite reconstruir escenas 3D a partir de una única cámara (monocular), sin necesidad de sensores de profundidad dedicados (LiDAR, ToF, etc.).

---

## 🏗️ Arquitectura del Sistema

El proyecto sigue **Clean Architecture** con el patrón **MVVM**, dividido en tres capas:

```
app/src/main/java/com/mateopilco/ticdso/
│
├── data/                          # Capa de Datos
│   ├── network/                   # Retrofit + DTO
│   ├── repository/                # Implementación del repositorio
│   └── source/                    # Fuentes de imagen (Cámara / Dataset)
│
├── domain/                        # Capa de Negocio
│   ├── model/                     # Modelos de dominio
│   ├── repository/                # Interfaces del repositorio
│   └── source/                    # Interfaz Strategy (ImageSource)
│
├── presentation/                  # Capa de Presentación
│   ├── ui/component/              # PointCloudViewer (renderizador 3D)
│   ├── ui/screen/                 # HomeScreen
│   └── viewmodel/                 # MainViewModel + MainUiState
│
├── util/                          # Utilidades
│   ├── PointsGenerator.kt         # ⚡⚡ CORE — Generación de nube de puntos
│   ├── MathUtils.kt               # Álgebra 3D (matrices, poses)
│   ├── BitmapUtils.kt             # Conversiones de imagen
│   └── Benchmarker.kt             # Medición de rendimiento
│
└── MainActivity.kt
```

---

## 🔄 Pipeline de Reconstrucción 3D

```
1. CAPTURA (ImageSource)
   ├─ CameraImageSource  →  Frames YUV → RGB vía CameraX, pose = Identidad
   └─ DatasetImageSource →  Lee images/ + groundtruthSync.txt, pose = T_wc

2. ENVÍO (DepthRepository)
   └─ POST http://{server}:5000/api/v1/predict  (imagen JPEG comprimida al 90%)

3. INFERENCIA (Servidor Flask — PixelFormer)
   └─ Retorna mapa de profundidad normalizado

4. GENERACIÓN 3D (PointsGenerator)
   ├─ Grid-based Pixel Selector (bloques 32×32, máx 2 000 puntos/frame)
   ├─ Gradientes Sobel → selección del píxel con mayor gradiente por bloque
   └─ Back-projection a 3D con modelo Pinhole inverso

5. GESTIÓN (MainViewModel)
   ├─ Keyframe cada 10 frames
   ├─ Sliding Window (máx 200 000 puntos globales, FIFO)
   └─ Trayectoria de cámara guardada por pose

6. RENDERIZADO (PointCloudViewer)
   ├─ Batch rendering con drawPoints()
   ├─ View Frustum Culling
   ├─ Proyección perspectiva 3D → 2D
   └─ Trayectoria + marcadores de keyframe estilo Pangolin
```

---

## ⚡ Optimizaciones Clave (v2.0)

### Grid-based Pixel Selector (basado en DSO `PixelSelector2.cpp`)

Reemplaza la iteración densa sobre toda la imagen por una selección por bloques, garantizando distribución espacial uniforme y control estricto de densidad.

| Constante | Valor | Descripción |
|-----------|-------|-------------|
| `BLOCK_SIZE` | 32 px | Tamaño de cuadrícula (estándar DSO) |
| `MAX_POINTS_PER_FRAME` | 2 000 | Límite de puntos por frame |
| `GRADIENT_SQ_THRESHOLD` | 50 | Umbral de gradiente² |
| `MIN_DEPTH` | 0.1 m | Profundidad mínima válida |
| `MAX_DEPTH` | 9.5 m | Profundidad máxima válida |

### Resultados de Rendimiento

| Métrica | Antes | Después | Mejora |
|---------|-------|---------|--------|
| Puntos/frame | 21 682 | ~2 000 | ✅ −90.8% |
| Tiempo proceso 3D | ~800 ms | ~120 ms | ✅ −85% |
| FPS de captura | 0.1 FPS | 1–2 FPS | ✅ +1 000% |
| Uso de CPU (render) | 80–90% | 25–30% | ✅ −66% |
| Uso de RAM | ~1.2 GB (crash) | ~450 MB (estable) | ✅ −62.5% |

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
|-----------|-----------|
| Lenguaje | Kotlin |
| UI | Jetpack Compose |
| Estado | StateFlow + MVVM |
| Red | Retrofit 2 + OkHttp |
| Cámara | CameraX |
| Renderizado 3D | Compose Canvas |
| Backend | Python + Flask + PixelFormer |
| Arquitectura | Clean Architecture |

---

## 🚀 Configuración y Uso

### Requisitos previos

- Android Studio Hedgehog o superior
- Dispositivo/emulador con Android 8.0+ (API 26+)
- Servidor Flask con PixelFormer corriendo y accesible en la red local

### Configuración del servidor

En `RetrofitClient.kt`, configurar la IP del servidor:

```kotlin
private const val BASE_URL = "http://192.168.x.x:5000/"
```

Asegurarse de que `network_security_config.xml` permite tráfico cleartext a esa IP:

```xml
<domain-config cleartextTrafficPermitted="true">
    <domain>192.168.x.x</domain>
</domain-config>
```

### Modos de operación

- **Modo Cámara:** Captura frames en tiempo real desde la cámara trasera del dispositivo. La pose es identidad (sin tracking de movimiento real aún).
- **Modo Dataset:** Lee imágenes y poses ground-truth desde almacenamiento local, útil para evaluación reproducible.

---

## 🧪 Casos de Prueba y Edge Cases

| Escenario | Comportamiento Esperado |
|-----------|------------------------|
| Servidor inaccesible | `ConnectionState.Error` → UI muestra mensaje de error, sin crash |
| Mapa de profundidad nulo | Retorna lista vacía, continúa ejecución |
| Profundidad fuera de rango | Píxel descartado silenciosamente |
| Sin calibración de dataset | Fallback: `fx=256, fy=254.4, cx=319.5, cy=239.5` |
| Memoria insuficiente | Sliding window actúa como FIFO; si persiste OOM, el job se cancela |

---

## 🔮 Deuda Técnica y Trabajo Futuro

### Pendiente inmediato
- [ ] Tests unitarios: `PointsGenerator.selectPixelsByGrid()`, `MathUtils`, sliding window en `MainViewModel`
- [ ] WebSocket para streaming continuo (reducir latencia de red)
- [ ] Integración de ARCore para tracking de pose real 6DOF

### Mejoras a mediano plazo
- [ ] Migrar renderizado de Canvas a **OpenGL ES 3.0** (objetivo: 60 FPS con 1M puntos)
- [ ] Convertir PixelFormer a **TensorFlow Lite** para inferencia on-device (<500ms)
- [ ] Exportar nube de puntos a formato **PLY/PCD** y trayectoria en formato **TUM**
- [ ] Implementar Z-buffer para oclusión correcta

---

## 📚 Referencias

1. Engel, J., Koltun, V., & Cremers, D. (2018). *Direct Sparse Odometry.* IEEE TPAMI. DOI: [10.1109/TPAMI.2017.2658577](https://doi.org/10.1109/TPAMI.2017.2658577)
2. Agarwal et al. (2023). *Attention Attention Everywhere: Monocular Depth Prediction with Skip Attention (PixelFormer).* WACV 2023.
3. Lovegrove, S. et al. *Pangolin: Lightweight Portable Rapid Prototyping Visualisation Library.* [github.com/stevenlovegrove/Pangolin](https://github.com/stevenlovegrove/Pangolin)
4. Código fuente de referencia DSO: `PixelSelector2.cpp`, `PangolinDSOViewer.cpp`, `settings.cpp`

---

## 🎓 Contribución a la Tesis

Este proyecto demuestra de forma práctica:

1. **Adaptación de algoritmos científicos** al entorno móvil Android (PixelSelector2 de DSO).
2. **Arquitectura Limpia en producción** con MVVM, Strategy pattern, Repository pattern y StateFlow.
3. **Optimización de recursos limitados**: ejecutar SLAM ligero en hardware móvil sin GPU de escritorio.
4. **Visualización científica móvil**: replicación de Pangolin/DSO en Jetpack Compose.

---

*Documento generado como referencia técnica para estudios futuros sobre reconstrucción 3D monocular en dispositivos móviles.*