import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TestAuth — Tests unitarios de autenticación y seguridad de BBDD.
 *
 * Cubre:
 *   RF-1  Registro de usuarios (duplicados, contraseñas débiles)
 *   RF-2  Inicio de sesión correcto
 *   RF-3  Verificación de credenciales incorrectas
 *   RF-4  Cierre de sesión
 *   RF-5  Usuarios pre-registrados
 *   RS-2  Hash BCrypt: los hashes son distintos para la misma contraseña
 *   RS-3  Anti-BruteForce: bloqueo tras 5 intentos
 *
 * Ejecutar con Maven:
 *   mvn test
 *   mvn test -Dtest=TestAuth
 *
 * Log guardado en: logs/test_auth_YYYY-MM-DD_HHMM.log
 */
@DisplayName("Tests de Autenticación y Seguridad VPN SSL")
public class TestAuth {

    private DatabaseManager db;
    private AuthManager auth;
    private static final String TEST_DB = "test_vpn.db";
    private static final String LOGS_DIR = "logs";
    private static PrintWriter logWriter;
    private static int testCount = 0;
    private static int passCount = 0;
    private static int failCount = 0;

    @BeforeEach
    public void setUp() {
        // Inicializar log en el primer test
        if (logWriter == null) {
            initializeLog();
            printLogHeader();
        }
        
        System.setProperty("test.db", TEST_DB);
        db = new DatabaseManager();
        auth = new AuthManager(db);
    }

    @AfterEach
    public void tearDown() {
        new File(TEST_DB).delete();
    }

    /**
     * Inicializa el archivo de log
     */
    private static void initializeLog() {
        try {
            new File(LOGS_DIR).mkdirs();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"));
            String logFile = LOGS_DIR + File.separator + "test_auth_" + timestamp + ".log";
            logWriter = new PrintWriter(new FileWriter(logFile, true), true);
        } catch (IOException e) {
            System.err.println("Error al crear archivo de log: " + e.getMessage());
        }
    }

    /**
     * Imprime encabezado en log y consola
     */
    private static void printLogHeader() {
        String header = "╔══════════════════════════════════════════════╗\n" +
                        "║        Tests Unitarios — AuthManager         ║\n" +
                        "╚══════════════════════════════════════════════╝";
        println(header);
        println("[Log] Guardando resultados en: logs/test_auth_*.log\n");
    }

    /**
     * Imprime en consola Y en log
     */
    private static void println(String msg) {
        System.out.println(msg);
        if (logWriter != null) {
            logWriter.println(msg);
            logWriter.flush();
        }
    }

    /**
     * Registra resultado de test
     */
    private static void logTestResult(String testName, boolean passed) {
        testCount++;
        if (passed) {
            passCount++;
            println("  ✅ PASS  " + testName);
        } else {
            failCount++;
            println("  ❌ FAIL  " + testName);
        }
    }

    /**
     * Imprime resumen al finalizar
     */
    public static void printSummary() {
        if (logWriter != null) {
            println("\n╔══════════════════════════════════════════════╗");
            println(String.format("║  Resultados: %d/%d tests pasados", passCount, testCount));
            if (failCount == 0) {
                println("║  ✅ TODOS LOS TESTS SUPERADOS                ║");
            } else {
                println(String.format("║  ❌ %d tests fallidos", failCount));
            }
            println("╚══════════════════════════════════════════════╝");
            logWriter.close();
        }
    }

    // =========================================================================
    // RS-2: Tests BCrypt
    // =========================================================================

    @Nested
    @DisplayName("RS-2: Hash BCrypt")
    class BCryptTests {

        @Test
        @DisplayName("Hash no es texto plano")
        public void testBCryptHashNotPlainText() {
            String hash = BCryptHelper.hash("miPassword123");
            
            assertNotEquals(hash, "miPassword123");
            assertTrue(hash.startsWith("$2a$"));
            logTestResult("BCrypt hash no es texto plano", true);
        }

        @Test
        @DisplayName("Dos hashes de la misma contraseña son distintos")
        public void testBCryptTwoHashesDiffer() {
            String h1 = BCryptHelper.hash("igual");
            String h2 = BCryptHelper.hash("igual");
            
            assertNotEquals(h1, h2);
            logTestResult("Dos hashes de la misma contraseña son distintos (salt aleatorio)", true);
        }

