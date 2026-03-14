#!/bin/bash

NUM_CLIENTS="${1:-300}"
HOST="${2:-localhost}"
PORT="${3:-8443}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CLASSES_DIR="$PROJECT_ROOT/target/classes"
LIBS_DIR="$PROJECT_ROOT/target/dependency-jars"
CERTS_DIR="$PROJECT_ROOT/certs"
LOGS_DIR="$PROJECT_ROOT/logs"

mkdir -p "$LOGS_DIR"

if [ ! -f "$CLASSES_DIR/PerformanceTest.class" ]; then
    echo "[ERROR] No se encuentra PerformanceTest.class"
    echo "Ejecuta primero: ./scripts/setup.sh"
    exit 1
fi

echo ""
echo "==============================================================="
echo "   Test de Rendimiento (300 clientes) - MAC/LINUX"
echo "==============================================================="
echo ""

echo "[OK] Verificacion completada"
echo "     Proyecto:    $PROJECT_ROOT"
echo "     Clientes:    $NUM_CLIENTS"
echo "     Host:        $HOST"
echo "     Puerto:      $PORT"
echo "     Log file:    $LOGS_DIR/performance_test.log"
echo ""

echo "[+] Iniciando test de rendimiento..."
echo "     Probando $NUM_CLIENTS conexiones simultaneas"
echo "     Esto puede tomar 30-60 segundos..."
echo ""

cd "$PROJECT_ROOT"

java -Dfile.encoding=UTF-8 \
     -Djavax.net.ssl.trustStore="$CERTS_DIR/truststore.jks" \
     -Djavax.net.ssl.trustStorePassword=PAI2password \
     -cp "$CLASSES_DIR:$LIBS_DIR/*" \
     PerformanceTest $NUM_CLIENTS $HOST $PORT > "$LOGS_DIR/performance_test.log" 2>&1

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] Fallo durante el test"
    echo "Verifica que ServidorSSL esta ejecutandose en otra terminal"
    exit 1
fi

echo ""
echo "==============================================================="
echo "[OK] Test completado exitosamente!"
echo ""
echo "Resultados guardados en: $LOGS_DIR/performance_test.log"
echo "==============================================================="
echo ""

cat "$LOGS_DIR/performance_test.log"