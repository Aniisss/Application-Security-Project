# Application-Security-Project

## Phoenix IAM (Identity and Access Management) Application

This is a Jakarta EE-based OAuth 2.0 Identity and Access Management application designed to run on WildFly Application Server.

---

## 📋 Table of Contents

1. [Application Overview](#application-overview)
2. [Prerequisites](#prerequisites)
3. [Build Instructions](#build-instructions)
4. [Deployment Steps](#deployment-steps)
5. [WildFly HSTS & Security Configuration](#wildfly-hsts--security-configuration)
6. [CAA DNS Record Configuration](#caa-dns-record-configuration)
7. [Complete Deployment Workflow](#complete-deployment-workflow)
8. [Verification Steps](#verification-steps)

---

## 🔍 Application Overview

### Technology Stack

- **Backend**: Jakarta EE 10 (JAX-RS, CDI, JPA, EJB)
- **Application Server**: WildFly (JBoss)
- **Database**: MySQL (via JPA/Hibernate)
- **Security**: OAuth 2.0 Authorization Code Flow with PKCE, JWT Tokens, Argon2 Password Hashing

### Key Components

- **AuthenticationEndpoint**: Handles OAuth 2.0 `/authorize` flow
- **TokenEndpoint**: Issues access and refresh tokens
- **JWKEndpoint**: Exposes JSON Web Key Set for token validation
- **JwtManager**: Manages JWT token generation and validation
- **Argon2Utility**: Handles secure password hashing

### Application Context

The application is configured to deploy at the root context (`/`) on a virtual host named `iam-host` (see `jboss-web.xml`).

---

## ✅ Prerequisites

Before deploying, ensure you have:

1. **WildFly Application Server** installed on your VM (e.g., WildFly 30+)
2. **Java 17+** installed
3. **MySQL Database** server running
4. **TLS/SSL Certificate** configured for your domain (anis-nsir.me)
5. **Maven** for building the application (if building from source)

---

## 🔧 Build Instructions

### Step 1: Clone the Repository

```bash
git clone https://github.com/Aniisss/Application-Security-Project.git
cd Application-Security-Project
```

### Step 2: Build the WAR File

You need to add a `pom.xml` build configuration. Create a `pom.xml` in the `src/` directory:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>xyz.kaaniche.phoenix</groupId>
    <artifactId>phoenix-iam</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>war</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>jakarta.platform</groupId>
            <artifactId>jakarta.jakartaee-api</artifactId>
            <version>10.0.0</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.eclipse.microprofile</groupId>
            <artifactId>microprofile</artifactId>
            <version>6.0</version>
            <type>pom</type>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.nimbusds</groupId>
            <artifactId>nimbus-jose-jwt</artifactId>
            <version>9.31</version>
        </dependency>
        <dependency>
            <groupId>de.mkammerer</groupId>
            <artifactId>argon2-jvm</artifactId>
            <version>2.11</version>
        </dependency>
    </dependencies>

    <build>
        <finalName>phoenix-iam</finalName>
    </build>
</project>
```

Then build:

```bash
cd src
mvn clean package
```

This will generate `target/phoenix-iam.war`.

---

## 🚀 Deployment Steps

### Step 1: Configure MySQL Datasource on WildFly

Before deploying the application, configure the MySQL datasource. In WildFly CLI (`jboss-cli.sh`):

**First, download the MySQL JDBC driver:**
```bash
# Download from: https://dev.mysql.com/downloads/connector/j/
# Select "Platform Independent" -> Download the TAR/ZIP archive
# Extract and copy the JAR to a known location:
wget https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-j-8.2.0.tar.gz
tar -xzf mysql-connector-j-8.2.0.tar.gz
sudo cp mysql-connector-j-8.2.0/mysql-connector-j-8.2.0.jar /opt/mysql-connector-j.jar
```

**Then configure WildFly:**
```bash
# Start WildFly CLI
$WILDFLY_HOME/bin/jboss-cli.sh --connect

# Add MySQL JDBC driver module
# Note: For Jakarta EE 10 (WildFly 27+), use jakarta.api instead of javax.api
module add --name=com.mysql --resources=/opt/mysql-connector-j.jar --dependencies=jakarta.api,jakarta.transaction.api

# Add JDBC driver
/subsystem=datasources/jdbc-driver=mysql:add(driver-name=mysql,driver-module-name=com.mysql,driver-class-name=com.mysql.cj.jdbc.Driver)

# Create datasource
data-source add --name=MySqlDS --jndi-name=java:jboss/datasources/MySqlDS --driver-name=mysql --connection-url=jdbc:mysql://localhost:3306/phoenix_iam --user-name=your_user --password=your_password
```

### Step 2: Configure Virtual Host for IAM

The application expects a virtual host named `iam-host`. Add it to your WildFly configuration:

```bash
# In WildFly CLI
/subsystem=undertow/server=default-server/host=iam-host:add(alias=["anis-nsir.me"])
```

### Step 3: Deploy the WAR File

```bash
# Copy the WAR file to WildFly deployments
cp target/phoenix-iam.war $WILDFLY_HOME/standalone/deployments/

# Or use CLI
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="deploy /path/to/phoenix-iam.war"
```

---

## 🔒 WildFly HSTS & Security Configuration

### ⚠️ IMPORTANT: Run AFTER deploying the application

The HSTS and security headers should be configured **after** the application is deployed. Here's the complete configuration script for your domain `anis-nsir.me`:

### HSTS Max-Age Guidelines

| Environment | Max-Age (seconds) | Description |
|-------------|-------------------|-------------|
| Testing | 86400 | 1 day - safe for initial testing |
| Staging | 604800 | 1 week - verify everything works |
| Production | 31536000 | 1 year - minimum for HSTS preload |
| Production (strict) | 63072000 | 2 years - maximum security |

**⚠️ Warning**: Start with a short max-age during testing. A long max-age can cause access issues if SSL is misconfigured.

### Step-by-Step WildFly CLI Commands

Connect to WildFly CLI:
```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect
```

#### 1. Configure Static Content Directories (Optional - for SPA frontends)

```bash
# Set up root static content directory
/subsystem=undertow/configuration=handler/file=welcome-content:write-attribute(name=path,value="/var/www/root")

reload

# Set up admin content directory (if needed)
/subsystem=undertow/configuration=handler/file=admin-content:add(path="/var/www/admin")

# Create admin host
/subsystem=undertow/server=default-server/host=admin-host:add(alias=["admin.anis-nsir.me"])

# Add location handler for admin host
/subsystem=undertow/server=default-server/host=admin-host/location=\/:add(handler=admin-content)

reload
```

#### 2. Configure HSTS Header

```bash
# Add HSTS response header filter
# For testing, use max-age=86400 (1 day)
# For production with preload, use max-age=31536000 (1 year) or more
/subsystem=undertow/configuration=filter/response-header=hsts:add(header-name=Strict-Transport-Security,header-value="max-age=31536000; includeSubDomains; preload")
```

#### 3. Configure HTTP to HTTPS Redirect

```bash
# Add HTTP to HTTPS redirect filter
/subsystem=undertow/configuration=filter/rewrite=http-to-https:add(target="https://%v%U",redirect=true)
```

#### 4. Apply Filters to Hosts

```bash
# Apply HSTS to default-host (only on HTTPS - port 443)
/subsystem=undertow/server=default-server/host=default-host/filter-ref=hsts:add(predicate="equals(%p,443)")

# Apply HTTP to HTTPS redirect on default-host (on HTTP - port 80)
/subsystem=undertow/server=default-server/host=default-host/filter-ref=http-to-https:add(predicate="equals(%p,80)")

# Apply HSTS to admin-host (if using admin host)
/subsystem=undertow/server=default-server/host=admin-host/filter-ref=hsts:add(predicate="equals(%p,443)")

# Apply HTTP to HTTPS redirect on admin-host
/subsystem=undertow/server=default-server/host=admin-host/filter-ref=http-to-https:add(predicate="equals(%p,80)")
```

#### 5. Configure SPA Routing (Error Page Fallback)

```bash
# Configure SPA fallback for default host
/subsystem=undertow/configuration=filter/error-page=spa-default-router:add(code="404",path="/var/www/root/index.html")
/subsystem=undertow/server=default-server/host=default-host/filter-ref=spa-default-router:add(predicate="true")

# Configure SPA fallback for admin host
/subsystem=undertow/configuration=filter/error-page=spa-admin-router:add(code="404",path="/var/www/admin/index.html")
/subsystem=undertow/server=default-server/host=admin-host/filter-ref=spa-admin-router:add(predicate="true")
```

#### 6. Configure Cache Control Header

```bash
# Add Cache-Control header (important for admin/sensitive pages)
/subsystem=undertow/configuration=filter/response-header=cache-control:add(header-name=Cache-Control,header-value="private,no-cache")
/subsystem=undertow/server=default-server/host=admin-host/filter-ref=cache-control:add(predicate="true")

reload
```

#### 7. Configure X-Content-Type-Options Header

```bash
# Add X-Content-Type-Options header (prevents MIME type sniffing)
/subsystem=undertow/configuration=filter/response-header=content-type-options:add(header-name=X-Content-Type-Options,header-value="nosniff")
/subsystem=undertow/server=default-server/host=default-host/filter-ref=content-type-options:add(predicate="true")

reload
```

---

## 🌐 CAA DNS Record Configuration

CAA (Certificate Authority Authorization) records specify which Certificate Authorities are allowed to issue certificates for your domain.

### Add CAA Records in Your DNS Provider

Add the following CAA records for `anis-nsir.me`:

| Type | Name | Value |
|------|------|-------|
| CAA | @ | `0 issue "letsencrypt.org"` |
| CAA | @ | `0 issuewild "letsencrypt.org"` |
| CAA | @ | `0 iodef "mailto:your-email@example.com"` |

If using a different CA (e.g., DigiCert, Sectigo), replace `letsencrypt.org` with your CA's domain.

### Example DNS Zone File Entry

```
anis-nsir.me.    IN    CAA    0 issue "letsencrypt.org"
anis-nsir.me.    IN    CAA    0 issuewild "letsencrypt.org"
anis-nsir.me.    IN    CAA    0 iodef "mailto:admin@anis-nsir.me"
```

---

## 📝 Complete Deployment Workflow

Here's the complete step-by-step workflow from your current situation:

### Phase 1: Prepare the VM

1. ✅ WildFly is installed
2. ✅ TLS/SSL is configured
3. ✅ Domain anis-nsir.me points to VM

### Phase 2: Build and Deploy Application

```bash
# 1. Clone repository on your local machine or VM
git clone https://github.com/Aniisss/Application-Security-Project.git
cd Application-Security-Project/src

# 2. Create pom.xml (see Build Instructions section above)

# 3. Build the WAR file
mvn clean package

# 4. Copy WAR to WildFly
scp target/phoenix-iam.war user@anis-nsir.me:/opt/wildfly/standalone/deployments/
```

### Phase 3: Configure WildFly

On the VM, execute the following in order:

```bash
# 1. Connect to WildFly CLI
$WILDFLY_HOME/bin/jboss-cli.sh --connect

# 2. Configure MySQL datasource (see Deployment Steps)

# 3. Configure IAM virtual host
/subsystem=undertow/server=default-server/host=iam-host:add(alias=["anis-nsir.me"])

# 4. Reload configuration
reload
```

### Phase 4: Apply Security Headers (HSTS)

After the application is deployed and working:

```bash
# Execute all HSTS and security header commands from above section
```

### Phase 5: Verify Deployment

See Verification Steps below.

---

## ✔️ Verification Steps

### 1. Test HTTPS Connection

```bash
curl -I https://anis-nsir.me
```

Expected headers:
```
HTTP/2 200
strict-transport-security: max-age=63072000; includeSubDomains; preload
x-content-type-options: nosniff
```

### 2. Test HTTP to HTTPS Redirect

```bash
curl -I http://anis-nsir.me
```

Expected: `301` or `302` redirect to `https://anis-nsir.me`

### 3. Test HSTS Preload Eligibility

Visit: https://hstspreload.org and enter your domain

### 4. Test CAA Records

```bash
dig CAA anis-nsir.me
```

### 5. Test SSL Configuration

Visit: https://www.ssllabs.com/ssltest/analyze.html?d=anis-nsir.me

### 6. Test Application Endpoints

```bash
# Test OAuth authorize endpoint
curl -I "https://anis-nsir.me/rest-iam/authorize?client_id=test&response_type=code&code_challenge_method=S256"
```

---

## 🗂️ Project Structure

```
Application-Security-Project/
└── src/
    └── src/
        ├── main/
        │   ├── java/xyz/kaaniche/phoenix/iam/
        │   │   ├── IamApplication.java          # JAX-RS Application entry point
        │   │   ├── boundaries/                   # REST endpoints
        │   │   │   ├── AuthenticationEndpoint.java
        │   │   │   ├── TokenEndpoint.java
        │   │   │   ├── JWKEndpoint.java
        │   │   │   └── PushWebSocketEndpoint.java
        │   │   ├── controllers/                  # Business logic
        │   │   ├── entities/                     # JPA entities
        │   │   │   ├── Grant.java
        │   │   │   ├── Identity.java
        │   │   │   └── Tenant.java
        │   │   └── security/                     # Security utilities
        │   │       ├── JwtManager.java
        │   │       └── Argon2Utility.java
        │   ├── resources/
        │   │   ├── META-INF/
        │   │   │   ├── microprofile-config.properties
        │   │   │   └── persistence.xml
        │   │   ├── login.html
        │   │   └── consent.html
        │   └── webapp/
        │       └── WEB-INF/
        │           ├── beans.xml
        │           └── jboss-web.xml
        └── test/
            └── java/                            # Test files
```

---

## ⚙️ Configuration Files

### microprofile-config.properties

Key configuration properties:

| Property | Description |
|----------|-------------|
| `jwt.lifetime.duration` | JWT token lifetime in seconds (1020 = 17 min) |
| `jwt.issuer` | JWT issuer claim |
| `jwt.audiences` | Allowed JWT audience claims |
| `argon2.saltLength` | Argon2 password hash salt length |
| `argon2.iterations` | Argon2 iterations for password hashing |

### jboss-web.xml

Configures:
- Context root: `/`
- Virtual host: `iam-host`

---

## 🔐 Security Summary

This application implements:

1. **Transport Security**
   - HSTS with preload directive
   - HTTP to HTTPS redirect
   - X-Content-Type-Options: nosniff

2. **Authentication**
   - OAuth 2.0 Authorization Code Flow with PKCE
   - Secure password hashing with Argon2
   - JWT token-based authentication

3. **Certificate Management**
   - CAA DNS records to control certificate issuance
   - TLS/SSL encryption

---

## 📞 Troubleshooting

### Application not starting

1. Check WildFly logs: `$WILDFLY_HOME/standalone/log/server.log`
2. Verify MySQL datasource is configured correctly
3. Ensure all dependencies are deployed

### HSTS not working

1. Ensure you're accessing via HTTPS
2. Check filter predicates are correctly applied
3. Verify reload was executed after configuration

### CAA records not resolving

1. Wait for DNS propagation (up to 48 hours)
2. Verify DNS provider supports CAA records
3. Use `dig CAA domain.com` to verify

---

## 📄 License

[Add your license here]

---

## 👥 Contributors

[Add contributor information]