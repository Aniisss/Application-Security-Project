package xyz.kaaniche.phoenix.iam.tools;

import xyz.kaaniche.phoenix.iam.security.Argon2Utility;

/**
 * Simple command-line utility to generate Argon2 password hashes
 * compatible with the Phoenix IAM application.
 * 
 * Usage:
 *   java xyz.kaaniche.phoenix.iam.tools.PasswordHashGenerator <password>
 * 
 * This tool uses the same Argon2 configuration as the main application
 * to ensure compatibility.
 */
public class PasswordHashGenerator {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java xyz.kaaniche.phoenix.iam.tools.PasswordHashGenerator <password>");
            System.err.println("");
            System.err.println("Example:");
            System.err.println("  java xyz.kaaniche.phoenix.iam.tools.PasswordHashGenerator 'MySecureP@ssw0rd'");
            System.exit(1);
        }
        
        String password = args[0];
        
        try {
            String hash = Argon2Utility.hash(password.toCharArray());
            
            System.out.println("========================================");
            System.out.println("Argon2 Password Hash Generated");
            System.out.println("========================================");
            System.out.println("");
            System.out.println("Password: " + password);
            System.out.println("");
            System.out.println("Hash:");
            System.out.println(hash);
            System.out.println("");
            System.out.println("========================================");
            System.out.println("SQL INSERT Statement:");
            System.out.println("========================================");
            System.out.println("");
            System.out.println("INSERT INTO identities (username, password, roles, provided_scopes)");
            System.out.println("VALUES ('admin', '" + hash + "', 7, 'openid profile email admin');");
            System.out.println("");
            System.out.println("========================================");
            System.out.println("SECURITY WARNING:");
            System.out.println("- Clear your terminal history after running this command");
            System.out.println("- Never store plain text passwords");
            System.out.println("- Use strong, unique passwords for production");
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println("Error generating hash: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
