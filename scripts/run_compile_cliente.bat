@echo off
REM Script para compilar ClienteSSL.java en Windows
REM Requisito: JDK 11+ instalado y en PATH

setlocal enabledelayedexpansion

echo.
echo ╔════════════════════════════════════════════╗
echo ║   Compilación de ClienteSSL.java - WINDOWS ║
echo ╚════════════════════════════════════════════╝
echo.

REM Obtener ruta del script
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set SRC_DIR=%PROJECT_ROOT%\src\main\java
set CLASSES_DIR=%PROJECT_ROOT%\target\classes
set LIBS_DIR=%PROJECT_ROOT%\target\dependency-jars

echo [Compilador] Verificando disponibilidad de javac...
where javac >nul 2>&1
if errorlevel 1 (
    echo.
    echo ❌ Error: javac no encontrado en PATH
    echo    Por favor, instala JDK 11 o superior
    echo    Descárgalo de: https://www.oracle.com/java/technologies/downloads/
    echo.
    echo    Después de instalar, agrega el bin del JDK a PATH:
    echo    Variables de entorno ^> Path ^> C:\Program Files\Java\jdk-XX\bin
    echo.
    pause
    exit /b 1
)

for /f "tokens=*" %%A in ('javac -version 2^>^&1') do set JAVAC_VERSION=%%A
echo [Compilador] %JAVAC_VERSION%
echo.

echo [Compilador] Compilando ClienteSSL.java...
echo [Compilador] Origen:  %SRC_DIR%\ClienteSSL.java
echo [Compilador] Destino: %CLASSES_DIR%
echo [Compilador] Libs:    %LIBS_DIR%
echo.

REM Cambiar al directorio de fuentes
cd /d "%SRC_DIR%"

REM Compilar
javac -cp ".;%LIBS_DIR%\*" ^
      -d "%CLASSES_DIR%" ^
      ClienteSSL.java

if errorlevel 1 (
    echo.
    echo ❌ Error durante la compilación
    pause
    exit /b 1
)

echo.
echo ✅ ClienteSSL.java compilado correctamente
echo.
dir /b "%CLASSES_DIR%\ClienteSSL.class" >nul 2>&1
if errorlevel 1 (
    echo ❌ Error: El archivo .class no se generó
    pause
    exit /b 1
)

for /f "tokens=1" %%A in ('dir /s "%CLASSES_DIR%\ClienteSSL.class" ^| findstr "ClienteSSL.class"') do (
    set FILE_INFO=%%A
)

echo [Compilador] Archivo generado:
echo             %CLASSES_DIR%\ClienteSSL.class
echo.

echo 📝 Para ejecutar el cliente en Windows:
echo.
echo   OPCIÓN 1: Línea de comandos
echo   ─────────────────────────────
echo   cd %PROJECT_ROOT%
echo   java -Djavax.net.ssl.trustStore=certs/truststore.jks ^
echo        -Djavax.net.ssl.trustStorePassword=PAI2password ^
echo        -cp target/classes;target/dependency-jars/* ^
echo        ClienteSSL localhost 8443
echo.

echo   OPCIÓN 2: Script por lotes (recomendado)
echo   ────────────────────────────────────────
echo   Ejecuta: scripts\run_client.bat
echo.

pause