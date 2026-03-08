@echo off
REM ============================================================================
REM Script para ejecutar test de rendimiento (300 clientes) en Windows
REM Requisito: ServidorSSL debe estar ejecutándose
REM Uso: run_performance_test.bat [num_clientes] [host] [puerto]
REM      Por defecto: 300 localhost 8443
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo ╔═══════════════════════════════════════════════════╗
echo ║     Test de Rendimiento (300 clientes) - WINDOWS  ║
echo ╚═══════════════════════════════════════════════════╝
echo.

REM Obtener parametros
set NUM_CLIENTS=%1
set HOST=%2
set PORT=%3

if "%NUM_CLIENTS%"=="" set NUM_CLIENTS=300
if "%HOST%"=="" set HOST=localhost
if "%PORT%"=="" set PORT=8443

REM Obtener ruta
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set CLASSES_DIR=%PROJECT_ROOT%\target\classes
set LIBS_DIR=%PROJECT_ROOT%\target\dependency-jars
set CERTS_DIR=%PROJECT_ROOT%\certs
set LOGS_DIR=%PROJECT_ROOT%\logs

REM Crear directorio de logs
if not exist "%LOGS_DIR%" mkdir "%LOGS_DIR%"

REM Verificar directorios
if not exist "%CLASSES_DIR%\PerformanceTest.class" (
    echo ❌ ERROR: No se encuentra PerformanceTest.class
    pause
    exit /b 1
)

echo [✓] Verificacion completada
echo     Proyecto:    %PROJECT_ROOT%
echo     Clientes:    %NUM_CLIENTS%
echo     Host:        %HOST%
echo     Puerto:      %PORT%
echo     Log file:    %LOGS_DIR%\performance_test.log
echo.

echo [+] Iniciando test de rendimiento...
echo     Simulando %NUM_CLIENTS% clientes concurrentes
echo     Esto puede tomar 30-60 segundos...
echo.

REM Ejecutar test y guardar log (sin tee, compatible con Windows)
cd /d "%PROJECT_ROOT%"

java -Djavax.net.ssl.trustStore="%CERTS_DIR%\truststore.jks" ^
     -Djavax.net.ssl.trustStorePassword=PAI2password ^
     -cp "%CLASSES_DIR%;%LIBS_DIR%\*" ^
     PerformanceTest %NUM_CLIENTS% %HOST% %PORT% > "%LOGS_DIR%\performance_test.log" 2>&1

if errorlevel 1 (
    echo.
    echo ❌ Error durante el test
    echo    Verifica que ServidorSSL esta ejecutándose
    pause
    exit /b 1
)

echo.
echo ═══════════════════════════════════════════════════════════════
echo ✅ Test completado!
echo.
echo Resultados guardados en: %LOGS_DIR%\performance_test.log
echo ═══════════════════════════════════════════════════════════════
echo.

REM Mostrar resultados en pantalla
type "%LOGS_DIR%\performance_test.log"

pause