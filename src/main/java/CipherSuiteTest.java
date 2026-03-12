import javax.net.ssl.*;
import java.io.*;

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
 *        -jar target/performance-test.jar
 *
 * Resultado esperado: ✅ para cada cipher suite configurada
 */
public class CipherSuiteTest {

    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 8443;

    // Resultado de un test individual
    private static class TestResult {
        final String  cipherSuite;
        final boolean success;
        final String  negotiatedProtocol;
        final String  negotiatedCipher;
        final long    handshakeMs;
        final String  errorMessage;

        TestResult(String cipherSuite, boolean success, String negotiatedProtocol,
                   String negotiatedCipher, long handshakeMs, String errorMessage) {
            this.cipherSuite        = cipherSuite;
            this.success            = success;
            this.negotiatedProtocol = negotiatedProtocol;
            this.negotiatedCipher   = negotiatedCipher;
            this.handshakeMs        = handshakeMs;
            this.errorMessage       = errorMessage;
        }
    }

    // -------------------------------------------------------------------------
    // MAIN
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws InterruptedException {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        String[] cipherSuites = Config.getCipherSuites();
        String   protocol     = Config.getProtocol();

        System.out.println("╔═════════════════════════════════════════════════════════════╗");
        System.out.println("║         Test de Cipher Suites — VPN SSL (TLS 1.3)           ║");
        System.out.println("╚═════════════════════════════════════════════════════════════╝");
        System.out.println("[Test] Servidor : " + host + ":" + port);
        System.out.println("[Test] Protocolo: " + protocol);
        System.out.println("[Test] Cipher suites a probar: " + cipherSuites.length);
        for (String cs : cipherSuites) {
            System.out.println("         • " + cs);
        }
        System.out.println();

        // Ejecutar un test por cada cipher suite
        TestResult[] results = new TestResult[cipherSuites.length];
        for (int i = 0; i < cipherSuites.length; i++) {
            System.out.printf("[Test] (%d/%d) Probando: %s%n",
                    i + 1, cipherSuites.length, cipherSuites[i]);
            results[i] = testCipherSuite(host, port, protocol, cipherSuites[i]);
            printSingleResult(results[i]);
            // Pequeña pausa entre tests para no saturar el servidor
            Thread.sleep(500);
        }

        // Reporte final
        printFinalReport(results, protocol);
    }

    // -------------------------------------------------------------------------
    // TEST DE UN CIPHER SUITE
    // -------------------------------------------------------------------------

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
            String negotiatedCipher   = session.getCipherSuite();

            socket.close();

            // Verificar que lo negociado coincide con lo solicitado
            boolean protocolOk = protocol.equals(negotiatedProtocol);
            boolean cipherOk   = cipherSuite.equals(negotiatedCipher);
            boolean success    = protocolOk && cipherOk;

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
        }
    }

    // -------------------------------------------------------------------------
    // RESULTADO INDIVIDUAL
    // -------------------------------------------------------------------------

    private static void printSingleResult(TestResult r) {
        if (r.success) {
            System.out.printf("  ✅ OK  |  Protocolo: %-10s  |  Cipher: %-40s  |  Handshake: %d ms%n",
                    r.negotiatedProtocol, r.negotiatedCipher, r.handshakeMs);
        } else {
            System.out.printf("  ❌ FAIL |  %s%n", r.errorMessage);
        }
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // REPORTE FINAL
    // -------------------------------------------------------------------------

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

        System.out.println("╔═════════════════════════════════════════════════════════════╗");
        System.out.println("║                    REPORTE FINAL                            ║");
        System.out.println("╚═════════════════════════════════════════════════════════════╝\n");

        // Tabla de resultados
        System.out.printf("%-44s  %-10s  %-12s  %s%n",
                "Cipher Suite", "Resultado", "Handshake", "Detalle");
        System.out.println("-".repeat(90));

        for (TestResult r : results) {
            if (r.success) {
                System.out.printf("%-44s  %-10s  %-9d ms  Protocolo: %s%n",
                        r.cipherSuite, "✅ PASS", r.handshakeMs, r.negotiatedProtocol);
            } else {
                System.out.printf("%-44s  %-10s  %-9d ms  %s%n",
                        r.cipherSuite, "❌ FAIL", r.handshakeMs, r.errorMessage);
            }
        }

        System.out.println("-".repeat(90));
        System.out.printf("%n  • Tests ejecutados : %d%n", results.length);
        System.out.printf("  • Pasados          : %d ✅%n", passed);
        System.out.printf("  • Fallados         : %d ❌%n", failed);
        System.out.printf("  • Handshake medio  : %.2f ms%n", avgHandshake);
        System.out.printf("  • Tasa de éxito    : %.1f%%%n%n",
                results.length > 0 ? (100.0 * passed) / results.length : 0);

        // Veredicto
        System.out.println("=".repeat(60));
        if (failed == 0) {
            System.out.println("  ✅ TODOS los cipher suites funcionan correctamente");
            System.out.println("  ✅ Protocolo " + protocol + " verificado en todas las conexiones");
        } else {
            System.out.println("  ⚠️  Algunos cipher suites fallaron — revisar config.properties");
            System.out.println("     y verificar que el servidor los tiene habilitados.");
            System.out.println();
            System.out.println("  Cipher suites con error:");
            for (TestResult r : results) {
                if (!r.success)
                    System.out.println("    ❌ " + r.cipherSuite + " → " + r.errorMessage);
            }
        }
        System.out.println("=".repeat(60) + "\n");
    }
}
