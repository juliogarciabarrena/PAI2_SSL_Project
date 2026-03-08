/**
 * BCryptHelper — utilidad para hash y verificación de contraseñas con BCrypt.
 * 
 * Usa la librería org.mindrot.jbcrypt (jbcrypt-0.4.jar).
 * Factor de coste 12 — equilibrio seguridad/rendimiento para un servidor universitario.
 * 
 * Requisito RS-2: Almacenamiento seguro de contraseñas mediante hash robusto.
 */
public class BCryptHelper {

    // Factor de coste BCrypt (2^12 iteraciones = ~250ms por hash en hardware moderno)
    private static final int COST_FACTOR = 12;

    /**
     * Genera un hash BCrypt a partir de una contraseña en texto plano.
     *
     * @param plainPassword contraseña en texto plano
     * @return hash BCrypt listo para almacenar en base de datos
     */
    public static String hash(String plainPassword) {
        return org.mindrot.jbcrypt.BCrypt.hashpw(plainPassword, org.mindrot.jbcrypt.BCrypt.gensalt(COST_FACTOR));
    }

    /**
     * Verifica si una contraseña en texto plano coincide con un hash BCrypt almacenado.
     *
     * @param plainPassword contraseña introducida por el usuario
     * @param storedHash    hash almacenado en base de datos
     * @return true si la contraseña es correcta
     */
    public static boolean verify(String plainPassword, String storedHash) {
        try {
            return org.mindrot.jbcrypt.BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException e) {
            // Hash inválido o corrupto
            System.err.println("[BCrypt] Hash inválido al verificar: " + e.getMessage());
            return false;
        }
    }

    /**
     * Comprueba si un hash BCrypt necesita ser re-hasheado (factor de coste insuficiente).
     * Útil para migrar usuarios a un factor más alto en el futuro.
     *
     * @param storedHash hash almacenado en base de datos
     * @return true si el coste del hash es inferior al actual COST_FACTOR
     */
    public static boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.length() < 7) return true;
        try {
            int storedCost = Integer.parseInt(storedHash.substring(4, 6));
            return storedCost < COST_FACTOR;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}