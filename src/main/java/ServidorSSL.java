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
        ExecutorService threadPool = Executors.newCachedThreadPool();

        // ── Crear SSLServerSocket ─────────────────────────────────────────────
        SSLServerSocketFactory factory =
                (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();

        SSLServerSocket serverSocket =
                (SSLServerSocket) factory.createServerSocket(PORT);

        
        serverSocket.setEnabledProtocols(new String[]{Config.getProtocol()});
        serverSocket.setEnabledCipherSuites(Config.getCipherSuites());

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

                // ya no hacemos handshake aquí: lo realizará el ClientHandler en su hilo
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