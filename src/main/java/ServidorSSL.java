import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;

/**
 * ServidorSSL — servidor VPN SSL/TLS para la Universidad Pública.
 *
 * Características de seguridad:
 *   RS-1  TLS 1.3 forzado explícitamente
 *   RS-1  Cipher suites AES-256-GCM y ChaCha20-Poly1305 (robustos, sin vulnerabilidades)
 *   RS-2  Contraseñas hasheadas con BCrypt (delegado en AuthManager/DatabaseManager)
 *   RS-3  Anti-BruteForce: bloqueo tras 5 intentos fallidos durante 15 minutos
 *
 * Escalabilidad:
 *   OBJ-4 Pool de 300 hilos para clientes concurrentes
 *
 * Ejecución:
 *   java -Djavax.net.ssl.keyStore=certs/keystore.jks \
 *        -Djavax.net.ssl.keyStorePassword=PAI2password \
 *        ServidorSSL
 */
public class ServidorSSL {

    // Puerto del servidor SSL
    private static final int PORT = 8443;

    // Máximo de clientes concurrentes (Objetivo 4 del enunciado)
    private static final int MAX_CONCURRENT_CLIENTS = 300;

    public static void main(String[] args) throws IOException {

        // ── Inicializar base de datos y gestor de autenticación ──────────────
        DatabaseManager db   = new DatabaseManager();
        AuthManager     auth = new AuthManager(db);

        // ── Pool de hilos para 300 clientes concurrentes ─────────────────────
        ExecutorService threadPool = Executors.newFixedThreadPool(MAX_CONCURRENT_CLIENTS);

        // ── Crear SSLServerSocket ─────────────────────────────────────────────
        SSLServerSocketFactory factory =
                (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();

        SSLServerSocket serverSocket =
                (SSLServerSocket) factory.createServerSocket(PORT);

        // ── RS-1: Forzar TLS 1.3 únicamente ──────────────────────────────────
        serverSocket.setEnabledProtocols(new String[]{"TLSv1.3"});

        // ── RS-1: Cipher suites TLS 1.3 robustos (Objetivo 2 del enunciado) ──
        // TLS_AES_256_GCM_SHA384     → AES-256 en modo GCM + SHA-384 (máxima seguridad)
        // TLS_CHACHA20_POLY1305_SHA256 → alternativa eficiente en dispositivos sin AES-NI
        // TLS_AES_128_GCM_SHA256     → mínimo aceptable para compatibilidad
        serverSocket.setEnabledCipherSuites(new String[]{
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_AES_128_GCM_SHA256"
        });

        // Shutdown hook para cerrar limpiamente
        final SSLServerSocket finalSocket = serverSocket;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Apagando servidor...");
            auth.clearAllSessions();
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(10, TimeUnit.SECONDS))
                    threadPool.shutdownNow();
                finalSocket.close();
            } catch (Exception e) {
                threadPool.shutdownNow();
            }
            System.out.println("[Server] Servidor detenido correctamente.");
        }));

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║       VPN SSL — Universidad Pública          ║");
        System.out.println("║   Protocolo: TLS 1.3  |  Puerto: " + PORT + "       ║");
        System.out.println("║   Clientes máx.: " + MAX_CONCURRENT_CLIENTS + "                    ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("[Server] Esperando conexiones SSL...\n");

        // ── Bucle principal: aceptar conexiones ───────────────────────────────
        while (!serverSocket.isClosed()) {
            try {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();

                // Verificar negociación TLS antes de aceptar el cliente
                clientSocket.startHandshake();

                // Log de la sesión TLS establecida
                SSLSession session = clientSocket.getSession();
                System.out.printf("[Server] Conexión TLS: protocolo=%s  cipher=%s  cliente=%s%n",
                        session.getProtocol(),
                        session.getCipherSuite(),
                        clientSocket.getRemoteSocketAddress());

                // Delegar al pool de hilos
                threadPool.execute(new ClientHandler(clientSocket, auth, db));

            } catch (SSLException e) {
                System.err.println("[Server] Error TLS handshake (cliente rechazado): " + e.getMessage());
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[Server] Error al aceptar conexión: " + e.getMessage());
                }
            }
        }
    }
}