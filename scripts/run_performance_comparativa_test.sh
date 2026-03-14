#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CERTS_DIR="$PROJECT_ROOT/certs"
PASSWORD="PAI2password"
JAR="$PROJECT_ROOT/target/performance-test.jar"
LOGS_DIR="logs"

mkdir -p "$LOGS_DIR"

FECHA=$(date +%Y-%m-%d)
HORA=$(date +%H%M)
LOG="$LOGS_DIR/performance_test_comparativa_${FECHA}_${HORA}.log"

if [ ! -f "$CERTS_DIR/truststore.jks" ]; then
    echo "ERROR: No se encuentra $CERTS_DIR/truststore.jks"
    echo "Ejecuta primero: ./scripts/setup.sh"
    exit 1
fi

if [ ! -f "$JAR" ]; then
    echo "ERROR: No se encuentra $JAR"
    echo "Ejecuta primero: mvn package"
    exit 1
fi

echo "============================================================"
echo "   Comparativa Conexiones Simultaneas — SSL vs Plain"
echo "   SSL   : localhost:8443  (run_server.sh debe estar activo)"
echo "   Plain : localhost:8080  (run_server_plain.sh debe estar activo)"
echo "   Clientes: 300"
echo "   Log: $LOG"
echo "============================================================"
echo ""

cd "$PROJECT_ROOT"

java -Dfile.encoding=UTF-8 \
     -Djavax.net.ssl.trustStore="$CERTS_DIR/truststore.jks" \
     -Djavax.net.ssl.trustStorePassword="$PASSWORD" \
     -jar "$JAR"

if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: El test termino con errores."
    echo "Verifica que ServidorSSL y ServidorPlain estan ejecutandose."
    exit 1
fi

echo ""
echo "============================================================"
echo "   Test completado"
echo "   Log guardado en: $LOG"
echo "============================================================"
echo ""