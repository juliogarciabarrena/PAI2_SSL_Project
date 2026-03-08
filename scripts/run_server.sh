#!/bin/bash
# =============================================================================
# run_server.sh — Arranque del servidor VPN SSL con TLS 1.3
#
# Uso: bash scripts/run_server.sh
# Prerequisito: haber ejecutado setup.sh primero
# =============================================================================

KEYSTORE="certs/keystore.jks"
PASSWORD="PAI2password"
CLASSPATH="src:libs/sqlite-jdbc.jar:libs/jbcrypt-0.4.jar"

# Verificar que el keystore existe
if [ ! -f "$KEYSTORE" ]; then
    echo "❌ Error: No se encuentra $KEYSTORE"
    echo "   Ejecuta primero: bash scripts/setup.sh"
    exit 1
fi

echo "🚀 Arrancando servidor VPN SSL..."
echo "   Keystore : $KEYSTORE"
echo "   Puerto   : 8443"
echo "   Protocolo: TLS 1.3"
echo ""

java \
    -Djavax.net.ssl.keyStore="$KEYSTORE" \
    -Djavax.net.ssl.keyStorePassword="$PASSWORD" \
    -cp "$CLASSPATH" \
    ServidorSSL