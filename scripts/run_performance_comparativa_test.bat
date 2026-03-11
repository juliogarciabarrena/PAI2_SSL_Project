@echo off
REM =============================================================================
REM run_performance_test.bat — Comparativa SSL vs Plain (300 conexiones)
REM
REM Prerequisitos:
REM   1. ServidorSSL   corriendo en 8443  →  run_server.bat
REM   2. ServidorPlain corriendo en 8080  →  run_server_plain.bat
REM   3. Haber compilado                  →  mvn package
REM =============================================================================

set TRUSTSTORE=certs\truststore.jks
set PASSWORD=PAI2password
set JAR=target\performance-test.jar

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
echo   Comparativa Conexiones Simultaneas — SSL vs Plain
echo   SSL   : localhost:8443  ^(run_server.bat debe estar activo^)
echo   Plain : localhost:8080  ^(run_server_plain.bat debe estar activo^)
echo   Clientes: 300
echo ============================================================
echo.

java ^
    -Djavax.net.ssl.trustStore="%TRUSTSTORE%" ^
    -Djavax.net.ssl.trustStorePassword="%PASSWORD%" ^
    -jar "%JAR%"

pause