# Phoenix IAM Deployment Guide for WildFly

This guide provides step-by-step instructions to deploy the Phoenix IAM application on WildFly with your domain configuration.

## Prerequisites

- WildFly Application Server (tested with WildFly 27+)
- MySQL Database Server (8.0+)
- Java 17 or higher
- SSL Certificate for your domains (anis-nsir.me and iam.anis-nsir.me)
- Maven (for building the application)

## Domain Configuration

Your setup:
- Main application domain: `anis-nsir.me` (for frontend/admin)
- IAM domain: `iam.anis-nsir.me` (for authentication service)
- VM running WildFly with both applications

## Step 1: Build the Application

```bash
cd src
mvn clean package
```

This will create `target/phoenix-iam.war`

## Step 2: Database Setup

### 2.1 Create MySQL Database and User

```sql
CREATE DATABASE phoenix_iam CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'phoenix_user'@'localhost' IDENTIFIED BY 'STRONG_PASSWORD_HERE';
GRANT ALL PRIVILEGES ON phoenix_iam.* TO 'phoenix_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2.2 Initialize Database Schema

```bash
mysql -u phoenix_user -p phoenix_iam < database-schema.sql
```

### 2.3 Update Admin Password (IMPORTANT!)

The default admin password in the schema is insecure. You need to:

1. Generate a secure Argon2 hash for your password
2. Update the `identities` table:

```sql
UPDATE identities 
SET password = 'YOUR_ARGON2_HASH_HERE' 
WHERE username = 'admin';
```

To generate an Argon2 hash, you can use the application's `Argon2Utility` class or an online tool.

## Step 3: Configure MySQL JDBC Driver in WildFly

### 3.1 Create Module Directory

```bash
mkdir -p $WILDFLY_HOME/modules/com/mysql/main/
```

### 3.2 Download MySQL Connector

Download MySQL Connector/J from: https://dev.mysql.com/downloads/connector/j/

```bash
# Example for version 8.0.33
wget https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.33/mysql-connector-java-8.0.33.jar
cp mysql-connector-java-8.0.33.jar $WILDFLY_HOME/modules/com/mysql/main/
```

### 3.3 Create Module Configuration

Copy the provided `mysql-module.xml` to WildFly:

```bash
cp mysql-module.xml $WILDFLY_HOME/modules/com/mysql/main/module.xml
```

**Important:** Edit the module.xml and update the JAR filename to match your MySQL Connector version.

## Step 4: Configure WildFly

### 4.1 Update Virtual Host Configuration

You've already configured `admin-host` for your main application. Now we need to configure `iam-host` for the IAM application.

Start WildFly CLI:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect
```

Execute these commands:

```bash
# Add iam-host virtual host
/subsystem=undertow/server=default-server/host=iam-host:add(alias=["iam.anis-nsir.me"],default-web-module="phoenix-iam.war")

# Add location handler for iam-host
/subsystem=undertow/server=default-server/host=iam-host/location=\/:add(handler=welcome-content)

# Apply security headers to iam-host (HSTS)
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=hsts:add(predicate="equals(%p,443)")

# Apply HTTP to HTTPS redirect
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=http-to-https:add(predicate="equals(%p,80)")

# Apply security headers (X-Content-Type-Options)
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=content-type-options:add(predicate="true")

reload
```

### 4.2 Configure MySQL DataSource

```bash
# Add MySQL driver
/subsystem=datasources/jdbc-driver=mysql:add(driver-name=mysql,driver-module-name=com.mysql,driver-class-name=com.mysql.cj.jdbc.Driver)

# Add datasource (UPDATE THE PASSWORD!)
data-source add --name=MySqlDS \
    --jndi-name=java:jboss/datasources/MySqlDS \
    --driver-name=mysql \
    --connection-url=jdbc:mysql://localhost:3306/phoenix_iam?useSSL=true&requireSSL=true&serverTimezone=UTC \
    --user-name=phoenix_user \
    --password=CHANGE_THIS_PASSWORD \
    --use-ccm=true \
    --min-pool-size=5 \
    --max-pool-size=20 \
    --blocking-timeout-wait-millis=5000 \
    --enabled=true \
    --exception-sorter-class-name=org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLExceptionSorter \
    --valid-connection-checker-class-name=org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLValidConnectionChecker

# Test the connection
/subsystem=datasources/data-source=MySqlDS:test-connection-in-pool

reload
```

