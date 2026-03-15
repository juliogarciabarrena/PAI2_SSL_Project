import java.io.*;
import java.util.Scanner;
import javax.net.ssl.*;

/**
 * ClienteSSL — cliente interactivo para la VPN SSL/TLS de la Universidad Pública.
 *
 * Características:
 *   RF-1  Registro de nuevos usuarios (REGISTER)
 *   RF-2  Inicio de sesión (LOGIN)
 *   RF-6  Envío de mensajes de máximo 144 caracteres (MSG)
 *   RF-4  Cierre de sesión (LOGOUT)
 *   RS-1  Conexión con TLS 1.3 forzado
 *
 * Protocolo de mensajes:
 *   REGISTER|usuario|contraseña  →  OK|... o ERROR|...
 *   LOGIN|usuario|contraseña     →  OK|... o ERROR|...
 *   MSG|texto (máx. 144 chars)   →  OK|... o ERROR|...
 *   LOGOUT                       →  OK|...
 *
 * Ejecución:
 *   java -Djavax.net.ssl.trustStore=certs/truststore.jks \
 *        -Djavax.net.ssl.trustStorePassword=PAI2password \
 *        ClienteSSL [host] [port]
 *
 * Por defecto: localhost:8443
 */
public class ClienteSSL {

    // Parámetros por defecto
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8443;

    // Variables de estado del cliente
    private SSLSocket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String loggedUser = null;

    public static void main(String[] args) {
        // Obtener host y puerto desde argumentos o usar valores por defecto
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        ClienteSSL client = new ClienteSSL();
        try {
            client.connect(host, port);
            client.interactiveMenu();
        } catch (IOException e) {
            System.err.println("[ERROR] Error de conexión: " + e.getMessage());
            System.exit(1);
        }
    }

    // =========================================================================
    // Conexión SSL/TLS
    // =========================================================================

