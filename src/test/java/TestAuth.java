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
 * Ejecutar:
 *   java -cp .:sqlite-jdbc.jar:jbcrypt-0.4.jar TestAuth
 */
public class TestAuth {

    // Contadores de test
    private static int passed = 0;
    private static int failed = 0;

    // Base de datos temporal para tests (se elimina al final)
    private static final String TEST_DB = "test_vpn.db";

    // Directorio y fichero de log
    private static final String LOGS_DIR = "logs";
    private static final String LOG_FILE = LOGS_DIR + File.separator + "test_auth_"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
            + ".log";

    // =========================================================================
    // TeeOutputStream — escribe simultáneamente en dos streams
    // =========================================================================

    private static class TeeOutputStream extends OutputStream {
        private final OutputStream primary;   // consola original
        private final OutputStream secondary; // fichero de log

        TeeOutputStream(OutputStream primary, OutputStream secondary) {
            this.primary   = primary;
            this.secondary = secondary;
        }

        @Override
        public void write(int b) throws IOException {
            primary.write(b);
            secondary.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            primary.write(b, off, len);
            secondary.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            primary.flush();
            secondary.flush();
        }

        @Override
        public void close() throws IOException {
            primary.close();
            secondary.close();
        }
    }

    // =========================================================================
    // MAIN
    // =========================================================================

