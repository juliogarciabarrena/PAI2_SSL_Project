import javax.net.ssl.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CipherSuiteTest — Verifica que el cliente puede conectarse al servidor
 * con cada cipher suite de TLS 1.3 definida en config.properties.
 *
 * Para cada cipher suite:
 *   1. Abre una SSLSocket forzando únicamente esa cipher suite
 *   2. Realiza el handshake TLS 1.3
 *   3. Verifica que el protocolo y cipher suite negociados son los esperados
 *   4. Cierra la conexión limpiamente
 *
 * Ejecución:
 *   java -Djavax.net.ssl.trustStore=certs/truststore.jks \
 *        -Djavax.net.ssl.trustStorePassword=PAI2password \
 *        -cp target/classes:target/dependency-jars/* \
 *        CipherSuiteTest [host] [puerto]
 *
 * Por defecto: localhost 8443
 *
 * Log: Se guarda automáticamente en logs/cipher_suite_test_YYYY-MM-DD_HHMM.log
 *
 * Resultado esperado: ✅ para cada cipher suite configurada
 */
public class CipherSuiteTest {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8443;
    
    // PrintWriter para escribir el log
    private static PrintWriter logWriter;
    private static StringBuilder logBuffer;

    /**
     * Resultado de un test individual
     */
    private static class TestResult {
        final String cipherSuite;
        final boolean success;
        final String negotiatedProtocol;
        final String negotiatedCipher;
        final long handshakeMs;
        final String errorMessage;

        TestResult(String cipherSuite, boolean success, String negotiatedProtocol,
                   String negotiatedCipher, long handshakeMs, String errorMessage) {
            this.cipherSuite = cipherSuite;
            this.success = success;
            this.negotiatedProtocol = negotiatedProtocol;
            this.negotiatedCipher = negotiatedCipher;
            this.handshakeMs = handshakeMs;
            this.errorMessage = errorMessage;
        }
    }

    // =========================================================================
    // MAIN
    // =========================================================================

    public static void main(String[] args) throws InterruptedException {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        String[] cipherSuites = Config.getCipherSuites();
        String protocol = Config.getProtocol();
        
        // Inicializar log
        initializeLog();

        println("\n╔═════════════════════════════════════════════════════════════╗");
        println("║         Test de Cipher Suites — VPN SSL (TLS 1.3)           ║");
        println("╚═════════════════════════════════════════════════════════════╝\n");
        println("[Test] Servidor : " + host + ":" + port);
        println("[Test] Protocolo: " + protocol);
        println("[Test] Cipher suites a probar: " + cipherSuites.length);
        for (String cs : cipherSuites) {
            println("         • " + cs);
        }
        println("");

        // Ejecutar un test por cada cipher suite
        TestResult[] results = new TestResult[cipherSuites.length];
        for (int i = 0; i < cipherSuites.length; i++) {
            println(String.format("[Test] (%d/%d) Probando: %s",
                    i + 1, cipherSuites.length, cipherSuites[i]));
            results[i] = testCipherSuite(host, port, protocol, cipherSuites[i]);
            printSingleResult(results[i]);
            // Pequeña pausa entre tests para no saturar el servidor
            Thread.sleep(500);
        }

        // Reporte final
        printFinalReport(results, protocol);
        
        // Cerrar el log
        closeLog();
    }

    // =========================================================================
    // TEST DE UN CIPHER SUITE
    // =========================================================================

