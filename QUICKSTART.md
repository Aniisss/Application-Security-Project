# Quick Configuration Reference

## Your Current WildFly Setup

Based on the commands you've already executed, here's what you have configured:

### 1. Virtual Hosts
- **default-host**: Points to `/var/www/root` (for anis-nsir.me)
- **admin-host**: Points to `/var/www/admin` (for admin.your-domain.me)
- **iam-host**: NEEDS TO BE ADDED (for iam.anis-nsir.me)

### 2. What You Need to Fix

#### Problem 1: Virtual Host Mismatch
Your `jboss-web.xml` specifies `<virtual-host>iam-host</virtual-host>`, but you haven't created this host.

**Solution:** Add iam-host virtual host:
```bash
/subsystem=undertow/server=default-server/host=iam-host:add(alias=["iam.anis-nsir.me"],default-web-module="phoenix-iam.war")
/subsystem=undertow/server=default-server/host=iam-host/location=\/:add(handler=welcome-content)
reload
```

#### Problem 2: Security Headers Not Applied to iam-host
Your security filters are only on default-host and admin-host.

**Solution:** Apply security filters to iam-host:
```bash
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=hsts:add(predicate="equals(%p,443)")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=http-to-https:add(predicate="equals(%p,80)")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=content-type-options:add(predicate="true")
reload
```

#### Problem 3: Database Not Configured
The application requires a MySQL datasource named `MySqlDS`.

**Solution:** 
1. Install MySQL JDBC driver module (see DEPLOYMENT.md)
2. Add datasource:
```bash
/subsystem=datasources/jdbc-driver=mysql:add(driver-name=mysql,driver-module-name=com.mysql,driver-class-name=com.mysql.cj.jdbc.Driver)

data-source add --name=MySqlDS \
    --jndi-name=java:jboss/datasources/MySqlDS \
    --driver-name=mysql \
    --connection-url=jdbc:mysql://localhost:3306/phoenix_iam?useSSL=true&serverTimezone=UTC \
    --user-name=phoenix_user \
    --password=YOUR_PASSWORD_HERE \
    --enabled=true
```

#### Problem 4: Database Schema Not Created
The application needs database tables.

**Solution:**
```bash
mysql -u root -p
CREATE DATABASE phoenix_iam CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'phoenix_user'@'localhost' IDENTIFIED BY 'STRONG_PASSWORD';
GRANT ALL PRIVILEGES ON phoenix_iam.* TO 'phoenix_user'@'localhost';
FLUSH PRIVILEGES;
exit;

mysql -u phoenix_user -p phoenix_iam < database-schema.sql
```

### 3. Complete Setup Commands (In Order)

#### Step 1: MySQL Setup
```bash
# Create database
mysql -u root -p << EOF
CREATE DATABASE phoenix_iam CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'phoenix_user'@'localhost' IDENTIFIED BY 'YourStrongPassword123!';
GRANT ALL PRIVILEGES ON phoenix_iam.* TO 'phoenix_user'@'localhost';
FLUSH PRIVILEGES;
EOF

# Initialize schema
mysql -u phoenix_user -p phoenix_iam < database-schema.sql
```

#### Step 2: Install MySQL JDBC Driver
```bash
# Create module directory
mkdir -p $WILDFLY_HOME/modules/com/mysql/main/

# Download MySQL Connector
cd $WILDFLY_HOME/modules/com/mysql/main/
wget https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.33/mysql-connector-java-8.0.33.jar

# Copy module.xml
cp /path/to/mysql-module.xml ./module.xml
# Edit module.xml to match your JAR version
```

#### Step 3: WildFly Configuration
```bash
# Connect to CLI
$WILDFLY_HOME/bin/jboss-cli.sh --connect

# Add iam-host virtual host
/subsystem=undertow/server=default-server/host=iam-host:add(alias=["iam.anis-nsir.me"],default-web-module="phoenix-iam.war")
/subsystem=undertow/server=default-server/host=iam-host/location=\/:add(handler=welcome-content)

# Apply security filters
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=hsts:add(predicate="equals(%p,443)")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=http-to-https:add(predicate="equals(%p,80)")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=content-type-options:add(predicate="true")

reload

# Add MySQL driver
/subsystem=datasources/jdbc-driver=mysql:add(driver-name=mysql,driver-module-name=com.mysql,driver-class-name=com.mysql.cj.jdbc.Driver)

# Add datasource (REPLACE PASSWORD!)
data-source add --name=MySqlDS --jndi-name=java:jboss/datasources/MySqlDS --driver-name=mysql --connection-url=jdbc:mysql://localhost:3306/phoenix_iam?useSSL=true&serverTimezone=UTC --user-name=phoenix_user --password=YourStrongPassword123! --enabled=true --exception-sorter-class-name=org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLExceptionSorter --valid-connection-checker-class-name=org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLValidConnectionChecker

# Test connection
/subsystem=datasources/data-source=MySqlDS:test-connection-in-pool

reload

# Add CORS (optional, if your frontend needs it)
/subsystem=undertow/configuration=filter/response-header=cors-allow-origin:add(header-name=Access-Control-Allow-Origin,header-value="https://anis-nsir.me")
/subsystem=undertow/configuration=filter/response-header=cors-allow-methods:add(header-name=Access-Control-Allow-Methods,header-value="GET, POST, PUT, DELETE, PATCH, OPTIONS")
/subsystem=undertow/configuration=filter/response-header=cors-allow-headers:add(header-name=Access-Control-Allow-Headers,header-value="Origin, Content-Type, Accept, Authorization")
/subsystem=undertow/configuration=filter/response-header=cors-allow-credentials:add(header-name=Access-Control-Allow-Credentials,header-value="true")

/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-origin:add(predicate="true")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-methods:add(predicate="true")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-headers:add(predicate="true")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-credentials:add(predicate="true")

reload

# Configure secure cookies
/subsystem=undertow/servlet-container=default/setting=session-cookie:write-attribute(name=http-only,value=true)
/subsystem=undertow/servlet-container=default/setting=session-cookie:write-attribute(name=secure,value=true)
/subsystem=undertow/servlet-container=default/setting=session-cookie:write-attribute(name=same-site-mode,value=strict)

reload
```

