#!/bin/bash
# WildFly Configuration Script for Phoenix IAM
# This script configures WildFly to properly host the Phoenix IAM application
# Run this script using the WildFly CLI (jboss-cli.sh)

# Connect to WildFly CLI
# ./jboss-cli.sh --connect --file=wildfly-config.sh

# OR run commands manually via CLI after connecting:
# ./jboss-cli.sh --connect

# ============================================================================
# 1. Configure Virtual Host for IAM (iam.anis-nsir.me)
# ============================================================================

# Add iam-host virtual host configuration
/subsystem=undertow/server=default-server/host=iam-host:add(alias=["iam.anis-nsir.me"],default-web-module="phoenix-iam.war")

# Add location handler for iam-host root
/subsystem=undertow/server=default-server/host=iam-host/location=\/:add(handler=welcome-content)

# Apply security headers to iam-host
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=hsts:add(predicate="equals(%p,443)")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=http-to-https:add(predicate="equals(%p,80)")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=content-type-options:add(predicate="true")

reload

# ============================================================================
# 2. Configure MySQL DataSource
# ============================================================================

# Add MySQL JDBC driver module (if not already added)
# You need to manually create the module structure first:
# $WILDFLY_HOME/modules/com/mysql/main/
# Place mysql-connector-java.jar in that directory
# Create module.xml with proper configuration

# Add MySQL driver
/subsystem=datasources/jdbc-driver=mysql:add(driver-name=mysql,driver-module-name=com.mysql,driver-class-name=com.mysql.cj.jdbc.Driver,driver-xa-datasource-class-name=com.mysql.cj.jdbc.MysqlXADataSource)

# Add MySQL datasource
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

# Test the datasource
/subsystem=datasources/data-source=MySqlDS:test-connection-in-pool

reload

# ============================================================================
# 3. Configure CORS for API access
# ============================================================================

# Add CORS filter for cross-origin requests
/subsystem=undertow/configuration=filter/response-header=cors-allow-origin:add(header-name=Access-Control-Allow-Origin,header-value="https://anis-nsir.me")
/subsystem=undertow/configuration=filter/response-header=cors-allow-methods:add(header-name=Access-Control-Allow-Methods,header-value="GET, POST, PUT, DELETE, PATCH, OPTIONS")
/subsystem=undertow/configuration=filter/response-header=cors-allow-headers:add(header-name=Access-Control-Allow-Headers,header-value="Origin, Content-Type, Accept, Authorization")
/subsystem=undertow/configuration=filter/response-header=cors-allow-credentials:add(header-name=Access-Control-Allow-Credentials,header-value="true")
/subsystem=undertow/configuration=filter/response-header=cors-max-age:add(header-name=Access-Control-Max-Age,header-value="3600")

# Apply CORS filters to iam-host
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-origin:add(predicate="true")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-methods:add(predicate="true")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-headers:add(predicate="true")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-allow-credentials:add(predicate="true")
/subsystem=undertow/server=default-server/host=iam-host/filter-ref=cors-max-age:add(predicate="true")

reload

# ============================================================================
# 4. Configure Session Management
# ============================================================================

# Set session timeout to 30 minutes (in seconds)
/subsystem=undertow/servlet-container=default/setting=session-cookie:write-attribute(name=http-only,value=true)
/subsystem=undertow/servlet-container=default/setting=session-cookie:write-attribute(name=secure,value=true)
/subsystem=undertow/servlet-container=default/setting=session-cookie:write-attribute(name=same-site-mode,value=strict)

reload

# ============================================================================
# 5. Optional: Increase logging for debugging
# ============================================================================

# Enable debug logging for the application (uncomment if needed)
# /subsystem=logging/logger=xyz.kaaniche.phoenix:add(level=DEBUG)

# ============================================================================
# Configuration Complete
# ============================================================================

echo "WildFly configuration for Phoenix IAM completed!"
echo "Please ensure you have:"
echo "1. Created the MySQL database 'phoenix_iam'"
echo "2. Run the database-schema.sql script"
echo "3. Updated the database password in the datasource configuration"
echo "4. Placed your SSL certificate in $WILDFLY_HOME/standalone/configuration/"
echo "5. Updated the certificate password in the SSL configuration"
echo "6. Deployed phoenix-iam.war to $WILDFLY_HOME/standalone/deployments/"
