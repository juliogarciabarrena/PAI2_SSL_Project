import java.io.*;
import java.net.SocketException;
import java.util.List;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * ClientHandler — gestiona la comunicación con un cliente SSL en su propio hilo.
 *
 * Protocolo de mensajes (texto plano sobre canal SSL cifrado):
 *
 *   Cliente → Servidor              Servidor → Cliente
 *   ─────────────────────────────   ──────────────────────────────────
 *   REGISTER|usuario|contraseña  →  OK|Usuario registrado
 *                                   ERROR|Usuario ya existe
 *   LOGIN|usuario|contraseña     →  OK|Login correcto
 *                                   ERROR|Credenciales incorrectas
 *                                   ERROR|Cuenta bloqueada (Xs restantes)
 *   MSG|texto (máx. 144 chars)   →  OK|Mensaje recibido (total: N)
 *                                   ERROR|No autenticado
 *   LOGOUT                       →  OK|Sesión cerrada
 *
 * Soporta 300 clientes concurrentes (cada uno ejecuta en un Thread del pool).
 */
public class ClientHandler implements Runnable {

    private final SSLSocket socket;
    private final AuthManager auth;
    private final DatabaseManager db;

    // Usuario autenticado en esta sesión (null si no ha hecho login)
    private String loggedUser = null;

    public ClientHandler(SSLSocket socket, AuthManager auth, DatabaseManager db) {
        this.socket = socket;
        this.auth   = auth;
        this.db     = db;
    }

