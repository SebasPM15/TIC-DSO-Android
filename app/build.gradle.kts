plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.mateopilco.ticdso"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mateopilco.ticdso"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Habilitar logging más detallado en debug
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        // Habilitar ViewBinding si lo necesitas en el futuro
        viewBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Excluir duplicados comunes de librerías de red
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // ==================== CORE ANDROID ====================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // ==================== COMPOSE UI ====================
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ==================== ARQUITECTURA (MVVM) ====================
    // ViewModel (Separación de lógica)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Runtime para Compose (Recolección de Flows)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // LiveData (Opcional, pero útil para algunos casos)
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // ==================== COROUTINES (ASINCRONÍA) ====================
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ==================== NETWORKING ====================
    // Retrofit (HTTP Client REST - Plan A v2.0)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp (Base para Retrofit y WebSockets)
    implementation(libs.okhttp)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson (JSON Parser)
    implementation(libs.gson)

    // ==================== CAMERAX (CAPTURA DE IMÁGENES) ====================
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ==================== MANEJO DE IMÁGENES ====================
    // Coil (Carga eficiente de imágenes en Compose)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")

    // DocumentFile (Lectura de carpetas - Dataset)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ==================== PERMISOS ====================
    // Accompanist para manejar permisos en Compose de forma fácil
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // ==================== NAVIGATION (OPCIONAL - PARA MÚLTIPLES PANTALLAS) ====================
    // Si vas a tener varias pantallas (Inicio, Configuración, Visor)
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // ==================== OPENGL / 3D RENDERING ====================
    // Para el visor 3D de la nube de puntos
    // (Android ya incluye OpenGL ES, pero estas son útiles)

    // ==================== UTILIDADES ====================
    // Para logging mejorado
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ==================== TESTING ====================
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}