### 4.3 Configure CORS (if your frontend needs to access IAM from different domain)

```bash
# Add CORS filters
/subsystem=undertow/configuration=filter/response-header=cors-allow-origin:add(header-name=Access-Control-Allow-Origin,header-value="https://anis-nsir.me")
/subsystem=undertow/configuration=filter/response-header=cors-allow-methods:add(header-name=Access-Control-Allow-Methods,header-value="GET, POST, PUT, DELETE, PATCH, OPTIONS")
/subsystem=undertow/configuration=filter/response-header=cors-allow-headers:add(header-name=Access-Control-Allow-Headers,header-value="Origin, Content-Type, Accept, Authorization")
/subsystem=undertow/configuration=filter/response-header=cors-allow-credentials:add(header-name=Access-Control-Allow-Credentials,header-value="true")

# Apply CORS to iam-host
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-origin:add(predicate="true")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-methods:add(predicate="true")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-headers:add(predicate="true")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-credentials:add(predicate="true")

reload
```

### 4.4 Configure Secure Session Cookies

```bash
/subsystem=undertow/servlet-container=default/setting=session-cookie:write-attribute(name=http-only,value=true)
/subsystem=undertow/servlet-container=default/setting=session-cookie:write-attribute(name=secure,value=true)
/subsystem=undertow/servlet-container=default/setting=session-cookie:write-attribute(name=same-site-mode,value=strict)

reload
```

## Step 5: SSL Certificate Configuration

Your certificate is already configured. Verify it includes both domains:

```bash
# Your certificate should include:
# - anis-nsir.me
# - iam.anis-nsir.me
# - *.anis-nsir.me (wildcard, if available)
```

If you need to update the certificate:

```bash
# Copy your certificate
cp your-domain.me.jks $WILDFLY_HOME/standalone/configuration/

# Update the Elytron configuration (already done based on your commands)
# The certificate should cover both domains
```

## Step 6: Deploy the Application

```bash
# Copy the WAR file to deployments directory
cp src/target/phoenix-iam.war $WILDFLY_HOME/standalone/deployments/

# WildFly will auto-deploy the application
# Watch the deployment
tail -f $WILDFLY_HOME/standalone/log/server.log
```

## Step 7: Verify Deployment

### 7.1 Check Application Status

```bash
# In WildFly CLI
deployment-info

# You should see phoenix-iam.war with status OK
```

### 7.2 Test the Endpoints

```bash
# Test IAM authorization endpoint
curl -k https://iam.anis-nsir.me/authorize?client_id=anis-app&response_type=code&redirect_uri=https://anis-nsir.me/callback&code_challenge_method=S256&code_challenge=DUMMY

# Test JWK endpoint
curl -k https://iam.anis-nsir.me/jwk

# Test OAuth token endpoint (after obtaining a code)
curl -k -X POST https://iam.anis-nsir.me/oauth/token \
  -d "grant_type=authorization_code&code=YOUR_CODE&code_verifier=YOUR_VERIFIER"
```

## Step 8: Configure Your Main Application

Update your main application (deployed on `anis-nsir.me`) to use the IAM service:

### OAuth 2.0 Configuration

```javascript
// Frontend configuration example
const oauth2Config = {
  authorizationEndpoint: 'https://iam.anis-nsir.me/authorize',
  tokenEndpoint: 'https://iam.anis-nsir.me/oauth/token',
  jwkEndpoint: 'https://iam.anis-nsir.me/jwk',
  clientId: 'anis-app',
  redirectUri: 'https://anis-nsir.me/callback',
  scope: 'openid profile email',
  codeChallengeMethod: 'S256'
};
```