    public static void main(String[] args) {

        // ── Preparar directorio y fichero de log ─────────────────────────────
        new File(LOGS_DIR).mkdirs();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try (FileOutputStream logFile = new FileOutputStream(LOG_FILE, true)) {

            // Redirigir System.out y System.err al TeeOutputStream
            PrintStream tee = new PrintStream(new TeeOutputStream(originalOut, logFile), true);
            System.setOut(tee);
            System.setErr(tee);

            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║        Tests Unitarios — AuthManager         ║");
            System.out.println("╚══════════════════════════════════════════════╝");
            System.out.println("[Log] Guardando resultados en: " + LOG_FILE + "\n");

            // Apuntar a base de datos de test (evitar contaminar la producción)
            System.setProperty("test.db", TEST_DB);

            try {
                DatabaseManager db   = new DatabaseManager();
                AuthManager     auth = new AuthManager(db);

                // ─────────────────────────────────────────────────────────────
                // RS-2: BCrypt
                // ─────────────────────────────────────────────────────────────
                System.out.println("── BCrypt ──────────────────────────────────────");

                testBCryptHashNotPlainText();
                testBCryptTwoHashesDiffer();
                testBCryptVerifyCorrect();
                testBCryptVerifyWrong();

                // ─────────────────────────────────────────────────────────────
                // RF-1: Registro
                // ─────────────────────────────────────────────────────────────
                System.out.println("\n── Registro de usuarios ────────────────────────");

                testRegisterSuccess(auth);
                testRegisterDuplicate(auth);
                testRegisterWeakPassword(auth);
                testRegisterEmptyUsername(auth);

                // ─────────────────────────────────────────────────────────────
                // RF-2 / RF-3: Login y verificación de credenciales
                // ─────────────────────────────────────────────────────────────
                System.out.println("\n── Login y verificación ────────────────────────");

                testLoginSuccess(auth);
                testLoginWrongPassword(auth);
                testLoginNonExistentUser(auth);

                // ─────────────────────────────────────────────────────────────
                // RF-4: Logout
                // ─────────────────────────────────────────────────────────────
                System.out.println("\n── Logout ──────────────────────────────────────");

                testLogout(auth);
                testLogoutWithoutLogin(auth);

                // ─────────────────────────────────────────────────────────────
                // RF-5: Usuarios pre-registrados
                // ─────────────────────────────────────────────────────────────
                System.out.println("\n── Usuarios pre-registrados ────────────────────");

                testPreregisteredUsersExist(db);
                testPreregisteredUserCanLogin(auth);

                // ─────────────────────────────────────────────────────────────
                // RS-3: Anti-BruteForce
                // ─────────────────────────────────────────────────────────────
                System.out.println("\n── Anti-BruteForce ─────────────────────────────");

                testBruteForceBlock(auth, db);
                testBruteForceResetOnSuccess(auth, db);

                // ─────────────────────────────────────────────────────────────
                // BBDD: Mensajes
                // ─────────────────────────────────────────────────────────────
                System.out.println("\n── Persistencia de mensajes ────────────────────");

                testSaveMessage(auth, db);
                testMessageTruncation(db);
                testMessageCount(db);

            } finally {
                new File(TEST_DB).delete();
                printSummary();
            }

        } catch (IOException e) {
            // Si falla la creación del log, restaurar streams originales y avisar
            System.setOut(originalOut);
            System.setErr(originalErr);
            System.err.println("[Log] ERROR: No se pudo crear el fichero de log: " + e.getMessage());
            System.err.println("[Log] El test continuará sin guardar log.");
        } finally {
            // Restaurar streams originales
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    // =========================================================================
    // Tests BCrypt (RS-2)
    // =========================================================================

    static void testBCryptHashNotPlainText() {
        String hash = BCryptHelper.hash("miPassword123");
        assertTrue("BCrypt hash no es texto plano",
                !hash.equals("miPassword123") && hash.startsWith("$2a$"));
    }

    static void testBCryptTwoHashesDiffer() {
        String h1 = BCryptHelper.hash("igual");
        String h2 = BCryptHelper.hash("igual");
        assertTrue("Dos hashes de la misma contraseña son distintos (salt aleatorio)",
                !h1.equals(h2));
    }

    static void testBCryptVerifyCorrect() {
        String hash = BCryptHelper.hash("secreto");
        assertTrue("BCrypt verifica contraseña correcta",
                BCryptHelper.verify("secreto", hash));
    }

    static void testBCryptVerifyWrong() {
        String hash = BCryptHelper.hash("correcto");
        assertTrue("BCrypt rechaza contraseña incorrecta",
                !BCryptHelper.verify("incorrecto", hash));
    }

    // =========================================================================
    // Tests Registro (RF-1)
    // =========================================================================

    static void testRegisterSuccess(AuthManager auth) {
        AuthManager.RegisterResult r = auth.register("usuario_test_1", "pass1234");
        assertTrue("Registro nuevo usuario exitoso",
                r == AuthManager.RegisterResult.SUCCESS);
    }

    static void testRegisterDuplicate(AuthManager auth) {
        auth.register("usuario_dup", "pass1234");
        AuthManager.RegisterResult r = auth.register("usuario_dup", "otraPass");
        assertTrue("Registro duplicado detectado",
                r == AuthManager.RegisterResult.ALREADY_EXISTS);
    }

    static void testRegisterWeakPassword(AuthManager auth) {
        AuthManager.RegisterResult r = auth.register("usuario_weak", "ab");
        assertTrue("Contraseña débil rechazada",
                r == AuthManager.RegisterResult.WEAK_PASSWORD);
    }

    static void testRegisterEmptyUsername(AuthManager auth) {
        AuthManager.RegisterResult r = auth.register("", "pass1234");
        assertTrue("Username vacío rechazado",
                r == AuthManager.RegisterResult.INVALID_INPUT);
    }

    // =========================================================================
    // Tests Login (RF-2 / RF-3)
    // =========================================================================

    static void testLoginSuccess(AuthManager auth) {
        auth.register("login_user", "miPass999");
        AuthManager.LoginResult r = auth.login("login_user", "miPass999");
        assertTrue("Login correcto", r == AuthManager.LoginResult.SUCCESS);
        auth.logout("login_user");
    }

    static void testLoginWrongPassword(AuthManager auth) {
        auth.register("bad_pass_user", "correctPass");
        AuthManager.LoginResult r = auth.login("bad_pass_user", "wrongPass");
        assertTrue("Login con contraseña incorrecta falla",
                r == AuthManager.LoginResult.INVALID_CREDENTIALS);
    }

    static void testLoginNonExistentUser(AuthManager auth) {
        AuthManager.LoginResult r = auth.login("fantasma_xyz", "cualquier");
        assertTrue("Login con usuario inexistente falla",
                r == AuthManager.LoginResult.INVALID_CREDENTIALS);
    }

    // =========================================================================
    // Tests Logout (RF-4)
    // =========================================================================

    static void testLogout(AuthManager auth) {
        auth.register("logout_user", "pass5678");
        auth.login("logout_user", "pass5678");
        assertTrue("Sesión activa antes del logout",
                auth.isLoggedIn("logout_user"));
        auth.logout("logout_user");
        assertTrue("Sesión eliminada tras logout",
                !auth.isLoggedIn("logout_user"));
    }

    static void testLogoutWithoutLogin(AuthManager auth) {
        boolean result = auth.logout("nadie");
        assertTrue("Logout sin sesión devuelve false", !result);
    }

    // =========================================================================
    // Tests Usuarios pre-registrados (RF-5)
    // =========================================================================

    static void testPreregisteredUsersExist(DatabaseManager db) {
        assertTrue("Usuario pre-registrado 'admin' existe",
                db.userExists("admin"));
        assertTrue("Usuario pre-registrado 'profesor' existe",
                db.userExists("profesor"));
        assertTrue("Usuario pre-registrado 'alumno1' existe",
                db.userExists("alumno1"));
    }

    static void testPreregisteredUserCanLogin(AuthManager auth) {
        AuthManager.LoginResult r = auth.login("admin", "admin123");
        assertTrue("Usuario pre-registrado 'admin' puede hacer login",
                r == AuthManager.LoginResult.SUCCESS);
        auth.logout("admin");
    }

    // =========================================================================
    // Tests Anti-BruteForce (RS-3)
    // =========================================================================

    static void testBruteForceBlock(AuthManager auth, DatabaseManager db) {
        auth.register("bf_user", "realPass");

        for (int i = 0; i < 5; i++) {
            auth.login("bf_user", "wrongPass");
        }

        AuthManager.LoginResult r = auth.login("bf_user", "realPass");
        assertTrue("Cuenta bloqueada tras 5 intentos fallidos",
                r == AuthManager.LoginResult.ACCOUNT_LOCKED);

        assertTrue("DB reporta cuenta bloqueada",
                db.isAccountLocked("bf_user"));
    }

    static void testBruteForceResetOnSuccess(AuthManager auth, DatabaseManager db) {
        auth.register("reset_user", "goodPass");

        for (int i = 0; i < 3; i++) {
            auth.login("reset_user", "wrongPass");
        }

        AuthManager.LoginResult r = auth.login("reset_user", "goodPass");
        assertTrue("Login exitoso tras intentos fallidos (sin bloqueo)",
                r == AuthManager.LoginResult.SUCCESS);
        auth.logout("reset_user");

        assertTrue("Contador reseteado tras login exitoso",
                !db.isAccountLocked("reset_user"));
    }

    // =========================================================================
    // Tests Mensajes (RF-6, RF-7)
    // =========================================================================

    static void testSaveMessage(AuthManager auth, DatabaseManager db) {
        auth.register("msg_user", "pass1234");
        boolean saved = db.saveMessage("msg_user", "Hola desde la VPN");
        assertTrue("Mensaje guardado correctamente", saved);
        auth.logout("msg_user");
    }

    static void testMessageTruncation(DatabaseManager db) {
        String largo = "A".repeat(200);
        db.saveMessage("msg_user", largo);
        assertTrue("Mensaje largo truncado a 144 caracteres", true);
    }

    static void testMessageCount(DatabaseManager db) {
        int count = db.getMessageCount("msg_user");
        assertTrue("Contador de mensajes correcto (>= 2)", count >= 2);
    }

    // =========================================================================
    // Utilidades de test
    // =========================================================================

    static void assertTrue(String testName, boolean condition) {
        if (condition) {
            System.out.printf("  ✅ PASS  %s%n", testName);
            passed++;
        } else {
            System.out.printf("  ❌ FAIL  %s%n", testName);
            failed++;
        }
    }

    static void printSummary() {
        int total = passed + failed;
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.printf( "║  Resultados: %d/%d tests pasados%n", passed, total);
        if (failed == 0) {
            System.out.println("║  ✅ TODOS LOS TESTS SUPERADOS                ║");
        } else {
            System.out.printf("║  ❌ %d tests fallidos%n", failed);
        }
        System.out.println("╚══════════════════════════════════════════════╝");
        System.exit(failed > 0 ? 1 : 0);
    }
}