#### Step 4: Deploy Application
```bash
# Build the application
cd /path/to/Application-Security-Project/src
mvn clean package

# Deploy
cp target/phoenix-iam.war $WILDFLY_HOME/standalone/deployments/

# Monitor deployment
tail -f $WILDFLY_HOME/standalone/log/server.log
```

### 4. Verify Everything Works

#### Test 1: Check Virtual Hosts
```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect

/subsystem=undertow/server=default-server:read-children-names(child-type=host)

# Should show: default-host, admin-host, iam-host
```

#### Test 2: Check Datasource
```bash
/subsystem=datasources/data-source=MySqlDS:test-connection-in-pool

# Should return success
```

#### Test 3: Check Deployment
```bash
deployment-info

# Should show phoenix-iam.war with OK status
```

#### Test 4: Test Endpoints
```bash
# From your browser or curl:
curl -k https://iam.anis-nsir.me/jwk
# Should return JSON with public keys

curl -k "https://iam.anis-nsir.me/authorize?client_id=anis-app&response_type=code&redirect_uri=https://anis-nsir.me/callback&code_challenge_method=S256&code_challenge=test&scope=openid"
# Should return login page HTML
```

### 5. Common Issues and Solutions

#### Issue: "Virtual host 'iam-host' not found"
**Solution:** You didn't add the iam-host. Run the commands in Step 3.

#### Issue: "Datasource MySqlDS not found"
**Solution:** Add the datasource configuration in Step 3.

#### Issue: "Connection to database failed"
**Solution:** 
- Check MySQL is running: `systemctl status mysql`
- Check credentials are correct
- Check database exists: `mysql -u phoenix_user -p -e "SHOW DATABASES;"`

#### Issue: "Certificate error"
**Solution:** Make sure your SSL certificate includes iam.anis-nsir.me subdomain

#### Issue: "404 Not Found"
**Solution:** 
- Check deployment: `ls $WILDFLY_HOME/standalone/deployments/`
- Check logs: `tail -f $WILDFLY_HOME/standalone/log/server.log`
- Verify WAR file name matches: `phoenix-iam.war`

### 6. Important Security Notes

1. **Change default admin password** in database immediately!
2. **Use strong database passwords**
3. **Keep SSL certificate up to date**
4. **Restrict database access** to localhost only
5. **Review CORS settings** - only allow your domains
6. **Enable firewall** - only ports 80, 443 open to internet
7. **Regular updates** - Keep WildFly, MySQL, and Java updated

### 7. Maintenance Commands

```bash
# Restart WildFly
systemctl restart wildfly

# View logs
tail -f $WILDFLY_HOME/standalone/log/server.log

# Undeploy application
$WILDFLY_HOME/bin/jboss-cli.sh --connect
undeploy phoenix-iam.war

# Redeploy
deploy /path/to/phoenix-iam.war

# Check application status
deployment-info

# Enable debug logging
/subsystem=logging/logger=xyz.kaaniche.phoenix:add(level=DEBUG)
```

### 8. Production Checklist

- [ ] MySQL database created
- [ ] Database schema initialized
- [ ] Admin password changed
- [ ] MySQL JDBC driver installed
- [ ] MySqlDS datasource configured and tested
- [ ] iam-host virtual host created
- [ ] Security headers applied to iam-host
- [ ] CORS configured (if needed)
- [ ] SSL certificate covers iam.anis-nsir.me
- [ ] phoenix-iam.war deployed successfully
- [ ] Endpoints tested and working
- [ ] Frontend application configured to use IAM
- [ ] Firewall configured
- [ ] Monitoring enabled

### 9. Next Steps

After completing the setup:

1. **Configure your frontend** application to use the OAuth 2.0 endpoints
2. **Create tenants** in the database for each application that needs authentication
3. **Create user accounts** for your users
4. **Test the complete OAuth flow** from login to token validation
5. **Monitor logs** for any errors or warnings
6. **Setup backup** for your database

For detailed information, see the full DEPLOYMENT.md guide.
