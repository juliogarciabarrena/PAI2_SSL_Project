@echo off
REM =============================================================================
REM run_performance_test.bat — Comparativa SSL vs Plain (300 conexiones)
REM
REM Prerequisitos:
REM   1. ServidorSSL   corriendo en 8443  →  run_server.bat
REM   2. ServidorPlain corriendo en 8080  →  run_server_plain.bat
REM   3. Haber compilado                  →  mvn package
REM =============================================================================

setlocal enabledelayedexpansion



REM Configurar encoding UTF-8
chcp 65001 >nul
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8

set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set CERTS_DIR=%PROJECT_ROOT%\certs
set PASSWORD=PAI2password
set JAR=%PROJECT_ROOT%\target\performance-test.jar
set LOGS_DIR=logs

REM Crear directorio de logs si no existe
if not exist "%LOGS_DIR%" mkdir "%LOGS_DIR%"

REM Nombre del log con fecha y hora para no sobreescribir ejecuciones anteriores
for /f "tokens=1-3 delims=/" %%a in ("%DATE%") do set FECHA=%%c-%%b-%%a
for /f "tokens=1-2 delims=:." %%a in ("%TIME: =0%") do set HORA=%%a%%b
set LOG=%LOGS_DIR%\performance_test_comparativa_%FECHA%_%HORA%.log

if not exist "%CERTS_DIR%\truststore.jks" (
    echo ERROR: No se encuentra %CERTS_DIR%\truststore.jks
    echo Ejecuta primero: scripts\setup.bat
    pause
    exit /b 1
)

if not exist "%JAR%" (
    echo ERROR: No se encuentra %JAR%
    echo Ejecuta primero: mvn package
    pause
    exit /b 1
)

echo ============================================================
echo   Comparativa Conexiones Simultaneas — SSL vs Plain
echo   SSL   : localhost:8443  ^(run_server.bat debe estar activo^)
echo   Plain : localhost:8080  ^(run_server_plain.bat debe estar activo^)
echo   Clientes: 300
echo   Log: %LOG%
echo ============================================================
echo.

cd /d "%PROJECT_ROOT%"

REM Ejecutar el test mostrando salida en pantalla Y guardando en el log simultáneamente
java -Dfile.encoding=UTF-8 ^
     -Djavax.net.ssl.trustStore="%CERTS_DIR%\truststore.jks" ^
     -Djavax.net.ssl.trustStorePassword="%PASSWORD%" ^
     -jar "%JAR%"

if errorlevel 1 (
    echo.
    echo ERROR: El test termino con errores.
    echo Verifica que ServidorSSL y ServidorPlain estan ejecutandose.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   Test completado
echo   Log guardado en: %LOG%
echo ============================================================
echo.
pause