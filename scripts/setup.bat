@echo off
REM =============================================================================
REM setup.bat — Generación automática de certificados para VPN SSL (Windows)
REM
REM Ejecutar ANTES de arrancar el servidor (Día 1, mañana).
REM Genera keystore.jks (servidor) y truststore.jks (cliente).
REM
REM Uso: scripts\setup.bat
REM =============================================================================

set CERTS_DIR=certs
set KEYSTORE=%CERTS_DIR%\keystore.jks
set TRUSTSTORE=%CERTS_DIR%\truststore.jks
set CERT_FILE=%CERTS_DIR%\server.cer
set PASSWORD=PAI2password
set ALIAS=ssl
set VALIDITY=365
set KEY_SIZE=2048
set DNAME=CN=universidad.local, OU=InSEGUS, O=Universidad de Sevilla, L=Sevilla, ST=Andalucia, C=ES

echo ================================================
echo    Generacion PKI -- VPN SSL TLS 1.3
echo ================================================
echo.

REM Crear directorio certs si no existe
if not exist "%CERTS_DIR%" mkdir "%CERTS_DIR%"

REM Eliminar keystores anteriores si existen
if exist "%KEYSTORE%"   del /f "%KEYSTORE%"
if exist "%TRUSTSTORE%" del /f "%TRUSTSTORE%"
if exist "%CERT_FILE%"  del /f "%CERT_FILE%"

REM -----------------------------------------------------------------------------
REM 1. Generar keystore del servidor
REM -----------------------------------------------------------------------------
echo [1/3] Generando keystore del servidor (RSA %KEY_SIZE%, %VALIDITY% dias)...
keytool -genkeypair ^
    -keystore "%KEYSTORE%" ^
    -alias "%ALIAS%" ^
    -keyalg RSA ^
    -keysize %KEY_SIZE% ^
    -validity %VALIDITY% ^
    -dname "%DNAME%" ^
    -storepass "%PASSWORD%" ^
    -keypass "%PASSWORD%" ^
    -storetype JKS ^
    -noprompt

if errorlevel 1 (
    echo ERROR: Fallo al generar el keystore.
    echo Asegurate de que keytool esta en el PATH ^(JDK instalado^).
    pause
    exit /b 1
)
echo     OK Keystore creado: %KEYSTORE%

REM -----------------------------------------------------------------------------
REM 2. Exportar certificado público
REM -----------------------------------------------------------------------------
echo [2/3] Exportando certificado publico del servidor...
keytool -exportcert ^
    -keystore "%KEYSTORE%" ^
    -alias "%ALIAS%" ^
    -file "%CERT_FILE%" ^
    -storepass "%PASSWORD%" ^
    -noprompt

echo     OK Certificado exportado: %CERT_FILE%

REM -----------------------------------------------------------------------------
REM 3. Crear truststore del cliente
REM -----------------------------------------------------------------------------
echo [3/3] Creando truststore para el cliente...
keytool -importcert ^
    -keystore "%TRUSTSTORE%" ^
    -alias "%ALIAS%" ^
    -file "%CERT_FILE%" ^
    -storepass "%PASSWORD%" ^
    -noprompt

echo     OK Truststore creado: %TRUSTSTORE%

REM -----------------------------------------------------------------------------
REM 4. Verificación
REM -----------------------------------------------------------------------------
echo.
echo -- Contenido del keystore --
keytool -list -keystore "%KEYSTORE%" -storepass "%PASSWORD%"

echo.
echo ================================================
echo   PKI generada correctamente
echo.
echo   Archivos creados:
echo     certs\keystore.jks   -- servidor
echo     certs\truststore.jks -- cliente
echo     certs\server.cer     -- certificado publico
echo.
echo   Contrasena: PAI2password
echo   IMPORTANTE: Compartir keystore.jks con Persona 2
echo ================================================
pause