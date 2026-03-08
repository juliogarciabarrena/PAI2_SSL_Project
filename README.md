# PAI-2: BYODSEC — Road Warrior VPN SSL
### Guía de desarrollo para equipo de 3 personas

> 🗓 **Entrega:** 16 de marzo a las 23:59 h &nbsp;|&nbsp; 👥 **Equipo:** 3 personas &nbsp;|&nbsp; ⏱ **~5 días de trabajo**

---

## 📋 Índice

1. [Visión General](#1-visión-general)
2. [Distribución de Roles](#2-distribución-de-roles)
3. [Plan de Trabajo — 5 Días](#3-plan-de-trabajo--5-días)
4. [Guía Técnica Paso a Paso](#4-guía-técnica-paso-a-paso)
5. [Estructura del Entregable](#5-estructura-del-entregable)
6. [Checklist Final](#6-checklist-final)

---

## 1. Visión General

El objetivo del PAI-2 es implementar una **VPN SSL/TLS estilo Road Warrior** para una universidad pública, garantizando **confidencialidad, integridad y autenticidad** en las comunicaciones.

### Tecnologías principales

| Componente | Tecnología |
|---|---|
| Lenguaje | Java (`javax.net.ssl`) |
| Protocolo | **TLS 1.3** (forzado explícitamente) |
| Base de datos | SQLite + JDBC |
| Hash contraseñas | BCrypt / PBKDF2 |
| Certificados | `keytool` (JDK) |
| Análisis tráfico | `tcpdump` + Wireshark (Linux) |

> ⚠️ **Requisito clave:** usar TLS 1.3 con cipher suites robustos. La entrega incluye código fuente, logs de pruebas, trazas de sniffer y un **PDF de máximo 10 páginas**.

---

## 2. Distribución de Roles

### 👤 Persona 1 — Backend & Seguridad
> Infraestructura servidor, certificados, autenticación y base de datos

- **Día 1:** Generación del keystore con `keytool` (RSA 2048, TLS 1.3)
- **Día 1–2:** Implementación del `SSLServerSocket` con cipher suites TLS 1.3 forzados
- **Día 2–3:** Sistema de autenticación: registro, login, logout
- **Día 2–3:** Hashing de contraseñas (BCrypt) + almacenamiento seguro en SQLite
- **Día 3:** Protección anti-BruteForce (bloqueo tras 5 intentos)
- **Día 4:** Tests unitarios de autenticación y seguridad de BBDD

---

### 👤 Persona 2 — Cliente & Protocolo de Comunicación
> Implementación del cliente SSL, protocolo de mensajes y pruebas funcionales

- **Día 1–2:** Implementación del `SSLSocket` cliente con el keystore compartido
- **Día 1–2:** Interfaz de línea de comandos (`register` / `login` / `send` / `logout`)
- **Día 2–3:** Protocolo de mensajes (máx. 144 chars + confirmación del servidor)
- **Día 3–4:** Captura de tráfico con `tcpdump` / Wireshark para verificar cifrado
- **Día 4:** Pruebas end-to-end del flujo completo cliente–servidor

---

### 👤 Persona 3 — Rendimiento, Análisis y Documentación
> Tests de carga, cipher suites, análisis de tráfico y redacción del informe

- **Día 2:** Verificación de cipher suites TLS 1.3 usados (`openssl s_client`)
- **Día 3–4:** Script de test de rendimiento: 300 clientes concurrentes con/sin VPN SSL
- **Día 3–4:** Análisis Wireshark: demostrar que los datos viajan cifrados
- **Día 4–5:** Redacción del PDF de entrega (resumen, manual, trazabilidad)
- **Día 4–5:** `[EXTRA]` Preparación y ejecución del ataque MitM (+10%)
- **Día 5:** Empaquetado final del zip `PAI2-STX.zip`

---

## 3. Plan de Trabajo — 5 Días

| Tarea | Resp. | Día 1 | Día 2 | Día 3 | Día 4 | Día 5 | Prioridad |
|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| Generación PKI (certs TLS 1.3) | P1 | ● | | | | | 🔴 BLOQUEANTE |
| Servidor SSL/TLS – esqueleto | P1 | ● | ● | | | | 🔴 CRÍTICA |
| Auth usuario + BBDD | P1 | | ● | ● | | | 🔴 CRÍTICA |
| Anti-BruteForce + hashing | P1 | | ● | ● | | | 🟠 ALTA |
| Cliente SSL/TLS – interfaz | P2 | ● | ● | | | | 🔴 CRÍTICA |
| Protocolo mensajes (send/recv) | P2 | | ● | ● | | | 🔴 CRÍTICA |
| Persistencia mensajes BBDD | P2 | | | ● | | | 🟠 ALTA |
| Captura Wireshark / tcpdump | P2 | | | ● | ● | | 🟠 ALTA |
| Test rendimiento (300 clientes) | P3 | | | ● | ● | | 🟠 ALTA |
| Análisis cipher suites TLS 1.3 | P3 | | ● | ● | | | 🟠 ALTA |
| Redacción informe PDF | P3 | | | | ● | ● | 🟡 MEDIA |
| Integración & pruebas finales | TODOS | | | | ● | ● | 🔴 CRÍTICA |
| `[EXTRA]` Ataque MitM | P3 | | | | ● | ● | 🟢 OPCIONAL |

> 🔑 **PUNTO CRÍTICO — Día 1 mañana:** P1 debe generar el `keystore.jks` y compartirlo con P2 antes del mediodía. Sin el keystore, P2 no puede arrancar el cliente SSL.

### Sincronización diaria (15 min)
1. ¿Qué completaste ayer?
2. ¿Qué harás hoy?
3. ¿Hay algún bloqueante para el equipo?

---

## 4. Guía Técnica Paso a Paso

### 4.1 Generación del Keystore `(P1 — Día 1, mañana)`

```bash
# 1. Generar keystore del servidor
keytool -genkeypair \
  -keystore keystore.jks \
  -alias ssl \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -dname "CN=universidad.local" \
  -storepass PAI2password

# 2. Exportar certificado público
keytool -exportcert \
  -keystore keystore.jks \
  -alias ssl \
  -file server.cer \
  -storepass PAI2password

# 3. Crear truststore para el cliente
keytool -importcert \
  -keystore truststore.jks \
  -alias ssl \
  -file server.cer \
  -storepass PAI2password \
  -noprompt

# Verificar contenido
keytool -list -keystore keystore.jks
```

> 📤 Compartir `keystore.jks`, `truststore.jks` y la contraseña con todo el equipo inmediatamente.

---

### 4.2 Servidor SSL/TLS con TLS 1.3 forzado `(P1 — Día 1–2)`

```java
// Configuración crítica: forzar TLS 1.3 y cipher suites seguros
SSLServerSocketFactory factory =
    (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();

SSLServerSocket serverSocket =
    (SSLServerSocket) factory.createServerSocket(8443);

// Forzar TLS 1.3 únicamente
serverSocket.setEnabledProtocols(new String[]{"TLSv1.3"});

// Cipher suites TLS 1.3 robustos (objetivo 2 del enunciado)
serverSocket.setEnabledCipherSuites(new String[]{
    "TLS_AES_256_GCM_SHA384",
    "TLS_CHACHA20_POLY1305_SHA256",
    "TLS_AES_128_GCM_SHA256"
});
```

```bash
# Ejecución del servidor
java -Djavax.net.ssl.keyStore=certs/keystore.jks \
     -Djavax.net.ssl.keyStorePassword=PAI2password \
     ServidorSSL
```

---

### 4.3 Cliente SSL/TLS `(P2 — Día 1–2)`

```java
SSLSocketFactory factory =
    (SSLSocketFactory) SSLSocketFactory.getDefault();

SSLSocket socket =
    (SSLSocket) factory.createSocket("localhost", 8443);

socket.setEnabledProtocols(new String[]{"TLSv1.3"});
```

```bash
# Ejecución del cliente
java -Djavax.net.ssl.trustStore=certs/truststore.jks \
     -Djavax.net.ssl.trustStorePassword=PAI2password \
     ClienteSSL
```

---

### 4.4 Protocolo de Mensajes `(P2 — Día 2–3)`

Protocolo simple en texto plano sobre el canal SSL cifrado:

| Dirección | Formato | Ejemplo |
|---|---|---|
| Cliente → Servidor | `REGISTER\|usuario\|password` | `REGISTER\|ana\|pass123` |
| Cliente → Servidor | `LOGIN\|usuario\|password` | `LOGIN\|ana\|pass123` |
| Cliente → Servidor | `MSG\|texto` (máx. 144 chars) | `MSG\|Hola mundo` |
| Cliente → Servidor | `LOGOUT` | `LOGOUT` |
| Servidor → Cliente | `OK\|descripción` | `OK\|Login correcto` |
| Servidor → Cliente | `ERROR\|motivo` | `ERROR\|Usuario no encontrado` |

---

### 4.5 Autenticación Segura y BBDD `(P1 — Día 2–3)`

```sql
-- Esquema SQLite
CREATE TABLE users (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    username        TEXT UNIQUE NOT NULL,
    password_hash   TEXT NOT NULL,         -- BCrypt hash
    failed_attempts INTEGER DEFAULT 0,
    locked_until    DATETIME DEFAULT NULL
);

CREATE TABLE messages (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL,
    content    TEXT NOT NULL,              -- máx. 144 chars
    timestamp  DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Habilitar WAL para concurrencia (300 clientes)
PRAGMA journal_mode=WAL;
```

```java
// Hash con BCrypt (librería: org.mindrot.jbcrypt)
String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));

// Verificación
boolean ok = BCrypt.checkpw(inputPassword, storedHash);

// Anti-BruteForce: bloquear tras 5 intentos durante 15 min
if (user.failedAttempts >= 5) {
    user.lockedUntil = LocalDateTime.now().plusMinutes(15);
}
```

---

### 4.6 Análisis de Tráfico con Wireshark `(P2–P3 — Día 3–4)`

```bash
# Captura SIN SSL (puerto 8080)
sudo tcpdump -i lo -w logs/captura_sin_ssl.pcap port 8080 &
# → ejecutar comunicación sin cifrar
# → Follow TCP Stream mostrará credenciales en TEXTO PLANO

# Captura CON SSL (puerto 8443)
sudo tcpdump -i lo -w logs/captura_con_ssl.pcap port 8443 &
# → ejecutar comunicación con TLS 1.3
# → Follow SSL Stream mostrará "Application Data" CIFRADO
```

> 📸 Incluir capturas de pantalla de ambos casos en el informe como evidencia de confidencialidad.

---

### 4.7 Test de Rendimiento 300 Clientes `(P3 — Día 3–4)`

```java
// Simular 300 clientes concurrentes
ExecutorService pool = Executors.newFixedThreadPool(300);
List<Future<Long>> results = new ArrayList<>();

long start = System.currentTimeMillis();

for (int i = 0; i < 300; i++) {
    results.add(pool.submit(() -> {
        long t0 = System.currentTimeMillis();
        // login + send message + logout
        return System.currentTimeMillis() - t0;
    }));
}

pool.shutdown();
pool.awaitTermination(60, TimeUnit.SECONDS);

long total = System.currentTimeMillis() - start;
// Calcular: tiempo medio, throughput, % overhead SSL vs plain
```

Métricas a documentar:

| Métrica | Sin SSL | Con TLS 1.3 | Overhead |
|---|---|---|---|
| Tiempo medio login | X ms | X ms | ~% |
| Throughput (msg/s) | X | X | ~% |
| Tiempo total 300 clientes | X s | X s | ~% |

> 💡 El overhead esperado de TLS 1.3 es **5–15%** gracias al 1-RTT handshake.

---

### 4.8 `[EXTRA]` Ataque MitM `(P3 — Día 4–5)`

> ⚠️ **Opcional (+10%).** Solo realizar si el resto está completamente terminado.

```bash
# Intento con mitmproxy
mitmproxy --mode transparent --ssl-insecure -p 8080

# Redirigir tráfico al proxy
iptables -t nat -A OUTPUT -p tcp --dport 8443 -j REDIRECT --to-port 8080
```

El ataque **fallará** porque el cliente valida el certificado del servidor contra el `truststore.jks`. Documentar el error obtenido y explicar por qué TLS 1.3 lo previene.

---

## 5. Estructura del Entregable

### 5.1 Árbol de directorios del zip

```
PAI2-STX.zip
│
├── 📁 src/
│   ├── ServidorSSL.java          ← SSLServerSocket + TLS 1.3 + gestión conexiones
│   ├── ClienteSSL.java           ← SSLSocket + menú interactivo
│   ├── AuthManager.java          ← registro, login, logout, anti-BruteForce
│   ├── MessageHandler.java       ← protocolo de mensajes, validación 144 chars
│   ├── DatabaseManager.java      ← SQLite: usuarios y mensajes (WAL mode)
│   └── PerformanceTest.java      ← test 300 clientes concurrentes
│
├── 📁 certs/
│   ├── keystore.jks              ← almacén de claves del servidor
│   ├── truststore.jks            ← almacén de confianza del cliente
│   └── server.cer                ← certificado público exportado
│
├── 📁 logs/
│   ├── captura_sin_ssl.pcap      ← tráfico en texto plano (evidencia)
│   ├── captura_con_ssl.pcap      ← tráfico cifrado TLS 1.3 (evidencia)
│   └── test_rendimiento.log      ← resultados de los 300 clientes
│
├── 📁 scripts/
│   ├── setup.sh                  ← generación automática de certificados
│   └── run_server.sh             ← arranque del servidor con parámetros JVM
│
└── 📄 PAI2-STX-Informe.pdf       ← informe final (máx. 10 páginas)
```

---

### 5.2 Estructura del PDF (máx. 10 páginas)

```
PAI2-STX-Informe.pdf
│
├── 1. Resumen (1–2 pág.)
│   ├── Decisión: TLS 1.3 + cipher suites AES-256-GCM / ChaCha20
│   ├── Decisión: BCrypt para hashing de contraseñas
│   ├── Decisión: SQLite con WAL para concurrencia
│   └── Protocolo de mensajes diseñado
│
├── 2. Manual de Despliegue (3–4 pág.)
│   ├── Requisitos: JDK 11+, SQLite JDBC, BCrypt jar
│   ├── Paso 1: generar certificados (setup.sh)
│   ├── Paso 2: arrancar servidor (run_server.sh)
│   ├── Paso 3: conectar cliente
│   └── Ejemplos de uso: registro, login, envío de mensaje
│
├── 3. Grado de Completitud (2–3 pág.)
│   └── Tabla de trazabilidad (ver sección 5.3)
│
└── 4. [EXTRA] Análisis MitM (1 pág.)
    ├── Herramienta usada y configuración
    ├── Resultado del ataque (fallo esperado)
    └── Explicación técnica de la protección TLS 1.3
```

---

### 5.3 Tabla de trazabilidad de requisitos

| ID | Requisito | Fichero / Clase | Estado |
|---|---|---|:---:|
| RF-1 | Registro de usuarios | `AuthManager.java` | ✅ |
| RF-2 | Inicio de sesión | `AuthManager.java` | ✅ |
| RF-3 | Verificar credenciales | `AuthManager.java` | ✅ |
| RF-4 | Cerrar sesión | `ServidorSSL.java` | ✅ |
| RF-5 | Usuarios preexistentes | `DatabaseManager.java` | ✅ |
| RF-6 | Envío de mensajes (máx. 144 chars) | `MessageHandler.java` | ✅ |
| RF-7 | Persistencia usuarios y mensajes | `DatabaseManager.java` | ✅ |
| RS-1 | TLS 1.3 + cipher suites robustos | `ServidorSSL.java` | ✅ |
| RS-2 | Hashing BCrypt de contraseñas | `AuthManager.java` | ✅ |
| RS-3 | Protección anti-BruteForce | `AuthManager.java` | ✅ |
| RS-4 | Análisis tráfico Wireshark | `logs/capturas.pcap` | ✅ |
| RS-5 | Test rendimiento 300 clientes | `PerformanceTest.java` | ✅ |
| OBJ-EXTRA | Ataque MitM documentado | `Informe sección 4` | ⚪ Opcional |

---

## 6. Checklist Final

Antes de subir el zip a la plataforma virtual:

- [ ] El servidor arranca sin errores SSL
- [ ] El cliente se conecta y realiza login/registro correctamente
- [ ] Los mensajes se envían y persisten en la BBDD
- [ ] La captura Wireshark muestra `Application Data` (datos cifrados)
- [ ] El test de 300 clientes concurrentes no genera errores
- [ ] El informe PDF tiene **máximo 10 páginas**
- [ ] El zip se llama exactamente **`PAI2-STX.zip`**
- [ ] La entrega se hace por la **plataforma virtual** (no por email ni mensajes internos)

> 🚀 Con esta distribución, el equipo trabaja en paralelo desde el Día 1. El único bloqueante real es el keystore (P1, mañana del Día 1). Una vez compartido, los tres podéis avanzar de forma completamente independiente.