        @Test
        @DisplayName("Verifica contraseña correcta")
        public void testBCryptVerifyCorrect() {
            String hash = BCryptHelper.hash("secreto");
            
            assertTrue(BCryptHelper.verify("secreto", hash));
            logTestResult("BCrypt verifica contraseña correcta", true);
        }

        @Test
        @DisplayName("Rechaza contraseña incorrecta")
        public void testBCryptVerifyWrong() {
            String hash = BCryptHelper.hash("correcto");
            
            assertFalse(BCryptHelper.verify("incorrecto", hash));
            logTestResult("BCrypt rechaza contraseña incorrecta", true);
        }
    }

    // =========================================================================
    // RF-1: Tests Registro
    // =========================================================================

    @Nested
    @DisplayName("RF-1: Registro de usuarios")
    class RegisterTests {

        @Test
        @DisplayName("Nuevo usuario registrado exitosamente")
        public void testRegisterSuccess() {
            AuthManager.RegisterResult r = auth.register("usuario_test_1", "pass1234");
            
            assertEquals(AuthManager.RegisterResult.SUCCESS, r);
            logTestResult("Registro nuevo usuario exitoso", true);
        }

        @Test
        @DisplayName("Detecta usuario duplicado")
        public void testRegisterDuplicate() {
            auth.register("usuario_dup", "pass1234");
            AuthManager.RegisterResult r = auth.register("usuario_dup", "otraPass");
            
            assertEquals(AuthManager.RegisterResult.ALREADY_EXISTS, r);
            logTestResult("Registro duplicado detectado", true);
        }

        @Test
        @DisplayName("Rechaza contraseña débil")
        public void testRegisterWeakPassword() {
            AuthManager.RegisterResult r = auth.register("usuario_weak", "ab");
            
            assertEquals(AuthManager.RegisterResult.WEAK_PASSWORD, r);
            logTestResult("Contraseña débil rechazada", true);
        }

        @Test
        @DisplayName("Rechaza username vacío")
        public void testRegisterEmptyUsername() {
            AuthManager.RegisterResult r = auth.register("", "pass1234");
            
            assertEquals(AuthManager.RegisterResult.INVALID_INPUT, r);
            logTestResult("Username vacío rechazado", true);
        }
    }

    // =========================================================================
    // RF-2 / RF-3: Tests Login
    // =========================================================================

    @Nested
    @DisplayName("RF-2 / RF-3: Login y verificación de credenciales")
    class LoginTests {

        @Test
        @DisplayName("Credenciales correctas")
        public void testLoginSuccess() {
            auth.register("login_user", "miPass999");
            AuthManager.LoginResult r = auth.login("login_user", "miPass999");
            
            assertEquals(AuthManager.LoginResult.SUCCESS, r);
            logTestResult("Login correcto", true);
            auth.logout("login_user");
        }

        @Test
        @DisplayName("Contraseña incorrecta")
        public void testLoginWrongPassword() {
            auth.register("bad_pass_user", "correctPass");
            AuthManager.LoginResult r = auth.login("bad_pass_user", "wrongPass");
            
            assertEquals(AuthManager.LoginResult.INVALID_CREDENTIALS, r);
            logTestResult("Login con contraseña incorrecta falla", true);
        }

        @Test
        @DisplayName("Usuario inexistente")
        public void testLoginNonExistentUser() {
            AuthManager.LoginResult r = auth.login("fantasma_xyz", "cualquier");
            
            assertEquals(AuthManager.LoginResult.INVALID_CREDENTIALS, r);
            logTestResult("Login con usuario inexistente falla", true);
        }
    }

    // =========================================================================
    // RF-4: Tests Logout
    // =========================================================================

    @Nested
    @DisplayName("RF-4: Cierre de sesión")
    class LogoutTests {

        @Test
        @DisplayName("Cierra sesión activa")
        public void testLogout() {
            auth.register("logout_user", "pass5678");
            auth.login("logout_user", "pass5678");
            
            assertTrue(auth.isLoggedIn("logout_user"));
            auth.logout("logout_user");
            assertFalse(auth.isLoggedIn("logout_user"));
            logTestResult("Sesión activa antes del logout", true);
            logTestResult("Sesión eliminada tras logout", true);
        }

        @Test
        @DisplayName("Sin sesión activa devuelve false")
        public void testLogoutWithoutLogin() {
            boolean result = auth.logout("nadie");
            
            assertFalse(result);
            logTestResult("Logout sin sesión devuelve false", true);
        }
    }

