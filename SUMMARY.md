# Configuration Summary for Phoenix IAM on WildFly

## Overview
This document summarizes the complete setup for deploying the Phoenix IAM application on WildFly with your domain configuration.

## Your Domain Setup
- **Main Application**: `anis-nsir.me` (frontend/admin content served from `/var/www/root` and `/var/www/admin`)
- **IAM Service**: `iam.anis-nsir.me` (Phoenix IAM application for authentication)
- **VM**: Single server running WildFly hosting both applications

## What Was Done

### 1. Fixed Missing Code
- ✅ Created `SimplePKEntity.java` - The missing JPA base class that all entities inherit from
- ✅ Updated `microprofile-config.properties` with your production domains

### 2. Created Configuration Files

#### Database Configuration
- **`database-schema.sql`**: Complete MySQL schema with tables for:
  - `tenants`: OAuth 2.0 clients (your applications)
  - `identities`: User accounts
  - `grants`: User consent for OAuth scopes

#### WildFly Configuration  
- **`wildfly-config.sh`**: Automated script containing all WildFly CLI commands to:
  - Create `iam-host` virtual host for `iam.anis-nsir.me`
  - Configure MySQL datasource (`MySqlDS`)
  - Apply security headers (HSTS, Content-Type-Options)
  - Configure CORS for cross-domain access
  - Set up secure session cookies

#### MySQL JDBC Driver
- **`mysql-module.xml`**: Module configuration for WildFly to load MySQL JDBC driver

### 3. Created Documentation

#### Comprehensive Guides
- **`DEPLOYMENT.md`**: Full step-by-step deployment guide (11KB)
  - Prerequisites and requirements
  - Database setup instructions
  - WildFly configuration steps
  - SSL certificate verification
  - Testing procedures
  - Troubleshooting guide
  - Security checklist

- **`QUICKSTART.md`**: Quick reference guide (10KB)
  - Summary of your current WildFly setup
  - What needs to be fixed (4 main issues)
  - Complete setup commands in order
  - Verification steps
  - Common issues and solutions
  - Production checklist

### 4. Created Helper Tools

#### Credential Generation
- **`generate-credentials.sh`**: Bash script that:
  - Generates secure database password (32 chars)
  - Generates OAuth tenant secret (64 chars hex)
  - Provides instructions for Argon2 password hashing
  - Shows where to use each credential

- **`PasswordHashGenerator.java`**: Java utility tool to:
  - Generate Argon2 password hashes using your exact configuration
  - Output SQL INSERT statements ready to use
  - Ensure compatibility with the application

### 5. Build Verification
- ✅ Maven build succeeds
- ✅ WAR file generated: `phoenix-iam.war` (2.7MB)
- ✅ All dependencies resolved
- ✅ No compilation errors

### 6. Security Verification
- ✅ Code review completed - all issues addressed
- ✅ CodeQL security scan - no vulnerabilities found
- ✅ Placeholder credentials clearly marked as invalid
- ✅ All security best practices documented

## What You Need to Do

### Step 1: Generate Credentials (5 minutes)
```bash
cd /path/to/Application-Security-Project
./generate-credentials.sh
```
Save the output securely (use a password manager).

### Step 2: Database Setup (10 minutes)
```bash
# Create database and user
mysql -u root -p << EOF
CREATE DATABASE phoenix_iam CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'phoenix_user'@'localhost' IDENTIFIED BY 'PASSWORD_FROM_STEP1';
GRANT ALL PRIVILEGES ON phoenix_iam.* TO 'phoenix_user'@'localhost';
FLUSH PRIVILEGES;
EOF

# Edit database-schema.sql with your tenant secret
# Then initialize schema
mysql -u phoenix_user -p phoenix_iam < database-schema.sql
```

### Step 3: Install MySQL JDBC Driver (5 minutes)
```bash
# Create module directory
mkdir -p $WILDFLY_HOME/modules/com/mysql/main/

# Download driver
cd $WILDFLY_HOME/modules/com/mysql/main/
wget https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.33/mysql-connector-java-8.0.33.jar

# Copy module configuration
cp /path/to/mysql-module.xml ./module.xml
```

### Step 4: Configure WildFly (15 minutes)
```bash
# Edit wildfly-config.sh and update the database password
# Then run the configuration via CLI

$WILDFLY_HOME/bin/jboss-cli.sh --connect --file=wildfly-config.sh
```

### Step 5: Build and Deploy Application (5 minutes)
```bash
# Build
cd /path/to/Application-Security-Project/src
mvn clean package

# Deploy
cp target/phoenix-iam.war $WILDFLY_HOME/standalone/deployments/

# Monitor deployment
tail -f $WILDFLY_HOME/standalone/log/server.log
```

### Step 6: Create Admin User (5 minutes)

After deployment, generate admin password hash:

```bash
# Method 1: Using the WAR file
cd $WILDFLY_HOME/standalone/deployments
jar -xf phoenix-iam.war WEB-INF/classes/xyz/kaaniche/phoenix/iam/tools/PasswordHashGenerator.class
jar -xf phoenix-iam.war WEB-INF/classes/xyz/kaaniche/phoenix/iam/security/Argon2Utility.class
# Then run with java -cp classpath

# Method 2: Using the source
cd /path/to/Application-Security-Project/src
mvn exec:java -Dexec.mainClass="xyz.kaaniche.phoenix.iam.tools.PasswordHashGenerator" -Dexec.args="YourAdminPassword"
```

