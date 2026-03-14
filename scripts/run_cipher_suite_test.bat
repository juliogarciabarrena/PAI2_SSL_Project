@echo off
REM =============================================================================
REM run_cipher_suite_test.bat — Prueba cada cipher suite contra el servidor SSL
REM
REM Requisitos:
REM   1. ServidorSSL corriendo en localhost:8443  →  run_server_simple.bat
REM   2. Archivos compilados en target\classes\
REM =============================================================================

setlocal enabledelayedexpansion

REM Configurar encoding UTF-8
chcp 65001 >nul
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8

set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set CERTS_DIR=%PROJECT_ROOT%\certs
set TRUSTSTORE=%CERTS_DIR%\truststore.jks
set PASSWORD=PAI2password
set CLASSES_DIR=%PROJECT_ROOT%\target\classes
set LIBS_DIR=%PROJECT_ROOT%\target\dependency-jars

if not exist "%TRUSTSTORE%" (
    echo ERROR: No se encuentra %TRUSTSTORE%
    echo Ejecuta primero: scripts\setup.bat
    pause
    exit /b 1
)

if not exist "%CLASSES_DIR%" (
    echo ERROR: No se encuentran clases compiladas en %CLASSES_DIR%
    echo Ejecuta primero: scripts\compile_cliente.bat
    pause
    exit /b 1
)

echo ============================================================
echo   Test de Cipher Suites — VPN SSL TLS 1.3
echo   Servidor: localhost:8443  (run_server_simple.bat debe estar activo)
echo   Protocolo: TLSv1.3
echo ============================================================
echo.

cd /d "%PROJECT_ROOT%"

REM Ejecutar el test
java -Dfile.encoding=UTF-8 ^
     -Djavax.net.ssl.trustStore="%TRUSTSTORE%" ^
     -Djavax.net.ssl.trustStorePassword="%PASSWORD%" ^
     -cp "%CLASSES_DIR%;%LIBS_DIR%\*" ^
     CipherSuiteTest localhost 8443

if errorlevel 1 (
    echo.
    echo ERROR: El test termino con errores.
    echo Verifica que ServidorSSL esta ejecutandose.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   Test completado
echo ============================================================
echo.
pause