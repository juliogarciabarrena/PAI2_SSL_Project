@echo off
REM =============================================================================
REM setup.bat - Generacion de certificados y compilacion para VPN SSL (Windows)
REM
REM Este script realiza un setup completo:
REM   1. mvn clean       - Limpia artefactos previos
REM   2. mvn install     - Descarga e instala dependencias
REM   3. Genera PKI      - Crea certificados (keystore/truststore)
REM   4. mvn compile     - Compila el codigo fuente
REM   5. mvn package     - Empaqueta en JAR
REM
REM Uso: scripts\setup.bat
REM =============================================================================

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
echo    SETUP COMPLETO - VPN SSL TLS 1.3
echo    Maven + PKI + Compilacion
echo ================================================
echo.

REM Verificar que Maven esta instalado
echo [VERIFICACION] Comprobando Maven...
where mvn >nul 2>&1
if errorlevel 1 (
    echo.
    echo [ERROR] Maven no esta instalado o no esta en PATH
    echo.
    echo Soluciones:
    echo   1. Descarga Maven desde: https://maven.apache.org/download.cgi
    echo   2. O instalalo con: choco install maven
    echo   3. O con scoop: scoop install maven
    echo.
    echo Despues reinicia esta ventana de comandos.
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
    echo [ERROR] mvn clean fallo
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
    echo [ERROR] mvn install fallo
    echo.
    echo Verifica que pom.xml existe y es valido
    echo.
    pause
    exit /b 1
)
echo [OK] mvn install completado
echo.

REM ============================================================================
REM FASE 3: Crear directorio de certificados
REM ============================================================================
echo [3/5] Generando PKI (Infraestructura de Clave Publica)
echo.

if not exist "%CERTS_DIR%" (
    echo       Creando directorio: %CERTS_DIR%
    mkdir "%CERTS_DIR%"
)

if exist "%KEYSTORE%"   del /f /q "%KEYSTORE%"
if exist "%TRUSTSTORE%" del /f /q "%TRUSTSTORE%"
if exist "%CERT_FILE%"  del /f /q "%CERT_FILE%"

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
    echo [ERROR] No se pudo crear el keystore
    pause
    exit /b 1
)
echo [OK] Keystore creado: %KEYSTORE%
echo.

echo [3b/5] Exportando certificado publico del servidor...
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

echo [VERIFICACION PKI] Contenido del keystore:
keytool -list -keystore "%KEYSTORE%" -storepass "%PASSWORD%"
echo.

REM ============================================================================
REM FASE 4: Maven Compile
REM ============================================================================
echo [4/5] Ejecutando: mvn compile
echo       Compilando codigo fuente...
echo.

cd /d "%PROJECT_ROOT%"
call mvn compile -q

if errorlevel 1 (
    echo.
    echo [ERROR] mvn compile fallo
    echo Verifica que el codigo Java es valido y las dependencias estan instaladas.
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
echo       Empaquetando aplicacion...
echo.

cd /d "%PROJECT_ROOT%"
call mvn package -DskipTests -q

if errorlevel 1 (
    echo.
    echo [ERROR] mvn package fallo
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
echo [Maven]
echo   mvn clean      - Limpieza completada
echo   mvn install    - Dependencias instaladas
echo   mvn compile    - Codigo compilado
echo   mvn package    - Aplicacion empaquetada
echo.
echo [PKI - Certificados en certs\]
echo   keystore.jks        - Servidor (clave privada)
echo   truststore.jks      - Cliente (certificado publico)
echo   server.cer          - Certificado publico exportado
echo.
echo [JARs en target\]
echo   servidor-ssl.jar        - Servidor TLS 1.3
echo   servidor-plain.jar      - Servidor TCP sin cifrado
echo   performance-test.jar    - Test de rendimiento
echo   cipher-suite-test.jar   - Test de cipher suites
echo.
echo [Pasos siguientes]
echo   1. Terminal 1: scripts\run_server.bat
echo   2. Terminal 2: scripts\run_client.bat
echo   3. Terminal 3: scripts\run_performance_test.bat
echo.
echo [PKI Info]
echo   Password : PAI2password
echo   Alias    : ssl
echo   Validez  : %VALIDITY% dias
echo   Tamano   : RSA %KEY_SIZE% bits
echo ================================================
echo.
pause