    /**
     * Establece conexión SSL/TLS con el servidor.
     * Fuerza TLS 1.3 según la especificación.
     */
    public void connect(String host, int port) throws IOException {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Cliente VPN SSL   Universidad Pública      ║");
        System.out.println("║   Protocolo: TLS 1.3  |  Puerto: " + port + "        ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("[Cliente] Conectando a " + host + ":" + port + "...\n");

        try {
            // Crear socket SSL con truststore del cliente
            SSLSocketFactory factory =
                    (SSLSocketFactory) SSLSocketFactory.getDefault();

            socket = (SSLSocket) factory.createSocket(host, port);

            socket.setEnabledProtocols(new String[]{Config.getProtocol()});
            socket.setEnabledCipherSuites(Config.getCipherSuites());

            // Iniciar handshake para obtener información de la sesión
            socket.startHandshake();

            // Log de conexión exitosa
            SSLSession session = socket.getSession();
            System.out.printf("[Cliente] [OK] Conexión SSL establecida:%n");
            System.out.printf("    Protocolo: %s%n", session.getProtocol());
            System.out.printf("    Cipher Suite: %s%n", session.getCipherSuite());
            System.out.printf("    Peer Principal: %s%n%n", session.getPeerPrincipal());

            // Crear streams de comunicación
            out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()),
                    true  // autoFlush
            );
            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            System.out.println("[Cliente] Escribe 'help' para ver los comandos disponibles.\n");

        } catch (IOException e) {
            System.err.println("[Cliente] [ERROR] No se pudo conectar al servidor: " + e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // Menú interactivo
    // =========================================================================

    /**
     * Bucle principal interactivo del cliente.
     * Proporciona un menú para que el usuario interactúe con el servidor.
     */
    public void interactiveMenu() {
        Scanner scanner = new Scanner(System.in);

        try {
            while (socket != null && socket.isConnected()) {
                System.out.print(loggedUser != null
                        ? "[" + loggedUser + "]> "
                        : "[no autenticado]> ");

                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                // Procesar comando
                if (input.equalsIgnoreCase("help")) {
                    showHelp();
                } else if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                    if (loggedUser != null) {
                        handleLogout();
                    }
                    break;
                } else if (input.startsWith("register ")) {
                    handleRegisterInteractive(input.substring(9));
                } else if (input.startsWith("login ")) {
                    handleLoginInteractive(input.substring(6));
                } else if (input.startsWith("send ")) {
                    String msg = input.startsWith("send ")
                            ? input.substring(5)
                            : input.substring(4);
                    handleMessage(msg);
                }else if (input.startsWith("history")) {
                    String args = input.length() > 7 ? input.substring(8) : "";
                    handleHistory(args.split(" "));
                } else if (input.equalsIgnoreCase("logout")) {
                    handleLogout();
                } else if (input.equalsIgnoreCase("status")) {
                    showStatus();
                } else if (input.equalsIgnoreCase("clear")) {
                    clearScreen();
                } else {
                    System.out.println("[ERROR] Comando desconocido. Escribe 'help' para ayuda.\n");
                }
            }
        } catch (Exception e) {
            System.err.println("[Cliente] [ERROR] Error durante interacción: " + e.getMessage());
        } finally {
            disconnect();
            scanner.close();
        }
    }

    // =========================================================================
    // Manejadores de comandos
    // =========================================================================

    private void handleHistory(String[] split) {
        if (split.length == 0) {
            System.out.println("Uso: history [username] [limit]\n");
            return;
        }

        if (loggedUser == null) {
            System.out.println("[ERROR] No autenticado. Debes hacer LOGIN primero.\n");
            return;
        }

        String cmd;
        if (split.length == 1) {
            if (!isNumeric(split[0])){
                cmd = String.format("HISTORY|%s|%s", split[0], "10");
            } else {
                cmd = String.format("HISTORY|%s", split[0]);
            }
        } else if (split.length == 2) {
            cmd = String.format("HISTORY|%s|%s", split[0], split[1]);
        } else {
            System.out.println("[ERROR] Formato incorrecto. Uso: history [username] [limit]\n");
            return;
        }

        // Enviar comando y leer respuesta multi-línea
        try {
            out.println(cmd);
            String firstLine = in.readLine();
            
            if (firstLine == null) {
                System.out.println("[WARN]  Desconexión inesperada.\n");
                return;
            }

            if (firstLine.startsWith("HISTORY_COUNT|")) {
                // Extraer número de mensajes
                String[] countParts = firstLine.split("\\|");
                int count = Integer.parseInt(countParts[1]);

                // Leer los siguientes 'count' mensajes
                System.out.println("Historial (" + count + " mensaje" + (count != 1 ? "s" : "") + "):");
                for (int i = 0; i < count; i++) {
                    String msg = in.readLine();
                    if (msg != null) {
                        System.out.println("  " + msg);
                    }
                }
                System.out.println();
            } else if (firstLine.startsWith("ERROR|")) {
                System.out.println("[ERROR] " + firstLine.substring(6) + "\n");
            } else {
                System.out.println("[WARN]  Respuesta inesperada: " + firstLine + "\n");
            }
        } catch (IOException e) {
            System.err.println("[Cliente] [ERROR] Error al obtener historial: " + e.getMessage());
        }
    }

    /**
     * RF-1: Maneja el registro de un nuevo usuario.
     */
    private void handleRegisterInteractive(String args) {
        if (args.isEmpty()) {
            System.out.println("Uso: register <usuario> <contraseña>\n");
            return;
        }

        String[] parts = args.split(" ", 2);
        if (parts.length < 2) {
            System.out.println("[ERROR] Debes proporcionar usuario y contraseña.\n");
            return;
        }

        String username = parts[0];
        String password = parts[1];

        String cmd = String.format("REGISTER|%s|%s", username, password);
        String response = sendCommand(cmd);

        if (response.startsWith("OK|")) {
            System.out.println("[OK]" + response.substring(3) + "\n");
        } else if (response.startsWith("ERROR|")) {
            System.out.println("[ERROR]" + response.substring(6) + "\n");
        } else {
            System.out.println("[WARN]  Respuesta inesperada: " + response + "\n");
        }
    }

    /**
     * RF-2: Maneja el inicio de sesión.
     */
    private void handleLoginInteractive(String args) {
        if (loggedUser != null) {
            System.out.println("[ERROR] Ya tienes una sesión activa como: " + loggedUser + "\n");
            return;
        }

        if (args.isEmpty()) {
            System.out.println("Uso: login <usuario> <contraseña>\n");
            return;
        }

        String[] parts = args.split(" ", 2);
        if (parts.length < 2) {
            System.out.println("[ERROR] Debes proporcionar usuario y contraseña.\n");
            return;
        }

        String username = parts[0];
        String password = parts[1];

        String cmd = String.format("LOGIN|%s|%s", username, password);
        String response = sendCommand(cmd);

        if (response.startsWith("OK|")) {
            loggedUser = username;
            System.out.println("[OK] " + response.substring(3) + "\n");
        } else if (response.startsWith("ERROR|")) {
            System.out.println("[ERROR] " + response.substring(6) + "\n");
        } else {
            System.out.println("[WARN]  Respuesta inesperada: " + response + "\n");
        }
    }

    /**
     * RF-6: Maneja el envío de mensajes (máx. 144 caracteres).
     */
    private void handleMessage(String content) {
        if (loggedUser == null) {
            System.out.println("[ERROR] No autenticado. Debes hacer LOGIN primero.\n");
            return;
        }

        if (content.isEmpty()) {
            System.out.println("[ERROR] El mensaje no puede estar vacío.\n");
            return;
        }

        // Validar límite de 144 caracteres (RF-6)
        if (content.length() > 144) {
            System.out.println("[WARN]  El mensaje excede 144 caracteres. Se truncará.\n");
            content = content.substring(0, 144);
        }

        String cmd = String.format("MSG|%s", content);
        String response = sendCommand(cmd);

        if (response.startsWith("OK|")) {
            System.out.println("[OK] " + response.substring(3) + "\n");
        } else if (response.startsWith("ERROR|")) {
            System.out.println("[ERROR] " + response.substring(6) + "\n");
        } else {
            System.out.println("[WARN]  Respuesta inesperada: " + response + "\n");
        }
    }

    /**
     * RF-4: Cierra la sesión del usuario.
     */
    private void handleLogout() {
        if (loggedUser == null) {
            System.out.println("[ERROR] No hay sesión activa.\n");
            return;
        }

        String response = sendCommand("LOGOUT");

        if (response.startsWith("OK|")) {
            System.out.println("[OK] " + response.substring(3) + "\n");
            loggedUser = null;
        } else if (response.startsWith("ERROR|")) {
            System.out.println("[ERROR] " + response.substring(6) + "\n");
        } else {
            System.out.println("[WARN]  Respuesta inesperada: " + response + "\n");
        }
    }

    // =========================================================================
    // Comunicación con el servidor
    // =========================================================================

    /**
     * Envía un comando al servidor y espera la respuesta.
     * @param command comando a enviar
     * @return respuesta del servidor
     */
    private String sendCommand(String command) {
        try {
            // Enviar comando
            out.println(command);

            // Recibir respuesta
            String response = in.readLine();
            return response != null ? response : "ERROR|Desconexión inesperada";

        } catch (IOException e) {
            System.err.println("[Cliente] [ERROR] Error al comunicarse: " + e.getMessage());
            return "ERROR|" + e.getMessage();
        }
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    /**
     * Muestra la ayuda de comandos disponibles.
     */
    private void showHelp() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                   COMANDOS DISPONIBLES                            ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  AUTENTICACIÓN:                                                   ║");
        System.out.println("║    register <usuario> <contraseña>   - Registrar nuevo usuario    ║");
        System.out.println("║    login <usuario> <contraseña>      - Iniciar sesión             ║");
        System.out.println("║    logout                            - Cerrar sesión              ║");
        System.out.println("║                                                                   ║");
        System.out.println("║  MENSAJES:                                                        ║");
        System.out.println("║    send <mensaje>                    - Enviar mensaje             ║");
        System.out.println("║    history <username> <limit>        - Ver historial de mensajes  ║");
        System.out.println("║                                                                   ║");
        System.out.println("║                                                                   ║");
        System.out.println("║  UTILIDADES:                                                      ║");
        System.out.println("║    status                            - Ver estado actual          ║");
        System.out.println("║    clear                             - Limpiar pantalla           ║");
        System.out.println("║    help                              - Mostrar esta ayuda         ║");
        System.out.println("║    exit, quit                        - Salir                      ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝\n");
    }

    /**
     * Muestra el estado actual del cliente.
     */
    private void showStatus() {
        System.out.println("\nEstado del cliente:");
        System.out.println("  • Conectado: " + (socket != null && socket.isConnected() ? " Sí" : " No"));
        if (socket != null && socket.isConnected()) {
            try {
                SSLSession session = socket.getSession();
                System.out.println("  • Protocolo: " + session.getProtocol());
                System.out.println("  • Cipher Suite: " + session.getCipherSuite());
            } catch (Exception e) {
                System.out.println("  • Error al obtener información SSL");
            }
        }
        System.out.println("  • Usuario autenticado: " + (loggedUser != null ? " Sí " + loggedUser : " No"));
        System.out.println();
    }

    /**
     * Limpia la pantalla (funciona en Linux/Mac, parcialmente en Windows).
     */
    private void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception ignored) {
            // Si no funciona, simplemente continuar
        }
    }

    /**
     * Cierra la conexión SSL/TLS de forma segura.
     */
    public void disconnect() {
        try {
            if (loggedUser != null) {
                try {
                    out.println("LOGOUT");
                    in.readLine();
                } catch (Exception ignored) {
                }
            }

            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();

            System.out.println("\n[Cliente] Desconectado. ¡Hasta luego!");
        } catch (IOException e) {
            System.err.println("[Cliente] Error al desconectar: " + e.getMessage());
        }
    }


    //Funcion auxiliar
    public static boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}