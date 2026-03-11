@echo off
REM =============================================================================
REM run_server.bat — Arranca el servidor VPN SSL (Windows)
REM
REM Prerequisitos:
REM   1. Haber ejecutado: scripts\setup.bat
REM   2. Haber compilado: mvn package
REM =============================================================================

set KEYSTORE=certs\keystore.jks
set PASSWORD=PAI2password
set JAR=target\servidor-ssl.jar
set DB=vpn_server.db

if not exist "%KEYSTORE%" (
    echo ERROR: No se encuentra %KEYSTORE%
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

REM -----------------------------------------------------------------------------
REM Limpiar base de datos anterior
REM -----------------------------------------------------------------------------
if exist "%DB%" (
    echo [Setup] Eliminando base de datos anterior: %DB%
    del /f "%DB%"
    echo [Setup] Base de datos eliminada. Se creara una nueva al arrancar.
) else (
    echo [Setup] No existe base de datos previa. Se creara una nueva.
)
echo.

echo Arrancando servidor VPN SSL...
echo   Keystore : %KEYSTORE%
echo   Puerto   : 8443
echo   Protocolo: TLS 1.3
echo   Base de datos: %DB%
echo.

java ^
    -Djavax.net.ssl.keyStore="%KEYSTORE%" ^
    -Djavax.net.ssl.keyStorePassword="%PASSWORD%" ^
    -jar "%JAR%"

pause