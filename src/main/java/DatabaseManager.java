import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseManager — gestiona la base de datos SQLite del servidor VPN SSL.
 * 
 * Tablas:
 *   users    — credenciales, intentos fallidos, bloqueo anti-BruteForce
 *   messages — historial de mensajes por usuario
 * 
 * Usa WAL (Write-Ahead Logging) para soportar 300 clientes concurrentes.
 */
public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("test.db", "vpn_server.db");

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    public DatabaseManager() {
        initDatabase();
    }

    /**
     * Crea las tablas si no existen y activa WAL + usuarios pre-registrados.
     */
    private void initDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // WAL mode: mejora concurrencia para múltiples clientes simultáneos
            stmt.execute("PRAGMA journal_mode=WAL;");

            // Tabla de usuarios
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    username        TEXT UNIQUE NOT NULL,
                    password_hash   TEXT NOT NULL,
                    failed_attempts INTEGER DEFAULT 0,
                    locked_until    TEXT DEFAULT NULL
                );
            """);

            // Tabla de mensajes
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id   INTEGER NOT NULL,
                    content   TEXT NOT NULL,
                    timestamp TEXT DEFAULT (datetime('now')),
                    FOREIGN KEY (user_id) REFERENCES users(id)
                );
            """);

            // Insertar usuarios pre-registrados (RF-5) si no existen
            insertPreregisteredUsers(conn);

            System.out.println("[DB] Base de datos inicializada correctamente.");

        } catch (SQLException e) {
            System.err.println("[DB] Error al inicializar la base de datos: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Inserta usuarios pre-registrados si la tabla está vacía.
     * Las contraseñas están hasheadas con BCrypt (factor de coste 12).
     */
    private void insertPreregisteredUsers(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM users;";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(checkSql)) {
            if (rs.getInt(1) > 0) return; // ya hay usuarios
        }

        // Usuarios iniciales: usuario / contraseña (hasheadas con BCrypt)
        String[][] users = {
            {"admin",    "admin123"},
            {"profesor", "prof456"},
            {"alumno1",  "pass789"},
            {"alumno2",  "pass000"},
            {"empleado", "emp321"}
        };

        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] u : users) {
                ps.setString(1, u[0]);
                ps.setString(2, BCryptHelper.hash(u[1]));
                ps.executeUpdate();
            }
        }
        System.out.println("[DB] Usuarios pre-registrados insertados.");
    }

    // -------------------------------------------------------------------------
    // Usuarios
    // -------------------------------------------------------------------------

    /**
     * Registra un nuevo usuario. Devuelve true si se creó correctamente.
     * Devuelve false si el nombre de usuario ya existe (RF-1).
     */
    public synchronized boolean registerUser(String username, String password) {
        if (userExists(username)) return false;

        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, BCryptHelper.hash(password));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[DB] Error al registrar usuario: " + e.getMessage());
            return false;
        }
    }

    /**
     * Comprueba si un usuario existe en la base de datos.
     */
    public boolean userExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Obtiene el hash de contraseña almacenado para un usuario.
     * Devuelve null si el usuario no existe.
     */
    public String getPasswordHash(String username) {
        String sql = "SELECT password_hash FROM users WHERE username = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("password_hash") : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Obtiene el ID de un usuario por su nombre.
     */
    public int getUserId(String username) {
        String sql = "SELECT id FROM users WHERE username = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : -1;
            }
        } catch (SQLException e) {
            return -1;
        }
    }

    // -------------------------------------------------------------------------
    // Anti-BruteForce (RS-3)
    // -------------------------------------------------------------------------

    /**
     * Comprueba si una cuenta está bloqueada por exceso de intentos fallidos.
     */
    public boolean isAccountLocked(String username) {
        String sql = "SELECT locked_until FROM users WHERE username = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String lockedUntil = rs.getString("locked_until");
                if (lockedUntil == null) return false;
                // Está bloqueado si el tiempo de desbloqueo aún no ha llegado
                return LocalDateTime.now().isBefore(LocalDateTime.parse(lockedUntil));
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Devuelve el tiempo restante de bloqueo en segundos (para mensajes al usuario).
     */
    public long getLockRemainingSeconds(String username) {
        String sql = "SELECT locked_until FROM users WHERE username = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0;
                String lockedUntil = rs.getString("locked_until");
                if (lockedUntil == null) return 0;
                LocalDateTime unlock = LocalDateTime.parse(lockedUntil);
                LocalDateTime now = LocalDateTime.now();
                if (now.isAfter(unlock)) return 0;
                return java.time.Duration.between(now, unlock).getSeconds();
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    /**
     * Incrementa los intentos fallidos. Si llega a 5, bloquea la cuenta 15 minutos.
     */
    public synchronized void incrementFailedAttempts(String username) {
        String selectSql = "SELECT failed_attempts FROM users WHERE username = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                int attempts = rs.getInt("failed_attempts") + 1;

                if (attempts >= 5) {
                    // Bloquear cuenta 15 minutos
                    String lockUntil = LocalDateTime.now().plusMinutes(15).toString();
                    String updateSql = "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE username = ?;";
                    try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
                        ups.setInt(1, attempts);
                        ups.setString(2, lockUntil);
                        ups.setString(3, username);
                        ups.executeUpdate();
                    }
                    System.err.println("[DB] Cuenta bloqueada por BruteForce: " + username);
                } else {
                    String updateSql = "UPDATE users SET failed_attempts = ? WHERE username = ?;";
                    try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
                        ups.setInt(1, attempts);
                        ups.setString(2, username);
                        ups.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error al incrementar intentos fallidos: " + e.getMessage());
        }
    }

    /**
     * Resetea los intentos fallidos tras un login exitoso.
     */
    public synchronized void resetFailedAttempts(String username) {
        String sql = "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE username = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Error al resetear intentos fallidos: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Mensajes (RF-6, RF-7)
    // -------------------------------------------------------------------------

    /**
     * Almacena un mensaje enviado por un usuario autenticado.
     */
    public synchronized boolean saveMessage(String username, String content) {
        int userId = getUserId(username);
        if (userId == -1) return false;

        // Limitar a 144 caracteres (RF-6)
        if (content.length() > 144) {
            content = content.substring(0, 144);
        }

        String sql = "INSERT INTO messages (user_id, content) VALUES (?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, content);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[DB] Error al guardar mensaje: " + e.getMessage());
            return false;
        }
    }

    /**
     * Devuelve el número total de mensajes enviados por un usuario.
     */
    public int getMessageCount(String username) {
        int userId = getUserId(username);
        if (userId == -1) return 0;

        String sql = "SELECT COUNT(*) FROM messages WHERE user_id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    /**
     * Devuelve los últimos N mensajes de un usuario (para historial).
     */
    public List<String> getLastMessages(String username, int limit) {
        int userId = getUserId(username);
        List<String> msgs = new ArrayList<>();
        if (userId == -1) return msgs;

        String sql = "SELECT content, timestamp FROM messages WHERE user_id = ? ORDER BY id DESC LIMIT ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    msgs.add("[" + rs.getString("timestamp") + "] " + rs.getString("content"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error al obtener mensajes: " + e.getMessage());
        }
        return msgs;
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
}