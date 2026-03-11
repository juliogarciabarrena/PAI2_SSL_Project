import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ServidorPlain — servidor TCP sin cifrado para comparativa de rendimiento.
 *
 * Propósito:
 *   OBJ-5  Línea base para medir el overhead de TLS 1.3 frente a una
 *          conexión sin cifrar. NO usar en producción.
 *
 * Comportamiento:
 *   - Acepta conexiones TCP planas en el puerto 8080.
 *   - Delega cada conexión a ClientHandlerPlain, que la mantiene abierta
 *     hasta que el cliente la cierre — igual que hace ServidorSSL con el
 *     handshake TLS, para que la comparativa sea justa.
 *   - No requiere keystore ni truststore.
 *
 * Ejecución:
 *   java -jar target\servidor-plain.jar
 *
 * ⚠️  SOLO PARA PRUEBAS — NO usar en producción.
 */
public class ServidorPlain {

    private static final int PORT                   = 8080;
    private static final int MAX_CONCURRENT_CLIENTS = 300;
    // Backlog amplio para absorber las 300 conexiones simultáneas del test
    private static final int BACKLOG                = 400;

    public static void main(String[] args) throws IOException {

        // Pool de hilos para 300 clientes concurrentes
        ExecutorService threadPool = Executors.newCachedThreadPool();

        // ServerSocket plain con backlog ampliado
        ServerSocket serverSocket = new ServerSocket(PORT, BACKLOG);

        // Shutdown hook para cerrar limpiamente
        final ServerSocket finalSocket = serverSocket;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[PlainServer] Apagando servidor...");
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(10, TimeUnit.SECONDS))
                    threadPool.shutdownNow();
                finalSocket.close();
            } catch (Exception e) {
                threadPool.shutdownNow();
            }
            System.out.println("[PlainServer] Servidor detenido correctamente.");
        }));

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Servidor Plain — Comparativa Rendimiento   ║");
        System.out.println("║   Protocolo: TCP (sin cifrado)               ║");
        System.out.println("║   Puerto   : " + PORT + "                          ║");
        System.out.println("║   Clientes máx.: " + MAX_CONCURRENT_CLIENTS + "                    ║");
        System.out.println("║   ⚠️  SOLO PARA PRUEBAS — NO USAR EN PROD    ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("[PlainServer] Esperando conexiones...\n");

        // Bucle principal: aceptar conexiones
        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();

                System.out.printf("[PlainServer] Conexión TCP: cliente=%s%n",
                        clientSocket.getRemoteSocketAddress());

                threadPool.execute(new ClientHandlerPlain(clientSocket));

            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[PlainServer] Error al aceptar conexión: " + e.getMessage());
                }
            }
        }
    }
}