    private static TestResult testCipherSuite(String host, int port,
                                               String protocol, String cipherSuite) {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        long startTime = System.currentTimeMillis();

        try {
            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);

            // Forzar únicamente este cipher suite y protocolo
            socket.setEnabledProtocols(new String[]{protocol});
            socket.setEnabledCipherSuites(new String[]{cipherSuite});

            // Realizar handshake
            socket.startHandshake();
            long handshakeMs = System.currentTimeMillis() - startTime;

            // Leer los valores realmente negociados
            SSLSession session = socket.getSession();
            String negotiatedProtocol = session.getProtocol();
            String negotiatedCipher = session.getCipherSuite();

            socket.close();

            // Verificar que lo negociado coincide con lo solicitado
            boolean protocolOk = protocol.equals(negotiatedProtocol);
            boolean cipherOk = cipherSuite.equals(negotiatedCipher);
            boolean success = protocolOk && cipherOk;

            String errorMsg = null;
            if (!protocolOk)
                errorMsg = "Protocolo negociado incorrecto: " + negotiatedProtocol;
            if (!cipherOk)
                errorMsg = (errorMsg != null ? errorMsg + " | " : "")
                        + "Cipher negociado incorrecto: " + negotiatedCipher;

            return new TestResult(cipherSuite, success, negotiatedProtocol,
                    negotiatedCipher, handshakeMs, errorMsg);

        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            return new TestResult(cipherSuite, false, "N/A", "N/A",
                    elapsed, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            return new TestResult(cipherSuite, false, "N/A", "N/A",
                    elapsed, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // =========================================================================
    // RESULTADO INDIVIDUAL
    // =========================================================================

    private static void printSingleResult(TestResult r) {
        if (r.success) {
            println(String.format("  ✅ OK  |  Protocolo: %-10s  |  Cipher: %-40s  |  Handshake: %d ms",
                    r.negotiatedProtocol, r.negotiatedCipher, r.handshakeMs));
        } else {
            println(String.format("  ❌ FAIL |  %s", r.errorMessage));
        }
        println("");
    }

    // =========================================================================
    // REPORTE FINAL
    // =========================================================================

    private static void printFinalReport(TestResult[] results, String protocol) {
        int passed = 0;
        int failed = 0;
        long totalHandshakeMs = 0;

        for (TestResult r : results) {
            if (r.success) {
                passed++;
                totalHandshakeMs += r.handshakeMs;
            } else {
                failed++;
            }
        }

        double avgHandshake = passed > 0 ? (double) totalHandshakeMs / passed : 0;

        println("╔═════════════════════════════════════════════════════════════╗");
        println("║                    REPORTE FINAL                            ║");
        println("╚═════════════════════════════════════════════════════════════╝\n");

        // Tabla de resultados
        println(String.format("%-44s  %-10s  %-12s  %s",
                "Cipher Suite", "Resultado", "Handshake", "Detalle"));
        println("-".repeat(90));

        for (TestResult r : results) {
            if (r.success) {
                println(String.format("%-44s  %-10s  %-9d ms  Protocolo: %s",
                        r.cipherSuite, "✅ PASS", r.handshakeMs, r.negotiatedProtocol));
            } else {
                println(String.format("%-44s  %-10s  %-9d ms  %s",
                        r.cipherSuite, "❌ FAIL", r.handshakeMs, r.errorMessage));
            }
        }

        println("-".repeat(90));
        println(String.format("%n  • Tests ejecutados : %d", results.length));
        println(String.format("  • Pasados          : %d ✅", passed));
        println(String.format("  • Fallados         : %d ❌", failed));
        println(String.format("  • Handshake medio  : %.2f ms", avgHandshake));
        println(String.format("  • Tasa de éxito    : %.1f%%%n",
                results.length > 0 ? (100.0 * passed) / results.length : 0));

        // Veredicto
        println("=".repeat(90));
        if (failed == 0) {
            println("  ✅ TODOS los cipher suites funcionan correctamente");
            println("  ✅ Protocolo " + protocol + " verificado en todas las conexiones");
        } else {
            println("  ⚠️  Algunos cipher suites fallaron — revisar config.properties");
            println("     y verificar que el servidor los tiene habilitados.");
            println("");
            println("  Cipher suites con error:");
            for (TestResult r : results) {
                if (!r.success)
                    println("    ❌ " + r.cipherSuite + " → " + r.errorMessage);
            }
        }
        println("=".repeat(90) + "\n");
    }

    // =========================================================================
    // UTILIDADES DE LOG
    // =========================================================================

    /**
     * Inicializa el archivo de log en la carpeta logs/
     */
    private static void initializeLog() {
        try {
            // Crear carpeta logs si no existe
            Path logsDir = Paths.get("logs");
            Files.createDirectories(logsDir);

            // Generar nombre del archivo con fecha y hora
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");
            String timestamp = LocalDateTime.now().format(formatter);
            String logFilename = "logs/cipher_suite_test_" + timestamp + ".log";

            // Crear el archivo de log
            logWriter = new PrintWriter(new FileWriter(logFilename, true), true);
            logBuffer = new StringBuilder();

            println("\n[Log] Guardando en: " + logFilename);

        } catch (IOException e) {
            System.err.println("Error al crear archivo de log: " + e.getMessage());
        }
    }

    /**
     * Imprime en pantalla Y guarda en el log simultáneamente
     */
    private static void println(String message) {
        // Mostrar en pantalla
        System.out.println(message);

        // Guardar en log (si está inicializado)
        if (logWriter != null) {
            logWriter.println(message);
            logWriter.flush();
        }
    }

    /**
     * Cierra el archivo de log
     */
    private static void closeLog() {
        if (logWriter != null) {
            logWriter.close();
            System.out.println("[Log] Archivo guardado correctamente");
        }
    }
}