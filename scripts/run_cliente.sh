#!/bin/bash

HOST="${1:-localhost}"
PORT="${2:-8443}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CLASSES_DIR="$PROJECT_ROOT/target/classes"
LIBS_DIR="$PROJECT_ROOT/target/dependency-jars"
CERTS_DIR="$PROJECT_ROOT/certs"

if [ ! -f "$CLASSES_DIR/ClienteSSL.class" ]; then
    echo "❌ ERROR: No se encuentra ClienteSSL.class"
    echo "           Necesitas compilar primero con: ./scripts/compile_cliente.sh"
    exit 1
fi

if [ ! -f "$CERTS_DIR/truststore.jks" ]; then
    echo "❌ ERROR: No se encuentra $CERTS_DIR/truststore.jks"
    exit 1
fi

echo "[✓] Verificacion completada"
echo "    Proyecto: $PROJECT_ROOT"
echo "    Host:     $HOST"
echo "    Puerto:   $PORT"
echo ""

echo "[+] Conectando al servidor..."
echo "    Protocolo: TLS 1.3"
echo "    Verifica que ServidorSSL este ejecutándose"
echo ""

cd "$PROJECT_ROOT"

java -Djavax.net.ssl.trustStore="$CERTS_DIR/truststore.jks" \
     -Djavax.net.ssl.trustStorePassword=PAI2password \
     -cp "$CLASSES_DIR:$LIBS_DIR/*" \
     ClienteSSL "$HOST" "$PORT"

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ Error al conectar"
    echo "   - Verifica que ServidorSSL esta ejecutándose"
    echo "   - Host correcto: $HOST"
    echo "   - Puerto correcto: $PORT"
    exit 1
fi