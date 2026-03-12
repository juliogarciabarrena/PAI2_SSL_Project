@echo off
REM =============================================================================
REM run_cipher_suite_test.bat — Prueba cada cipher suite contra el servidor SSL
REM
REM Prerequisitos:
REM   1. ServidorSSL corriendo en 8443  →  run_server.bat
REM   2. Haber compilado                →  mvn package
REM =============================================================================

set TRUSTSTORE=certs\truststore.jks
set PASSWORD=PAI2password
set JAR=target\cipher-suite-test.jar
set LOGS_DIR=logs

REM Crear directorio de logs si no existe
if not exist "%LOGS_DIR%" mkdir "%LOGS_DIR%"

REM Nombre del log con fecha y hora para no sobreescribir ejecuciones anteriores
for /f "tokens=1-3 delims=/" %%a in ("%DATE%") do set FECHA=%%c-%%b-%%a
for /f "tokens=1-2 delims=:." %%a in ("%TIME: =0%") do set HORA=%%a%%b
set LOG=%LOGS_DIR%\cipher_suite_test_%FECHA%_%HORA%.log

if not exist "%TRUSTSTORE%" (
    echo ERROR: No se encuentra %TRUSTSTORE%
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
echo   Test de Cipher Suites — VPN SSL TLS 1.3
echo   Servidor : localhost:8443
echo   Leyendo cipher suites desde: config.properties
echo   Log: %LOG%
echo ============================================================
echo.

powershell -Command ^
    "java " ^
    "'-Djavax.net.ssl.trustStore=%TRUSTSTORE%' " ^
    "'-Djavax.net.ssl.trustStorePassword=%PASSWORD%' " ^
    "'-jar' '%JAR%' " ^
    "2>&1 | Tee-Object -FilePath '%LOG%'"

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
echo   Log guardado en: %LOG%
echo ============================================================
echo.
pause