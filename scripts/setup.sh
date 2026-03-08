#!/bin/bash
# =============================================================================
# setup.sh — Generación automática de certificados para VPN SSL
#
# Ejecutar ANTES de arrancar el servidor (Día 1, mañana).
# Genera keystore.jks (servidor) y truststore.jks (cliente).
#
# Uso: bash scripts/setup.sh
# =============================================================================

set -e  # Salir si algún comando falla

CERTS_DIR="certs"
KEYSTORE="$CERTS_DIR/keystore.jks"
TRUSTSTORE="$CERTS_DIR/truststore.jks"
CERT_FILE="$CERTS_DIR/server.cer"
PASSWORD="PAI2password"
ALIAS="ssl"
VALIDITY=365
KEY_SIZE=2048
DNAME="CN=universidad.local, OU=InSEGUS, O=Universidad de Sevilla, L=Sevilla, ST=Andalucia, C=ES"

echo "╔══════════════════════════════════════════════╗"
echo "║    Generación PKI — VPN SSL TLS 1.3          ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

# Crear directorio si no existe
mkdir -p "$CERTS_DIR"

# -----------------------------------------------------------------------------
# 1. Eliminar keystores anteriores si existen
# -----------------------------------------------------------------------------
if [ -f "$KEYSTORE" ]; then
    echo "[!] Eliminando keystore anterior..."
    rm -f "$KEYSTORE" "$TRUSTSTORE" "$CERT_FILE"
fi

# -----------------------------------------------------------------------------
# 2. Generar keystore del servidor (clave RSA 2048 + certificado autofirmado)
# -----------------------------------------------------------------------------
echo "[1/3] Generando keystore del servidor (RSA $KEY_SIZE, $VALIDITY días)..."
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

echo "    ✅ Keystore creado: $KEYSTORE"

# -----------------------------------------------------------------------------
# 3. Exportar certificado público del servidor
# -----------------------------------------------------------------------------
echo "[2/3] Exportando certificado público del servidor..."
keytool -exportcert \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -file "$CERT_FILE" \
    -storepass "$PASSWORD" \
    -noprompt

echo "    ✅ Certificado exportado: $CERT_FILE"

# -----------------------------------------------------------------------------
# 4. Crear truststore del cliente (importar certificado del servidor)
# -----------------------------------------------------------------------------
echo "[3/3] Creando truststore para el cliente..."
keytool -importcert \
    -keystore "$TRUSTSTORE" \
    -alias "$ALIAS" \
    -file "$CERT_FILE" \
    -storepass "$PASSWORD" \
    -noprompt

echo "    ✅ Truststore creado: $TRUSTSTORE"

# -----------------------------------------------------------------------------
# 5. Verificación
# -----------------------------------------------------------------------------
echo ""
echo "── Contenido del keystore ───────────────────────"
keytool -list -keystore "$KEYSTORE" -storepass "$PASSWORD"

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║  ✅ PKI generada correctamente               ║"
echo "║                                              ║"
echo "║  Archivos:                                   ║"
echo "║    certs/keystore.jks   → servidor           ║"
echo "║    certs/truststore.jks → cliente            ║"
echo "║    certs/server.cer     → cert. público      ║"
echo "║                                              ║"
echo "║  Contraseña: PAI2password                    ║"
echo "║  ⚠️  Compartir keystore.jks con Persona 2!   ║"
echo "╚══════════════════════════════════════════════╝"