Insert the admin user:
```sql
INSERT INTO identities (username, password, roles, provided_scopes)
VALUES ('admin', 'HASH_FROM_PREVIOUS_STEP', 7, 'openid profile email admin');
```

### Step 7: Verify Everything Works (10 minutes)

Test the endpoints:
```bash
# Test JWK endpoint
curl https://iam.anis-nsir.me/jwk

# Test authorization endpoint
curl "https://iam.anis-nsir.me/authorize?client_id=anis-app&response_type=code&redirect_uri=https://anis-nsir.me/callback&code_challenge_method=S256&code_challenge=test"
```

## Key Issues That Were Fixed

### Issue 1: Missing iam-host Virtual Host
**Problem**: Your WildFly configuration had `admin-host` but not `iam-host`, while `jboss-web.xml` specifies `iam-host`.

**Solution**: The `wildfly-config.sh` script creates `iam-host` with alias `iam.anis-nsir.me`.

### Issue 2: No Database Configuration
**Problem**: Application requires MySQL datasource `MySqlDS` which wasn't configured.

**Solution**: Complete database setup instructions and WildFly datasource configuration in the scripts.

### Issue 3: Hardcoded localhost URLs
**Problem**: Configuration had `localhost` instead of production domains.

**Solution**: Updated `microprofile-config.properties` with:
- JWT Issuer: `https://iam.anis-nsir.me`
- JWT Audiences: `https://anis-nsir.me`, `https://api.anis-nsir.me`

### Issue 4: Missing Base Entity Class
**Problem**: Code wouldn't compile due to missing `SimplePKEntity`.

**Solution**: Created the JPA base class with proper entity configuration.

## Important Security Notes

### ⚠️ Critical Actions Required
1. **Change ALL placeholder passwords** in configuration files
2. **Generate strong admin password** using the provided tools
3. **Use strong database password** (generated by script)
4. **Verify SSL certificate** covers both domains
5. **Restrict database access** to localhost only

### 🔒 Security Features Enabled
- HTTPS enforced (HTTP → HTTPS redirect)
- HSTS headers (max-age 2 years)
- Secure session cookies (HttpOnly, Secure, SameSite=Strict)
- X-Content-Type-Options: nosniff
- CORS properly configured
- Argon2 password hashing with strong parameters
- JWT with Ed25519 signatures
- OAuth 2.0 with PKCE

## OAuth 2.0 Flow

Your application will work as follows:

1. **User visits** `https://anis-nsir.me` (your main app)
2. **Redirected to IAM** → `https://iam.anis-nsir.me/authorize?client_id=anis-app&...`
3. **User logs in** on IAM domain
4. **IAM redirects back** → `https://anis-nsir.me/callback?code=...`
5. **Your app exchanges code** for JWT token
6. **Token validated** using public keys from `https://iam.anis-nsir.me/jwk`
7. **User authenticated** on your main application

## File Structure

```
Application-Security-Project/
├── src/
│   ├── main/
│   │   ├── java/xyz/kaaniche/phoenix/
│   │   │   ├── core/entities/
│   │   │   │   └── SimplePKEntity.java (NEW - JPA base class)
│   │   │   └── iam/
│   │   │       ├── boundaries/ (REST endpoints)
│   │   │       ├── controllers/ (Business logic)
│   │   │       ├── entities/ (JPA entities)
│   │   │       ├── security/ (JWT, Argon2, OAuth)
│   │   │       └── tools/
│   │   │           └── PasswordHashGenerator.java (NEW - Password tool)
│   │   ├── resources/META-INF/
│   │   │   ├── microprofile-config.properties (UPDATED - Production domains)
│   │   │   └── persistence.xml
│   │   └── webapp/WEB-INF/
│   │       ├── jboss-web.xml (Specifies iam-host)
│   │       └── beans.xml
│   └── pom.xml
├── DEPLOYMENT.md (NEW - Full deployment guide)
├── QUICKSTART.md (NEW - Quick reference)
├── database-schema.sql (NEW - MySQL schema)
├── wildfly-config.sh (NEW - WildFly CLI commands)
├── mysql-module.xml (NEW - JDBC driver config)
├── generate-credentials.sh (NEW - Credential generator)
└── .gitignore (NEW - Excludes build artifacts)
```

## Next Steps After Deployment

1. **Configure your frontend application** to use the OAuth 2.0 endpoints
2. **Create additional tenants** for each application that needs authentication
3. **Create user accounts** for your users
4. **Test the complete flow** end-to-end
5. **Setup monitoring** and logging
6. **Configure database backups**
7. **Setup certificate auto-renewal** (if using Let's Encrypt)

## Support and Troubleshooting

- See **DEPLOYMENT.md** for detailed troubleshooting steps
- See **QUICKSTART.md** for common issues and solutions
- Check WildFly logs: `$WILDFLY_HOME/standalone/log/server.log`
- Check MySQL logs: `/var/log/mysql/error.log`

## Estimated Time to Complete

- Database setup: 15 minutes
- WildFly configuration: 20 minutes  
- Application deployment: 10 minutes
- Testing and verification: 15 minutes
- **Total: ~60 minutes**

## Conclusion

All necessary configuration files, scripts, and documentation have been created. The application is ready for deployment following the steps above. All security best practices have been implemented, and the code has passed security scans.

**Status**: ✅ Ready for deployment
**Build**: ✅ Successful (phoenix-iam.war 2.7MB)
**Security**: ✅ No vulnerabilities found
**Documentation**: ✅ Complete

For questions or issues, refer to DEPLOYMENT.md and QUICKSTART.md for detailed guidance.
