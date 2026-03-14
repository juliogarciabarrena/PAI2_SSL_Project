#!/bin/bash
# =============================================================================
# setup.sh — Generación automática de certificados para VPN SSL (Mac/Linux)
#
# Este script realiza un setup completo:
#   1. mvn clean       — Limpia artefactos previos
#   2. mvn install     — Descarga e instala dependencias
#   3. Genera PKI      — Crea certificados (keystore/truststore)
#   4. mvn compile     — Compila el código fuente
#   5. mvn package     — Empaqueta en JAR
#
# Ejecutar ANTES de arrancar el servidor (Día 1, mañana).
# Genera keystore.jks (servidor) y truststore.jks (cliente).
#
# Uso: ./scripts/setup.sh
# =============================================================================

set -e  # Salir si hay error

CERTS_DIR="certs"
KEYSTORE="$CERTS_DIR/keystore.jks"
TRUSTSTORE="$CERTS_DIR/truststore.jks"
CERT_FILE="$CERTS_DIR/server.cer"
PASSWORD="PAI2password"
ALIAS="ssl"
VALIDITY="365"
KEY_SIZE="2048"
DNAME="CN=universidad.local, OU=InSEGUS, O=Universidad de Sevilla, L=Sevilla, ST=Andalucia, C=ES"

echo "================================================"
echo "    SETUP COMPLETO — VPN SSL TLS 1.3"
echo "    Maven + PKI + Compilacion"
echo "================================================"
echo ""

# =============================================================================
# VERIFICACION PREVIA
# =============================================================================
echo "[VERIFICACION] Comprobando requisitos..."
echo ""

# Verificar Java
if ! command -v java &> /dev/null; then
    echo "❌ ERROR: Java no está instalado"
    echo ""
    echo "Soluciones:"
    echo "  macOS: brew install openjdk@21"
    echo "  Ubuntu/Debian: sudo apt install openjdk-21-jdk"
    echo "  RedHat/CentOS: sudo yum install java-21-openjdk-devel"
    echo ""
    exit 1
fi
echo "✓ Java encontrado: $(java -version 2>&1 | grep version | head -1)"
echo ""

# Verificar Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ ERROR: Maven no está instalado"
    echo ""
    echo "Soluciones:"
    echo "  macOS: brew install maven"
    echo "  Ubuntu/Debian: sudo apt install maven"
    echo "  RedHat/CentOS: sudo yum install maven"
    echo ""
    echo "O descarga desde: https://maven.apache.org/download.cgi"
    echo ""
    exit 1
fi
echo "✓ Maven encontrado: $(mvn -version | head -1)"
echo ""

# Verificar keytool
if ! command -v keytool &> /dev/null; then
    echo "❌ ERROR: keytool no está disponible"
    echo ""
    echo "keytool debe venir con Java. Verifica que Java está bien instalado."
    echo ""
    exit 1
fi
echo "✓ keytool disponible"
echo ""

# =============================================================================
# CONFIGURACION
# =============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "[CONFIGURACION]"
echo "Proyecto: $PROJECT_ROOT"
echo ""

cd "$PROJECT_ROOT"

# =============================================================================
# FASE 1: Maven Clean
# =============================================================================
echo "[1/5] Ejecutando: mvn clean"
echo "      Limpiando artefactos previos..."
echo ""

mvn clean -q

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ ERROR: mvn clean falló"
    exit 1
fi
echo "✓ mvn clean completado"
echo ""

# =============================================================================
# FASE 2: Maven Install
# =============================================================================
echo "[2/5] Ejecutando: mvn install"
echo "      Descargando e instalando dependencias..."
echo ""

mvn install -DskipTests -q

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ ERROR: mvn install falló"
    echo ""
    echo "Verifica que pom.xml existe y es válido"
    echo ""
    exit 1
fi
echo "✓ mvn install completado"
echo ""

# =============================================================================
# FASE 3: Crear directorio de certificados
# =============================================================================
echo "[3/5] Generando PKI (Infraestructura de Clave Pública)"
echo ""

# Crear directorio certs si no existe
if [ ! -d "$CERTS_DIR" ]; then
    echo "      Creando directorio: $CERTS_DIR"
    mkdir -p "$CERTS_DIR"
fi

# Eliminar keystores anteriores si existen
if [ -f "$KEYSTORE" ]; then
    echo "      Eliminando keystore anterior..."
    rm -f "$KEYSTORE"
fi
if [ -f "$TRUSTSTORE" ]; then
    echo "      Eliminando truststore anterior..."
    rm -f "$TRUSTSTORE"
fi
if [ -f "$CERT_FILE" ]; then
    echo "      Eliminando certificado anterior..."
    rm -f "$CERT_FILE"
fi

echo ""
echo "[3a/5] Generando keystore del servidor (RSA $KEY_SIZE, $VALIDITY dias)..."

keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize $KEY_SIZE \
    -validity $VALIDITY \
    -dname "$DNAME" \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -storetype JKS \
    -noprompt

if [ ! -f "$KEYSTORE" ]; then
    echo "❌ ERROR: No se pudo crear el keystore"
    exit 1
fi
echo "✓ Keystore creado: $KEYSTORE"
echo ""

echo "[3b/5] Exportando certificado público del servidor..."
keytool -exportcert \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -file "$CERT_FILE" \
    -storepass "$PASSWORD" \
    -noprompt

echo "✓ Certificado exportado: $CERT_FILE"
echo ""

echo "[3c/5] Creando truststore para el cliente..."
keytool -importcert \
    -keystore "$TRUSTSTORE" \
    -alias "$ALIAS" \
    -file "$CERT_FILE" \
    -storepass "$PASSWORD" \
    -noprompt

echo "✓ Truststore creado: $TRUSTSTORE"
echo ""

# Verificar keystores
echo "[VERIFICACION PKI]"
echo "Contenido del keystore:"
keytool -list -keystore "$KEYSTORE" -storepass "$PASSWORD"
echo ""

# =============================================================================
# FASE 4: Maven Compile
# =============================================================================
echo "[4/5] Ejecutando: mvn compile"
echo "      Compilando código fuente..."
echo ""

mvn compile -q

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ ERROR: mvn compile falló"
    echo ""
    echo "Verifica que:"
    echo "  - El código Java es válido"
    echo "  - Las dependencias se descargaron correctamente"
    echo ""
    exit 1
fi
echo "✓ mvn compile completado"
echo ""

# =============================================================================
# FASE 5: Maven Package
# =============================================================================
echo "[5/5] Ejecutando: mvn package"
echo "      Empaquetando aplicación..."
echo ""

mvn package -DskipTests -q

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ ERROR: mvn package falló"
    echo ""
    exit 1
fi
echo "✓ mvn package completado"
echo ""
