package xyz.kaaniche.phoenix.iam.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPassword {
    @Test
    public void generatePassword(){
        String hash = Argon2Utility.hash("fLGVAI2KKzj6C1aXRYwLDbztILRatmBVRXribg/QMynib8kOMK293LJoAKOrNmOI".toCharArray());
        System.out.println(hash);
        assertTrue(Argon2Utility.check(hash,"fLGVAI2KKzj6C1aXRYwLDbztILRatmBVRXribg/QMynib8kOMK293LJoAKOrNmOI".toCharArray()));
    }
}
