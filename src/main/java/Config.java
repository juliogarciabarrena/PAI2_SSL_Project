import java.io.*;
import java.util.Properties;

public class Config {

    private static final String CONFIG_FILE = "config.properties";
    private static final Properties props = new Properties();

    static {
        try (InputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
            System.out.println("[Config] Configuración cargada desde " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("[Config] No se encontró " + CONFIG_FILE + ", usando valores por defecto.");
        }
    }

    public static String[] getCipherSuites() {
        String value = props.getProperty(
            "ssl.ciphersuites",
            "TLS_AES_256_GCM_SHA384,TLS_CHACHA20_POLY1305_SHA256,TLS_AES_128_GCM_SHA256"
        );
        return value.split(",");
    }

    public static String getProtocol() {
        return props.getProperty("ssl.protocol", "TLSv1.3");
    }


}