    @Override
    public void run() {
        String clientAddr = socket.getRemoteSocketAddress().toString();
        System.out.println("[Server] Cliente conectado: " + clientAddr);

        // el handshake se realizará aquí en el hilo de ejecución del cliente
        try {
            socket.startHandshake();
            SSLSession session = socket.getSession();
            System.out.printf("[Server] Conexión TLS: protocolo=%s  cipher=%s  cliente=%s%n",
                    session.getProtocol(),
                    session.getCipherSuite(),
                    clientAddr);
        } catch (IOException e) {
            System.err.println("[Server] Falló handshake inicial con " + clientAddr + ": " + e.getMessage());
            try { socket.close(); } catch (IOException ignored) {}
            return;
        }

        try (BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter    out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                String response = handleCommand(line.trim());
                out.println(response);

                // Si fue un LOGOUT cerramos el bucle
                if (line.trim().equalsIgnoreCase("LOGOUT")) break;
            }

        } catch (SocketException e) {
            System.out.println("[Server] Cliente desconectado abruptamente: " + clientAddr);
        } catch (IOException e) {
            System.err.println("[Server] Error de IO con " + clientAddr + ": " + e.getMessage());
        } finally {
            // Limpiar sesión si el cliente se desconecta sin hacer LOGOUT
            if (loggedUser != null) {
                auth.logout(loggedUser);
                System.out.println("[Server] Sesión forzada cerrada para: " + loggedUser);
            }
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[Server] Conexión cerrada: " + clientAddr);
        }
    }

    // =========================================================================
    // Procesamiento de comandos
    // =========================================================================

    private String handleCommand(String line) {
        if (line.isEmpty()) return "ERROR|Comando vacío";

        // Dividir comando y parámetros (máximo 3 partes)
        String[] parts = line.split("\\|", 3);
        String cmd = parts[0].toUpperCase();

        return switch (cmd) {
            case "REGISTER" -> handleRegister(parts);
            case "LOGIN"    -> handleLogin(parts);
            case "MSG"      -> handleMessage(parts);
            case "HISTORY"  -> handleHistory(parts);
            case "LOGOUT"   -> handleLogout();
            default         -> "ERROR|Comando desconocido: " + cmd;
        };
    }

    // -------------------------------------------------------------------------
    // REGISTER|usuario|contraseña
    // -------------------------------------------------------------------------
    private String handleRegister(String[] parts) {
        if (parts.length < 3) return "ERROR|Formato incorrecto. Uso: REGISTER|usuario|contraseña";

        String username = parts[1].trim();
        String password = parts[2].trim();

        AuthManager.RegisterResult result = auth.register(username, password);
        return switch (result) {
            case SUCCESS        -> "OK|Usuario registrado exitosamente";
            case ALREADY_EXISTS -> "ERROR|Usuario ya registrado. Elige otro nombre.";
            case WEAK_PASSWORD  -> "ERROR|Contraseña demasiado corta (mínimo 4 caracteres)";
            case INVALID_INPUT  -> "ERROR|Nombre de usuario inválido";
        };
    }

    // -------------------------------------------------------------------------
    // LOGIN|usuario|contraseña
    // -------------------------------------------------------------------------
    private String handleLogin(String[] parts) {
        if (parts.length < 3) return "ERROR|Formato incorrecto. Uso: LOGIN|usuario|contraseña";

        if (loggedUser != null) {
            return "ERROR|Ya tienes una sesión activa como: " + loggedUser;
        }

        String username = parts[1].trim();
        String password = parts[2].trim();

        AuthManager.LoginResult result = auth.login(username, password);
        return switch (result) {
            case SUCCESS -> {
                loggedUser = username;
                yield "OK|Inicio de sesión correcto. Bienvenido, " + username + "!";
            }
            case INVALID_CREDENTIALS -> "ERROR|Credenciales incorrectas. Inicio de sesión fallido.";
            case ACCOUNT_LOCKED -> {
                long secs = db.getLockRemainingSeconds(username);
                yield "ERROR|Cuenta bloqueada por múltiples intentos fallidos. Espera " + secs + " segundos.";
            }
        };
    }

    // -------------------------------------------------------------------------
    // MSG|texto
    // -------------------------------------------------------------------------
    private String handleMessage(String[] parts) {
        if (loggedUser == null) {
            return "ERROR|No autenticado. Debes hacer LOGIN primero.";
        }
        if (parts.length < 2) return "ERROR|Formato incorrecto. Uso: MSG|tu mensaje";

        String content = parts[1].trim();
        if (content.isEmpty()) return "ERROR|El mensaje no puede estar vacío";

        // Limitar a 144 caracteres (RF-6)
        if (content.length() > 144) {
            content = content.substring(0, 144);
        }

        boolean saved = db.saveMessage(loggedUser, content);
        if (saved) {
            int total = db.getMessageCount(loggedUser);
            return "OK|Mensaje recibido y almacenado correctamente. Total mensajes: " + total;
        } else {
            return "ERROR|No se pudo almacenar el mensaje";
        }
    }

        // -------------------------------------------------------------------------
    // MSG|texto
    // -------------------------------------------------------------------------
    private String handleHistory(String[] parts) {
        int limit = 10; 
        List<String> history; 
        if (loggedUser == null) {
            return "ERROR|No autenticado. Debes hacer LOGIN primero.";
        }
        if (parts.length < 2) {
            history = db.getLastMessagesGeneral(limit);

        } else if (parts.length == 2) {
            limit = parts[1].trim().isEmpty() ? 10 : Integer.parseInt(parts[1].trim());
             history = db.getLastMessagesGeneral(limit);
        } else if (parts.length == 3) {
            limit = parts[2].trim().isEmpty() ? 10 : Integer.parseInt(parts[2].trim());
            history = db.getLastMessages(parts[1].trim(), limit);
        } else {
            return "ERROR|Formato incorrecto. Uso: HISTORY|[número de mensajes]";
        }

        // Devolver el número de mensajes seguido de cada uno
        // Formato: HISTORY_COUNT|N seguido de N líneas con los mensajes
        StringBuilder res = new StringBuilder("HISTORY_COUNT|" + history.size());
        for (String msg : history) {
            res.append("\n").append(msg);
        }

        return res.toString();
    }

    // -------------------------------------------------------------------------
    // LOGOUT
    // -------------------------------------------------------------------------
    private String handleLogout() {
        if (loggedUser == null) {
            return "ERROR|No hay sesión activa";
        }
        auth.logout(loggedUser);
        String user = loggedUser;
        loggedUser = null;
        return "OK|Sesión cerrada correctamente. Hasta luego, " + user + "!";
    }
}