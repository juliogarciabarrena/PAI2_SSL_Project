@echo off
REM ============================================================================
REM Script para ejecutar ClienteSSL en Windows
REM Requisito: ServidorSSL debe estar ejecutándose en otra terminal
REM Uso: run_client.bat [host] [puerto]
REM      Por defecto: localhost 8443
REM ============================================================================

setlocal enabledelayedexpansion


REM Obtener parametros (host y puerto)
set HOST=%1
set PORT=%2

if "%HOST%"=="" (
    set HOST=localhost
)
if "%PORT%"=="" (
    set PORT=8443
)

REM Obtener ruta
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set CLASSES_DIR=%PROJECT_ROOT%\target\classes
set LIBS_DIR=%PROJECT_ROOT%\target\dependency-jars
set CERTS_DIR=%PROJECT_ROOT%\certs

REM Verificar directorios
if not exist "%CLASSES_DIR%\ClienteSSL.class" (
    echo ❌ ERROR: No se encuentra ClienteSSL.class
    echo           Necesitas compilar primero con: compile_cliente.bat
    pause
    exit /b 1
)

if not exist "%CERTS_DIR%\truststore.jks" (
    echo ❌ ERROR: No se encuentra certs\truststore.jks
    pause
    exit /b 1
)

echo [✓] Verificacion completada
echo     Proyecto: %PROJECT_ROOT%
echo     Host:     %HOST%
echo     Puerto:   %PORT%
echo.

echo [+] Conectando al servidor...
echo     Protocolo: TLS 1.3
echo     Verifica que ServidorSSL este ejecutándose
echo.

REM Ejecutar cliente
cd /d "%PROJECT_ROOT%"

java -Djavax.net.ssl.trustStore="%CERTS_DIR%\truststore.jks" ^
     -Djavax.net.ssl.trustStorePassword=PAI2password ^
     -cp "%CLASSES_DIR%;%LIBS_DIR%\*" ^
     ClienteSSL %HOST% %PORT%

if errorlevel 1 (
    echo.
    echo ❌ Error al conectar
    echo    - Verifica que ServidorSSL esta ejecutándose
    echo    - Host correcto: %HOST%
    echo    - Puerto correcto: %PORT%
    pause
    exit /b 1
)

pause