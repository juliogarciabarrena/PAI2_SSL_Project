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
echo ============================================================
echo.

java ^
    -Djavax.net.ssl.trustStore="%TRUSTSTORE%" ^
    -Djavax.net.ssl.trustStorePassword="%PASSWORD%" ^
    -jar "%JAR%"

pause