### Backend JWT Validation

Your backend should validate JWTs from the IAM service:

- Fetch public keys from: `https://iam.anis-nsir.me/jwk`
- Validate JWT signature, expiration, issuer, and audience
- Expected issuer: `https://iam.anis-nsir.me`
- Expected audience: `https://anis-nsir.me` or `https://api.anis-nsir.me`

## Troubleshooting

### Issue: Application not accessible

**Check:**
1. DNS points correctly to your VM
2. Firewall allows ports 80, 443
3. WildFly is listening on the correct interfaces

```bash
# Check WildFly interfaces
netstat -tlnp | grep java
```

### Issue: Database connection fails

**Check:**
1. MySQL is running: `systemctl status mysql`
2. Database credentials in datasource are correct
3. Test connection: `/subsystem=datasources/data-source=MySqlDS:test-connection-in-pool`

### Issue: Certificate errors

**Check:**
1. Certificate includes both domains (anis-nsir.me and iam.anis-nsir.me)
2. Certificate is not expired
3. Certificate chain is complete

### Issue: CORS errors

**Check:**
1. CORS headers are properly configured
2. Origin matches exactly (including protocol and port)
3. Credentials are allowed if needed

### Enable Debug Logging

```bash
# In WildFly CLI
/subsystem=logging/logger=xyz.kaaniche.phoenix:add(level=DEBUG)
/subsystem=logging/logger=org.jboss.as.web:add(level=DEBUG)
reload
```

## Security Checklist

- [ ] Changed default admin password
- [ ] Updated tenant secret in database
- [ ] Configured strong database password
- [ ] SSL/TLS enabled and tested
- [ ] HSTS headers configured
- [ ] Secure session cookies enabled
- [ ] CORS properly restricted
- [ ] Firewall configured (only ports 80, 443, 3306 if needed)
- [ ] Database accessible only from localhost
- [ ] Regular security updates applied

## Application Flow

### OAuth 2.0 Authorization Code Flow with PKCE

1. **User accesses protected resource** on `anis-nsir.me`
2. **Redirect to IAM** → `https://iam.anis-nsir.me/authorize?client_id=anis-app&...`
3. **User logs in** on IAM domain
4. **Consent** (if first time)
5. **Redirect back** → `https://anis-nsir.me/callback?code=...`
6. **Exchange code for token** → POST to `https://iam.anis-nsir.me/oauth/token`
7. **Validate JWT** using public keys from `https://iam.anis-nsir.me/jwk`
8. **Access granted** on main application

## Configuration Files Summary

### Files Updated in This Repository

1. **src/main/resources/META-INF/microprofile-config.properties**
   - Updated JWT issuer and audiences for production domains
   - Updated MQTT URIs (if used)

2. **src/main/webapp/WEB-INF/jboss-web.xml**
   - Configured to deploy on `iam-host` virtual host

3. **src/main/resources/META-INF/persistence.xml**
   - Configured to use `java:jboss/datasources/MySqlDS`

### Files Created

1. **database-schema.sql** - Database initialization script
2. **wildfly-config.sh** - WildFly CLI configuration commands
3. **mysql-module.xml** - MySQL JDBC driver module configuration
4. **DEPLOYMENT.md** - This deployment guide

## Additional Resources

- [WildFly Documentation](https://docs.wildfly.org/)
- [Jakarta EE 10 Documentation](https://jakarta.ee/specifications/platform/10/)
- [OAuth 2.0 RFC](https://datatracker.ietf.org/doc/html/rfc6749)
- [PKCE RFC](https://datatracker.ietf.org/doc/html/rfc7636)

## Support

For issues with the Phoenix IAM application, check:
- Application logs: `$WILDFLY_HOME/standalone/log/server.log`
- WildFly configuration: `$WILDFLY_HOME/standalone/configuration/standalone.xml`
- Database connectivity and schema

## License

[Include your license information here]
