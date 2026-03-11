import java.io.*;
import java.net.Socket;
import java.net.SocketException;

/**
 * ClientHandlerPlain — gestiona una conexión plain (sin SSL) en su propio hilo.
 *
 * Propósito exclusivo: servidor de línea base para medir el overhead de TLS 1.3
 * frente a una conexión TCP sin cifrar.
 *
 * Comportamiento:
 *   - Acepta la conexión y la mantiene abierta hasta que el cliente cierre el socket.
 *   - No espera comandos: el test de conexiones simultáneas solo verifica
 *     que la conexión se establece, no que se intercambian mensajes.
 *
 * ⚠️  NO usar en producción. La conexión no está cifrada.
 */
public class ClientHandlerPlain implements Runnable {

    private final Socket socket;

    public ClientHandlerPlain(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String clientAddr = socket.getRemoteSocketAddress().toString();
        System.out.println("[PlainServer] Cliente conectado: " + clientAddr);

        try {
            // Mantener la conexión abierta hasta que el cliente la cierre.
            // read() bloqueará hasta recibir datos o hasta que el cliente
            // cierre el socket — momento en que devuelve -1 y salimos.
            InputStream in = socket.getInputStream();
            while (in.read() != -1) {
                // drenar cualquier byte que llegue sin procesarlo
            }

        } catch (SocketException e) {
            // El cliente cerró el socket — comportamiento esperado en el test
            System.out.println("[PlainServer] Cliente desconectado: " + clientAddr);
        } catch (IOException e) {
            System.err.println("[PlainServer] Error de IO con " + clientAddr + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[PlainServer] Conexión cerrada: " + clientAddr);
        }
    }
}