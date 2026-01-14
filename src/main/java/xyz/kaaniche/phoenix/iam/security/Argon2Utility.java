package xyz.kaaniche.phoenix.iam.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.security.enterprise.identitystore.PasswordHash;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.logging.Logger;

public class Argon2Utility implements PasswordHash {
    private static final Logger LOGGER = Logger.getLogger(Argon2Utility.class.getName());
    private static final Config config = ConfigProvider.getConfig();

    // Secure Defaults (OWASP Recommendations)
    // 12MB memory, 3 iterations, 1 thread as a baseline
    private static final int SALT_LENGTH = config.getOptionalValue("argon2.saltLength", Integer.class).orElse(16);
    private static final int HASH_LENGTH = config.getOptionalValue("argon2.hashLength", Integer.class).orElse(32);
    private static final int ITERATIONS = config.getOptionalValue("argon2.iterations", Integer.class).orElse(3);
    private static final int MEMORY = config.getOptionalValue("argon2.memory", Integer.class).orElse(12288);
    private static final int THREADS = config.getOptionalValue("argon2.threads", Integer.class).orElse(1);

    // Using a factory to ensure the Argon2 instance is correctly initialized
    private static final Argon2 argon2 = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id, 
            SALT_LENGTH, 
            HASH_LENGTH
    );

    public static String hash(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
        try {
            // Argon2.hash generates its own unique salt per password automatically
            return argon2.hash(ITERATIONS, MEMORY, THREADS, password);
        } finally {
            argon2.wipeArray(password);
        }
    }

    public static boolean check(String hashedPassword, char[] password) {
        if (hashedPassword == null || password == null) {
            return false;
        }
        try {
            // verify() is constant-time to prevent timing attacks
            return argon2.verify(hashedPassword, password);
        } finally {
            argon2.wipeArray(password);
        }
    }

    @Override
    public String generate(char[] password) {
        return hash(password);
    }

    @Override
    public boolean verify(char[] password, String hashedPassword) {
        return check(hashedPassword, password);
    }
}