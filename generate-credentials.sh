#!/bin/bash
# Credential Generation Helper Script
# This script helps generate secure credentials for the Phoenix IAM deployment

echo "=========================================="
echo "Phoenix IAM Credential Generation Helper"
echo "=========================================="
echo ""

# Generate database password
echo "1. Generating MySQL Database Password..."
DB_PASSWORD=$(openssl rand -base64 32 | tr -d "=+/" | cut -c1-32)
echo "   Database Password: $DB_PASSWORD"
echo ""

# Generate tenant secret
echo "2. Generating Tenant Secret..."
TENANT_SECRET=$(openssl rand -hex 32)
echo "   Tenant Secret: $TENANT_SECRET"
echo ""

# Instructions for Argon2 hash
echo "3. Admin Password Hash Generation:"
echo "   The admin password hash MUST be generated using the application's Argon2 configuration."
echo "   You have two options:"
echo ""
echo "   Option A: Generate after deployment"
echo "   ---------------------------------"
echo "   1. Deploy the application to WildFly"
echo "   2. Create a simple REST endpoint or standalone Java class that uses Argon2Utility"
echo "   3. Call it to hash your desired admin password"
echo "   4. Insert the user into the database manually"
echo ""
echo "   Example Java code to generate hash:"
echo "   -----------------------------------"
cat << 'EOF'
   import xyz.kaaniche.phoenix.iam.security.Argon2Utility;
   
   public class HashGenerator {
       public static void main(String[] args) {
           if (args.length == 0) {
               System.out.println("Usage: java HashGenerator <password>");
               return;
           }
           String password = args[0];
           String hash = Argon2Utility.hash(password.toCharArray());
           System.out.println("Argon2 Hash: " + hash);
       }
   }
EOF
echo ""
echo "   Option B: Use external Argon2 tool"
echo "   -----------------------------------"
echo "   Install argon2 CLI tool and generate with matching parameters:"
echo "   argon2 \$(openssl rand -base64 32) -t 23 -m 17 -p 2 -l 128 -id"
echo ""

# Summary
echo "=========================================="
echo "SUMMARY - Save these values securely:"
echo "=========================================="
echo "Database Password: $DB_PASSWORD"
echo "Tenant Secret:     $TENANT_SECRET"
echo ""
echo "NEXT STEPS:"
echo "1. Update database-schema.sql:"
echo "   - Replace 'CHANGE_THIS_SECRET_IN_PRODUCTION' with: $TENANT_SECRET"
echo ""
echo "2. Update wildfly-config.sh:"
echo "   - Replace 'CHANGE_THIS_PASSWORD' with: $DB_PASSWORD"
echo ""
echo "3. Create MySQL user with generated password:"
echo "   mysql -u root -p"
echo "   CREATE USER 'phoenix_user'@'localhost' IDENTIFIED BY '$DB_PASSWORD';"
echo ""
echo "4. Generate and insert admin user after deployment (see Option A above)"
echo ""
echo "=========================================="
echo "SECURITY REMINDER:"
echo "- Store these credentials securely (use a password manager)"
echo "- Never commit these values to version control"
echo "- Rotate credentials periodically"
echo "=========================================="
