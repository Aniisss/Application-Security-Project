package xyz.kaaniche.phoenix.iam.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.ChaCha20ParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public record AuthorizationCode(
        String tenantName,
        String identityUsername,
        String approvedScopes,
        Long expirationDate,
        String redirectUri) {

    private static final SecretKey key = loadKey(); // persistent key recommended
    private static final String codePrefix = "urn:phoenix:code:";
    private static final int NONCE_LEN = 12;

    private static SecretKey loadKey() {
        try {
            // For production: load from secure config / keystore
            return KeyGenerator.getInstance("ChaCha20").generateKey();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to generate secret key", e);
        }
    }

    public String getCode(String codeChallenge) throws GeneralSecurityException {
        String code = UUID.randomUUID().toString();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((tenantName + ":" + identityUsername + ":" + approvedScopes + ":" + expirationDate + ":" + redirectUri)
                        .getBytes(StandardCharsets.UTF_8));
        byte[] encryptedChallenge = encrypt(codeChallenge.getBytes(StandardCharsets.UTF_8));

        return codePrefix + code + ":" + payload + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedChallenge);
    }

    public static AuthorizationCode decode(String authorizationCode, String codeVerifier) throws GeneralSecurityException {
        int pos = authorizationCode.lastIndexOf(':');
        if (pos < 0) throw new IllegalArgumentException("Invalid code format");

        String cipherCodeChallengeB64 = authorizationCode.substring(pos + 1);
        String codeWithPayload = authorizationCode.substring(0, pos);

        // Decode and verify code challenge
        byte[] decrypted = decrypt(Base64.getUrlDecoder().decode(cipherCodeChallengeB64));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(codeVerifier.getBytes(StandardCharsets.UTF_8));
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());

        if (!expected.equals(new String(decrypted, StandardCharsets.UTF_8))) {
            throw new GeneralSecurityException("Code verifier mismatch");
        }

        // Extract payload
        pos = codeWithPayload.lastIndexOf(':');
        String payloadB64 = codeWithPayload.substring(pos + 1);
        String decoded = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);

        String[] parts = decoded.split(":", 5); // tenant, user, scopes, expiration, redirectUri
        return new AuthorizationCode(parts[0], parts[1], parts[2], Long.parseLong(parts[3]), parts[4]);
    }

    private static byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_LEN];
        new SecureRandom().nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        ChaCha20ParameterSpec spec = new ChaCha20ParameterSpec(nonce, 0);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] ciphertext = cipher.doFinal(plaintext);

        return ByteBuffer.allocate(ciphertext.length + NONCE_LEN)
                .put(ciphertext)
                .put(nonce)
                .array();
    }

    private static byte[] decrypt(byte[] ciphertext) throws GeneralSecurityException {
        if (ciphertext.length < NONCE_LEN) throw new GeneralSecurityException("Invalid ciphertext");

        ByteBuffer bb = ByteBuffer.wrap(ciphertext);
        byte[] encrypted = new byte[ciphertext.length - NONCE_LEN];
        byte[] nonce = new byte[NONCE_LEN];
        bb.get(encrypted);
        bb.get(nonce);

        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        ChaCha20ParameterSpec spec = new ChaCha20ParameterSpec(nonce, 0);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        return cipher.doFinal(encrypted);
    }
}
