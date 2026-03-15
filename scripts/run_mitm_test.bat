@echo off

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
echo   Test de Protección contra MITM — TLS 1.3
echo   Servidor: localhost:8443  (run_server.bat debe estar activo)
echo ============================================================
echo.

cd /d "%PROJECT_ROOT%"

java -Dfile.encoding=UTF-8 ^
     -Djavax.net.ssl.trustStore="%TRUSTSTORE%" ^
     -Djavax.net.ssl.trustStorePassword="%PASSWORD%" ^
     -cp "%CLASSES_DIR%;%LIBS_DIR%\*" ^
     MITMProtectionTest localhost 8443

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