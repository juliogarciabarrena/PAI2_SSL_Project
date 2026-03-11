@echo off
REM =============================================================================
REM run_server_plain.bat — Arranca el servidor TCP sin cifrado (Windows)
REM
REM Propósito:
REM   Servidor de línea base para medir el overhead de TLS 1.3.
REM   Ejecutar junto a run_server.bat para la comparativa de rendimiento.
REM
REM   ⚠️  NO usar en producción. Las conexiones no están cifradas.
REM
REM Prerequisitos:
REM   1. Haber compilado: mvn package
REM
REM Puertos:
REM   ServidorSSL   → 8443  (run_server.bat)
REM   ServidorPlain → 8080  (este script)
REM =============================================================================

set JAR=target\servidor-plain.jar
set DB=vpn_server.db

if not exist "%JAR%" (
    echo ERROR: No se encuentra %JAR%
    echo Ejecuta primero: mvn package
    pause
    exit /b 1
)


REM -----------------------------------------------------------------------------
REM Liberar puerto 8080 si está ocupado
REM -----------------------------------------------------------------------------
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
    echo [Setup] Liberando puerto 8080 ^(PID %%a^)...
    taskkill /PID %%a /F >nul 2>&1
)

echo ============================================================
echo   Servidor Plain ^(sin cifrado^) — Solo para pruebas
echo   Puerto   : 8080
echo   Protocolo: TCP sin cifrar
echo   ⚠️  NO usar en produccion
echo ============================================================
echo.

java -jar "%JAR%"

pause