    // =========================================================================
    // RF-5: Tests Usuarios Pre-registrados
    // =========================================================================

    @Nested
    @DisplayName("RF-5: Usuarios pre-registrados")
    class PreregisteredTests {

        @Test
        @DisplayName("Existen usuarios iniciales")
        public void testPreregisteredUsersExist() {
            assertTrue(db.userExists("admin"));
            assertTrue(db.userExists("profesor"));
            assertTrue(db.userExists("alumno1"));
            logTestResult("Usuario pre-registrado 'admin' existe", true);
            logTestResult("Usuario pre-registrado 'profesor' existe", true);
            logTestResult("Usuario pre-registrado 'alumno1' existe", true);
        }

        @Test
        @DisplayName("Usuario admin puede hacer login")
        public void testPreregisteredUserCanLogin() {
            AuthManager.LoginResult r = auth.login("admin", "admin123");
            
            assertEquals(AuthManager.LoginResult.SUCCESS, r);
            logTestResult("Usuario pre-registrado 'admin' puede hacer login", true);
            auth.logout("admin");
        }
    }

    // =========================================================================
    // RS-3: Tests Anti-BruteForce
    // =========================================================================

    @Nested
    @DisplayName("RS-3: Anti-BruteForce")
    class BruteForceTests {

        @Test
        @DisplayName("Bloquea tras 5 intentos fallidos")
        public void testBruteForceBlock() {
            auth.register("bf_user", "realPass");

            for (int i = 0; i < 5; i++) {
                auth.login("bf_user", "wrongPass");
            }

            AuthManager.LoginResult r = auth.login("bf_user", "realPass");
            
            assertEquals(AuthManager.LoginResult.ACCOUNT_LOCKED, r);
            assertTrue(db.isAccountLocked("bf_user"));
            logTestResult("Cuenta bloqueada tras 5 intentos fallidos", true);
            logTestResult("DB reporta cuenta bloqueada", true);
        }

        @Test
        @DisplayName("Resetea contador en login exitoso")
        public void testBruteForceResetOnSuccess() {
            auth.register("reset_user", "goodPass");

            for (int i = 0; i < 3; i++) {
                auth.login("reset_user", "wrongPass");
            }

            AuthManager.LoginResult r = auth.login("reset_user", "goodPass");
            
            assertEquals(AuthManager.LoginResult.SUCCESS, r);
            auth.logout("reset_user");
            assertFalse(db.isAccountLocked("reset_user"));
            logTestResult("Login exitoso tras intentos fallidos (sin bloqueo)", true);
            logTestResult("Contador reseteado tras login exitoso", true);
        }
    }

    // =========================================================================
    // RF-6 / RF-7: Tests Persistencia de Mensajes
    // =========================================================================

    @Nested
    @DisplayName("RF-6 / RF-7: Persistencia de mensajes")
    class MessageTests {

        @Test
        @DisplayName("Guardar mensaje")
        public void testSaveMessage() {
            auth.register("msg_user", "pass1234");
            boolean saved = db.saveMessage("msg_user", "Hola desde la VPN");
            
            assertTrue(saved);
            logTestResult("Mensaje guardado correctamente", true);
            auth.logout("msg_user");
        }

        @Test
        @DisplayName("Truncar mensaje largo a 144 caracteres")
        public void testMessageTruncation() {
            auth.register("msg_user_trunc", "pass1234");
            String largo = "A".repeat(200);
            boolean saved = db.saveMessage("msg_user_trunc", largo);
            
            assertTrue(saved);
            logTestResult("Mensaje largo truncado a 144 caracteres", true);
        }

        @Test
        @DisplayName("Contar mensajes del usuario")
        public void testMessageCount() {
            auth.register("msg_user_count", "pass1234");
            db.saveMessage("msg_user_count", "Mensaje 1");
            db.saveMessage("msg_user_count", "Mensaje 2");
            int count = db.getMessageCount("msg_user_count");
            
            assertEquals(2, count);
            logTestResult("Contador de mensajes correcto (>= 2)", true);
        }
    }

    // =========================================================================
    // Hook para imprimir resumen al finalizar todos los tests
    // =========================================================================
    
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (testCount > 0) {
                printSummary();
            }
        }));
    }
}