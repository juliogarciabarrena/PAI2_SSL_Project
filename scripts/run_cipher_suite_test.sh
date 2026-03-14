#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CERTS_DIR="$PROJECT_ROOT/certs"
TRUSTSTORE="$CERTS_DIR/truststore.jks"
PASSWORD="PAI2password"
CLASSES_DIR="$PROJECT_ROOT/target/classes"
LIBS_DIR="$PROJECT_ROOT/target/dependency-jars"

if [ ! -f "$TRUSTSTORE" ]; then
    echo "ERROR: No se encuentra $TRUSTSTORE"
    echo "Ejecuta primero: ./scripts/setup.sh"
    exit 1
fi

if [ ! -d "$CLASSES_DIR" ]; then
    echo "ERROR: No se encuentran clases compiladas en $CLASSES_DIR"
    echo "Ejecuta primero: ./scripts/compile_cliente.sh"
    exit 1
fi

echo "============================================================"
echo "   Test de Cipher Suites — VPN SSL TLS 1.3"
echo "   Servidor: localhost:8443  (run_server.sh debe estar activo)"
echo "   Protocolo: TLSv1.3"
echo "============================================================"
echo ""

cd "$PROJECT_ROOT"

java -Dfile.encoding=UTF-8 \
     -Djavax.net.ssl.trustStore="$TRUSTSTORE" \
     -Djavax.net.ssl.trustStorePassword="$PASSWORD" \
     -cp "$CLASSES_DIR:$LIBS_DIR/*" \
     CipherSuiteTest localhost 8443

if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: El test termino con errores."
    echo "Verifica que ServidorSSL esta ejecutandose."
    exit 1
fi

echo ""
echo "============================================================"
echo "   Test completado"
echo "============================================================"
echo ""