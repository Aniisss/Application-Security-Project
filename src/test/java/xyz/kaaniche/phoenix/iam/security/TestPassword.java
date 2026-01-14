package xyz.kaaniche.phoenix. iam.security;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api. Assertions.assertTrue;

public class TestPassword {
    @Test
    public void generatePassword() throws Exception {
        // YOUR ACTUAL CREDENTIALS
        String username = "Aniisss";           // Your username
        String rawPassword = "password123";    // Your desired password
        
        // Step 1: Create entropy-enhanced password (like Phoenix login form)
        String entropyData = username + "@phoenix.xyz:" + rawPassword;
        MessageDigest sha384 = MessageDigest.getInstance("SHA-384");
        byte[] hashBytes = sha384.digest(entropyData. getBytes(StandardCharsets.UTF_8));
        String entropyPassword = Base64.getEncoder().encodeToString(hashBytes);
        
        // Step 2: Hash with Argon2
        String hash = Argon2Utility. hash(entropyPassword.toCharArray());
        
        // Output the results
        System.out.println("=== Phoenix IAM Password Hash Generator ===");
        System.out.println("Username: " + username);
        System.out.println("Raw Password: " + rawPassword);
        System.out.println("Entropy Password: " + entropyPassword);
        System.out.println("Final Argon2 Hash: " + hash);
        System.out.println();
        System.out.println("=== SQL Command ===");
        System.out. println("UPDATE identities SET password = '" + hash + "' WHERE username = '" + username + "';");
        System.out.println();
        
        // Verify the hash works
        boolean isValid = Argon2Utility.check(hash, entropyPassword.toCharArray());
        System.out.println("Hash verification: " + (isValid ? "✅ PASSED" : "❌ FAILED"));
        
        assertTrue(isValid);
    }
}