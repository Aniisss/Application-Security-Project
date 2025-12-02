package xyz.kaaniche.phoenix.iam.security;

import com.google.crypto.tink.subtle.XChaCha20Poly1305;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JwtManagerTest {
    private static JwtManager manager;

    @BeforeAll
    public static void setUp(){
        manager = new JwtManager();
        manager.start();
    }

    @Test
    public void testGenerateJWT(){
        String token = manager.generateAccessToken("api", "alice","resource.read resource.write", new String[]{"manager", "surfer"});
        System.out.println(token);
        assertNotNull(token);
        long last = 1L<<62L,sum=1L,i=1L;//k=1L;
        while(i<last){
            i=(i<<1L);
            //System.out.printf("R_P%02d(1L<<%02dL),",k,k++);
            sum+=i;
        }
        assertEquals(Long.MAX_VALUE,sum);
    }

    @Test
    public void testXChaCha20Poly1305() throws GeneralSecurityException {
        String associatedData = "urn:phoenix:code:dummy_random:more_info";
        byte[] key = KeyGenerator.getInstance("CHACHA20").generateKey().getEncoded();
        XChaCha20Poly1305 cipherDecipher = new XChaCha20Poly1305(key);
        String plainText = "Hello! I am a challenge! Can you solve me?";
        byte[] cipher = cipherDecipher.encrypt(plainText.getBytes(StandardCharsets.UTF_8),
                associatedData.getBytes(StandardCharsets.UTF_8));
        String cipherText = Base64.getEncoder().encodeToString(cipher);
        String decipherText = new String(cipherDecipher.decrypt(Base64.getDecoder().decode(cipherText),associatedData.getBytes()),StandardCharsets.UTF_8);
        assertEquals(plainText,decipherText);
    }
}
