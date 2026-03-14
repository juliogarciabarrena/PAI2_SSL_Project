#!/bin/bash

JAR="target/servidor-plain.jar"
DB="vpn_server.db"

if [ ! -f "$JAR" ]; then
    echo "ERROR: No se encuentra $JAR"
    echo "Ejecuta primero: mvn package"
    exit 1
fi

for pid in $(lsof -ti :8080); do
    echo "[Setup] Liberando puerto 8080 (PID $pid)..."
    kill -9 $pid 2>/dev/null || true
done

echo "============================================================"
echo "   Servidor Plain (sin cifrado) — Solo para pruebas"
echo "   Puerto   : 8080"
echo "   Protocolo: TCP sin cifrar"
echo "   ⚠️  NO usar en produccion"
echo "============================================================"
echo ""

java -jar "$JAR"