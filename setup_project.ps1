# ============================================================================
# SCRIPT DE CONFIGURACIÓN COMPLETA DEL PROYECTO TIC-DSO
# ============================================================================
# Ejecutar desde: D:\Tesis\App_android\
# Comando: .\setup_project.ps1

Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║      CONFIGURACIÓN PROYECTO TIC-DSO - PIXELFORMER APP       ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

$ErrorActionPreference = "Stop"

# ============================================================================
# PARTE 1: CREAR ESTRUCTURA DE CARPETAS (KOTLIN)
# ============================================================================
Write-Host "┌─────────────────────────────────────────────────────┐" -ForegroundColor Green
Write-Host "│ PASO 1: Creando estructura de carpetas Kotlin      │" -ForegroundColor Green
Write-Host "└─────────────────────────────────────────────────────┘" -ForegroundColor Green
Write-Host ""

$basePackage = "app\src\main\java\com\mateopilco\ticdso"

$folders = @(
    # Domain Layer
    "$basePackage\domain\model",
    "$basePackage\domain\source",
    "$basePackage\domain\repository",
    
    # Data Layer
    "$basePackage\data\network",
    "$basePackage\data\network\dto",
    "$basePackage\data\repository",
    "$basePackage\data\source",
    
    # Presentation Layer
    "$basePackage\presentation\viewmodel",
    "$basePackage\presentation\ui\screen",
    "$basePackage\presentation\ui\component",
    "$basePackage\presentation\ui\theme",
    "$basePackage\presentation\navigation",
    
    # Utilities
    "$basePackage\util"
)

$createdFolders = 0
$existingFolders = 0

foreach ($folder in $folders) {
    if (!(Test-Path $folder)) {
        New-Item -Path $folder -ItemType Directory -Force | Out-Null
        Write-Host "  ✓ Creada: $folder" -ForegroundColor Cyan
        $createdFolders++
    } else {
        Write-Host "  ○ Ya existe: $folder" -ForegroundColor DarkGray
        $existingFolders++
    }
}

Write-Host ""
Write-Host "  📊 Resumen:" -ForegroundColor Yellow
Write-Host "     - Carpetas creadas: $createdFolders" -ForegroundColor Green
Write-Host "     - Carpetas existentes: $existingFolders" -ForegroundColor Gray
Write-Host ""

# ============================================================================
# PARTE 2: CREAR ARCHIVOS XML DE RECURSOS
# ============================================================================
Write-Host "┌─────────────────────────────────────────────────────┐" -ForegroundColor Green
Write-Host "│ PASO 2: Creando archivos XML de configuración      │" -ForegroundColor Green
Write-Host "└─────────────────────────────────────────────────────┘" -ForegroundColor Green
Write-Host ""

$xmlFolder = "app\src\main\res\xml"

# Crear carpeta xml si no existe
if (!(Test-Path $xmlFolder)) {
    New-Item -Path $xmlFolder -ItemType Directory -Force | Out-Null
    Write-Host "  ✓ Carpeta xml/ creada" -ForegroundColor Cyan
}

# --- 2.1: network_security_config.xml ---
$networkSecurityConfig = @"
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Configuración para Debug (permite HTTP a servidor local) -->
    <domain-config cleartextTrafficPermitted="true">
        <!-- Tu servidor Flask en la VM -->
        <domain includeSubdomains="true">192.168.3.36</domain>
        <domain includeSubdomains="true">192.168.1.0</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>  <!-- Emulador -->
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
"@

Set-Content -Path "$xmlFolder\network_security_config.xml" -Value $networkSecurityConfig -Encoding UTF8
Write-Host "  ✓ network_security_config.xml creado" -ForegroundColor Cyan

# --- 2.2: file_paths.xml ---
$filePaths = @"
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="cache" path="." />
    <external-path name="external_files" path="." />
    <files-path name="internal_files" path="." />
    <external-files-path name="external_app_files" path="." />
    <external-path name="downloads" path="Download/" />
</paths>
"@

Set-Content -Path "$xmlFolder\file_paths.xml" -Value $filePaths -Encoding UTF8
Write-Host "  ✓ file_paths.xml creado" -ForegroundColor Cyan

# --- 2.3: backup_rules.xml ---
if (!(Test-Path "$xmlFolder\backup_rules.xml")) {
    $backupRules = @"
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="temp_prefs.xml" />
    <exclude domain="database" path="cache.db" />
</full-backup-content>
"@
    Set-Content -Path "$xmlFolder\backup_rules.xml" -Value $backupRules -Encoding UTF8
    Write-Host "  ✓ backup_rules.xml creado" -ForegroundColor Cyan
} else {
    Write-Host "  ○ backup_rules.xml ya existe" -ForegroundColor DarkGray
}

