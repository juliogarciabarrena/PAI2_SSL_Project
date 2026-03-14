#!/bin/bash

KEYSTORE="certs/keystore.jks"
PASSWORD="PAI2password"
JAR="target/servidor-ssl.jar"
DB="vpn_server.db"

if [ ! -f "$KEYSTORE" ]; then
    echo "ERROR: No se encuentra $KEYSTORE"
    echo "Ejecuta primero: ./scripts/setup.sh"
    exit 1
fi

if [ ! -f "$JAR" ]; then
    echo "ERROR: No se encuentra $JAR"
    echo "Ejecuta primero: mvn package"
    exit 1
fi

echo "Arrancando servidor VPN SSL..."
echo "  Keystore : $KEYSTORE"
echo "  Puerto   : 8443"
echo "  Protocolo: TLS 1.3"
echo "  Base de datos: $DB"
echo ""

java \
    -Djavax.net.ssl.keyStore="$KEYSTORE" \
    -Djavax.net.ssl.keyStorePassword="$PASSWORD" \
    -jar "$JAR"