@echo off
REM =============================================================================
REM setup.bat — Generación automática de certificados para VPN SSL (Windows)
REM
REM Este script realiza un setup completo:
REM   1. mvn clean       — Limpia artefactos previos
REM   2. mvn install     — Descarga e instala dependencias
REM   3. Genera PKI      — Crea certificados (keystore/truststore)
REM   4. mvn compile     — Compila el código fuente
REM   5. mvn package     — Empaqueta en JAR
REM
REM Ejecutar ANTES de arrancar el servidor (Día 1, mañana).
REM Genera keystore.jks (servidor) y truststore.jks (cliente).
REM
REM Uso: scripts\setup.bat
REM =============================================================================

@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

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
echo    SETUP COMPLETO — VPN SSL TLS 1.3
echo    Maven + PKI + Compilacion
echo ================================================
echo.

REM Verificar que Maven está instalado
echo [VERIFICACION] Comprobando Maven...
where mvn >nul 2>&1
if errorlevel 1 (
    echo.
    echo [ERROR] Maven no está instalado o no está en PATH
    echo.
    echo Soluciones:
    echo   1. Descarga Maven desde: https://maven.apache.org/download.cgi
    echo   2. O instálalo con: choco install maven
    echo   3. O con scoop: scoop install maven
    echo.
    echo Después reinicia esta ventana de comandos.
    echo.
    pause
    exit /b 1
)
echo [OK] Maven encontrado
echo.

REM Obtener ruta del proyecto
set PROJECT_ROOT=%~dp0..
echo [CONFIGURACION]
echo Proyecto: %PROJECT_ROOT%
echo.

REM ============================================================================
REM FASE 1: Maven Clean
REM ============================================================================
echo [1/5] Ejecutando: mvn clean
echo       Limpiando artefactos previos...
echo.

cd /d "%PROJECT_ROOT%"
call mvn clean -q

if errorlevel 1 (
    echo.
    echo [ERROR] mvn clean falló
    pause
    exit /b 1
)
echo [OK] mvn clean completado
echo.

REM ============================================================================
REM FASE 2: Maven Install
REM ============================================================================
echo [2/5] Ejecutando: mvn install
echo       Descargando e instalando dependencias...
echo.

cd /d "%PROJECT_ROOT%"
call mvn install -DskipTests -q

if errorlevel 1 (
    echo.
    echo [ERROR] mvn install falló
    echo.
    echo Verifica que pom.xml existe y es válido
    echo.
    pause
    exit /b 1
)
echo [OK] mvn install completado
echo.

REM ============================================================================
REM FASE 3: Crear directorio de certificados
REM ============================================================================
echo [3/5] Generando PKI (Infraestructura de Clave Pública)
echo.

REM Crear directorio certs si no existe
if not exist "%CERTS_DIR%" (
    echo       Creando directorio: %CERTS_DIR%
    mkdir "%CERTS_DIR%"
)

REM Eliminar keystores anteriores si existen
if exist "%KEYSTORE%" (
    echo       Eliminando keystore anterior...
    del /f /q "%KEYSTORE%"
)
if exist "%TRUSTSTORE%" (
    echo       Eliminando truststore anterior...
    del /f /q "%TRUSTSTORE%"
)
if exist "%CERT_FILE%" (
    echo       Eliminando certificado anterior...
    del /f /q "%CERT_FILE%"
)

echo.
echo [3a/5] Generando keystore del servidor (RSA %KEY_SIZE%, %VALIDITY% dias)...

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

if not exist "%KEYSTORE%" (
    echo ❌ ERROR
    pause
)
echo [OK] Keystore creado: %KEYSTORE%
echo.

echo [3b/5] Exportando certificado público del servidor...
keytool -exportcert ^
    -keystore "%KEYSTORE%" ^
    -alias "%ALIAS%" ^
    -file "%CERT_FILE%" ^
    -storepass "%PASSWORD%" ^
    -noprompt

echo [OK] Certificado exportado: %CERT_FILE%
echo.

echo [3c/5] Creando truststore para el cliente...
keytool -importcert ^
    -keystore "%TRUSTSTORE%" ^
    -alias "%ALIAS%" ^
    -file "%CERT_FILE%" ^
    -storepass "%PASSWORD%" ^
    -noprompt

echo [OK] Truststore creado: %TRUSTSTORE%
echo.

REM Verificar keystores
echo [VERIFICACION PKI]
echo Contenido del keystore:
keytool -list -keystore "%KEYSTORE%" -storepass "%PASSWORD%"
echo.

REM ============================================================================
REM FASE 4: Maven Compile
REM ============================================================================
echo [4/5] Ejecutando: mvn compile
echo       Compilando código fuente...
echo.

cd /d "%PROJECT_ROOT%"
call mvn compile -q

if errorlevel 1 (
    echo.
    echo [ERROR] mvn compile falló
    echo.
    echo Verifica que:
    echo   - El código Java es válido
    echo   - Las dependencias se descargaron correctamente
    echo.
    pause
    exit /b 1
)
echo [OK] mvn compile completado
echo.

REM ============================================================================
REM FASE 5: Maven Package
REM ============================================================================
echo [5/5] Ejecutando: mvn package
echo       Empaquetando aplicación...
echo.

cd /d "%PROJECT_ROOT%"
call mvn package -DskipTests -q

if errorlevel 1 (
    echo.
    echo [ERROR] mvn package falló
    echo.
    pause
    exit /b 1
)
echo [OK] mvn package completado
echo.

REM ============================================================================
REM RESUMEN FINAL
REM ============================================================================
echo ================================================
echo    SETUP COMPLETADO EXITOSAMENTE
echo ================================================
echo.
echo RESUMEN:
echo.
echo [Maven]
echo   mvn clean      — Limpieza completada
echo   mvn install    — Dependencias instaladas
echo   mvn compile    — Código compilado
echo   mvn package    — Aplicación empaquetada
echo.
echo [PKI]
echo   keystore.jks   — Servidor (privado)
echo   truststore.jks — Cliente
echo   server.cer     — Certificado público
echo.
echo [Archivos]
echo   target/classes              — Clases compiladas
echo   target/dependency-jars      — Dependencias
echo   certs                       — Certificados PKI
echo.
echo [Pasos Siguientes]
echo   1. Terminal 1: scripts\run_server_simple.bat
echo   2. Terminal 2: scripts\run_client_simple.bat
echo   3. Terminal 3: scripts\run_performance_test_simple.bat
echo.
echo [Información PKI]
echo   Contraseña: PAI2password
echo   Alias: ssl
echo   Validez: %VALIDITY% días
echo.
echo ================================================
echo.

pause