# --- 2.4: data_extraction_rules.xml ---
if (!(Test-Path "$xmlFolder\data_extraction_rules.xml")) {
    $dataExtractionRules = @"
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="sharedpref" path="user_settings.xml"/>
    </cloud-backup>
    <device-transfer>
        <include domain="sharedpref" path="user_settings.xml"/>
    </device-transfer>
</data-extraction-rules>
"@
    Set-Content -Path "$xmlFolder\data_extraction_rules.xml" -Value $dataExtractionRules -Encoding UTF8
    Write-Host "  ✓ data_extraction_rules.xml creado" -ForegroundColor Cyan
} else {
    Write-Host "  ○ data_extraction_rules.xml ya existe" -ForegroundColor DarkGray
}

Write-Host ""

# ============================================================================
# PARTE 3: RESUMEN Y VERIFICACIÓN
# ============================================================================
Write-Host "┌─────────────────────────────────────────────────────┐" -ForegroundColor Green
Write-Host "│ PASO 3: Verificación de archivos                   │" -ForegroundColor Green
Write-Host "└─────────────────────────────────────────────────────┘" -ForegroundColor Green
Write-Host ""

Write-Host "  📁 Archivos XML creados:" -ForegroundColor Yellow
Get-ChildItem $xmlFolder -Filter "*.xml" | ForEach-Object {
    Write-Host "     ✓ $($_.Name)" -ForegroundColor Green
}

Write-Host ""
Write-Host "  📦 Estructura de carpetas principal:" -ForegroundColor Yellow
Get-ChildItem $basePackage -Directory -Recurse -Depth 1 | Select-Object -First 15 | ForEach-Object {
    $relativePath = $_.FullName.Replace((Get-Location).Path + "\", "")
    Write-Host "     📂 $relativePath" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║                  ✅ CONFIGURACIÓN COMPLETA                    ║" -ForegroundColor Green
Write-Host "╚═══════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""

# ============================================================================
# PARTE 4: PRÓXIMOS PASOS
# ============================================================================
Write-Host "📝 PRÓXIMOS PASOS:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  1️⃣  Abre Android Studio" -ForegroundColor White
Write-Host "     - File → Open → D:\Tesis\App_android" -ForegroundColor Gray
Write-Host ""
Write-Host "  2️⃣  Actualiza build.gradle.kts" -ForegroundColor White
Write-Host "     - app/build.gradle.kts → Reemplaza con el del artifact" -ForegroundColor Gray
Write-Host "     - Clic en 'Sync Now'" -ForegroundColor Gray
Write-Host ""
Write-Host "  3️⃣  Actualiza AndroidManifest.xml" -ForegroundColor White
Write-Host "     - app/src/main/AndroidManifest.xml → Reemplaza con el del artifact" -ForegroundColor Gray
Write-Host ""
Write-Host "  4️⃣  Clean & Rebuild" -ForegroundColor White
Write-Host "     - Build → Clean Project" -ForegroundColor Gray
Write-Host "     - Build → Rebuild Project" -ForegroundColor Gray
Write-Host ""
Write-Host "  5️⃣  Verifica que no haya errores" -ForegroundColor White
Write-Host "     - Pestaña 'Build' debe estar sin errores rojos" -ForegroundColor Gray
Write-Host ""

Write-Host "💡 TIP: Si ves errores de 'Unresolved reference', es normal." -ForegroundColor Cyan
Write-Host "   Solo indica que aún no has creado las clases Kotlin." -ForegroundColor Cyan
Write-Host "   Los archivos XML están listos para usar." -ForegroundColor Cyan
Write-Host ""

# ============================================================================
# PARTE 5: CREAR CHECKLIST
# ============================================================================
$checklistPath = "CHECKLIST_MIGRACION.md"
$checklistContent = @"
# ✅ Checklist de Migración TIC-DSO

## Fase 1: Preparación (Completada)
- [x] Estructura de carpetas creada
- [x] Archivos XML de configuración creados
- [ ] build.gradle.kts actualizado
- [ ] AndroidManifest.xml actualizado
- [ ] Sync exitoso sin errores

## Fase 2: Domain Layer
- [ ] domain/model/DepthData.kt creado
- [ ] domain/source/ImageSource.kt creado
- [ ] domain/repository/DepthRepository.kt creado

## Fase 3: Data Layer
- [ ] data/network/PixelFormerApi.kt creado
- [ ] data/network/RetrofitClient.kt creado
- [ ] data/network/dto/* creados
- [ ] data/repository/DepthRepositoryImpl.kt creado

## Fase 4: Utils
- [ ] util/BitmapUtils.kt creado
- [ ] util/PermissionUtils.kt creado
- [ ] util/NetworkUtils.kt creado

## Fase 5: Presentation
- [ ] presentation/viewmodel/MainViewModel.kt refactorizado
- [ ] presentation/ui/screen/HomeScreen.kt creado
- [ ] MainActivity.kt actualizado

## Test Final
- [ ] App compila sin errores
- [ ] App se ejecuta en dispositivo/emulador
- [ ] Conexión con servidor Flask funciona
"@

Set-Content -Path $checklistPath -Value $checklistContent -Encoding UTF8
Write-Host "📋 Checklist de migración creado: $checklistPath" -ForegroundColor Magenta
Write-Host ""

Write-Host "🚀 ¡Listo para comenzar el desarrollo!" -ForegroundColor Green
Write-Host ""