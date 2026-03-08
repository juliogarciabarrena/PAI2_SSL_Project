import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthManager — gestión completa de autenticación para la VPN SSL.
 *
 * Funcionalidades:
 *   RF-1  Registro de nuevos usuarios
 *   RF-2  Inicio de sesión
 *   RF-3  Verificación de credenciales contra la BBDD
 *   RF-4  Cierre de sesión
 *   RS-2  Hashing BCrypt de contraseñas (delegado en BCryptHelper)
 *   RS-3  Protección anti-BruteForce (bloqueo tras 5 intentos, 15 min)
 */
public class AuthManager {

    // Sesiones activas: set de nombres de usuario con sesión iniciada
    // ConcurrentHashMap como set thread-safe para los 300 clientes concurrentes
    private final Set<String> activeSessions =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final DatabaseManager db;

    public AuthManager(DatabaseManager db) {
        this.db = db;
    }

    // =========================================================================
    // RF-1 Registro de usuarios
    // =========================================================================

    /**
     * Registra un nuevo usuario en el sistema.
     *
     * @param username nombre de usuario único
     * @param password contraseña en texto plano (se hashea internamente)
     * @return RegisterResult con el estado de la operación
     */
    public RegisterResult register(String username, String password) {
        if (username == null || username.isBlank()) {
            return RegisterResult.INVALID_INPUT;
        }
        if (password == null || password.length() < 4) {
            return RegisterResult.WEAK_PASSWORD;
        }

        boolean created = db.registerUser(username.trim(), password);
        return created ? RegisterResult.SUCCESS : RegisterResult.ALREADY_EXISTS;
    }

    public enum RegisterResult {
        SUCCESS,
        ALREADY_EXISTS,   // RF-1: informar si el usuario ya existe
        INVALID_INPUT,
        WEAK_PASSWORD
    }

    // =========================================================================
    // RF-2 / RF-3  Inicio de sesión y verificación de credenciales
    // =========================================================================

    /**
     * Intenta iniciar sesión con las credenciales proporcionadas.
     *
     * Flujo:
     *   1. Verifica si la cuenta existe
     *   2. Verifica si está bloqueada por BruteForce (RS-3)
     *   3. Valida contraseña con BCrypt
     *   4. En caso de éxito: resetea intentos fallidos y crea sesión
     *   5. En caso de fallo: incrementa contador de intentos
     *
     * @param username nombre de usuario
     * @param password contraseña en texto plano
     * @return LoginResult con el estado de la operación
     */
    public LoginResult login(String username, String password) {
        if (username == null || password == null) {
            return LoginResult.INVALID_CREDENTIALS;
        }

        String user = username.trim();

        // Comprobar si el usuario existe
        if (!db.userExists(user)) {
            return LoginResult.INVALID_CREDENTIALS;
        }

        // RS-3: Comprobar si la cuenta está bloqueada
        if (db.isAccountLocked(user)) {
            long secs = db.getLockRemainingSeconds(user);
            System.err.println("[Auth] Login bloqueado para '" + user + "'. Restantes: " + secs + "s");
            return LoginResult.ACCOUNT_LOCKED;
        }

        // RF-3: Verificar contraseña con BCrypt
        String storedHash = db.getPasswordHash(user);
        if (storedHash == null || !BCryptHelper.verify(password, storedHash)) {
            db.incrementFailedAttempts(user);  // RS-3: contar intento fallido
            System.err.println("[Auth] Contraseña incorrecta para '" + user + "'");
            return LoginResult.INVALID_CREDENTIALS;
        }

        // Login correcto: resetear contador y abrir sesión
        db.resetFailedAttempts(user);
        activeSessions.add(user);
        System.out.println("[Auth] Sesión iniciada: " + user);
        return LoginResult.SUCCESS;
    }

    public enum LoginResult {
        SUCCESS,
        INVALID_CREDENTIALS,
        ACCOUNT_LOCKED
    }

    // =========================================================================
    // RF-4  Cerrar sesión
    // =========================================================================

    /**
     * Cierra la sesión de un usuario autenticado.
     *
     * @param username nombre de usuario
     * @return true si la sesión existía y se cerró; false si no había sesión activa
     */
    public boolean logout(String username) {
        boolean removed = activeSessions.remove(username);
        if (removed) {
            System.out.println("[Auth] Sesión cerrada: " + username);
        }
        return removed;
    }

    // =========================================================================
    // Consultas de estado de sesión
    // =========================================================================

    /**
     * Comprueba si un usuario tiene sesión activa.
     */
    public boolean isLoggedIn(String username) {
        return activeSessions.contains(username);
    }

    /**
     * Devuelve el número de sesiones activas en este momento.
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Cierra todas las sesiones activas (útil para parada del servidor).
     */
    public void clearAllSessions() {
        activeSessions.clear();
        System.out.println("[Auth] Todas las sesiones cerradas.");
    }
}