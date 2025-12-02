#!/bin/bash

#############################################################
# WildFly Database and Deployment Script
# 
# This script configures MySQL datasource and deploys
# the Phoenix IAM application to WildFly
#
# Usage: ./deploy.sh [WAR_PATH]
# Example: ./deploy.sh ./target/phoenix-iam.war
#
# Prerequisites:
# - WildFly must be running
# - MySQL must be running
# - MySQL JDBC driver must be available
#
#############################################################

# Configuration - EDIT THESE VALUES
WAR_PATH="${1:-./target/phoenix-iam.war}"
DOMAIN="${2:-anis-nsir.me}"

# Database configuration - EDIT THESE VALUES
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-phoenix_iam}"
DB_USER="${DB_USER:-phoenix}"
DB_PASS="${DB_PASS:-your_secure_password}"

# MySQL JDBC driver path - EDIT THIS VALUE
MYSQL_DRIVER_PATH="${MYSQL_DRIVER_PATH:-/opt/mysql-connector-j-8.2.0.jar}"

# WildFly home directory - adjust if needed
WILDFLY_HOME="${WILDFLY_HOME:-/opt/wildfly}"
JBOSS_CLI="${WILDFLY_HOME}/bin/jboss-cli.sh"

echo "============================================"
echo "Phoenix IAM Deployment Script"
echo "Domain: ${DOMAIN}"
echo "WAR Path: ${WAR_PATH}"
echo "============================================"

# Check if WildFly CLI exists
if [ ! -f "$JBOSS_CLI" ]; then
    echo "ERROR: WildFly CLI not found at ${JBOSS_CLI}"
    echo "Please set WILDFLY_HOME environment variable"
    exit 1
fi

# Check if WAR file exists
if [ ! -f "$WAR_PATH" ]; then
    echo "ERROR: WAR file not found at ${WAR_PATH}"
    echo "Please build the application first: mvn clean package"
    exit 1
fi

# Check if WildFly is running
if ! $JBOSS_CLI --connect --command=":read-attribute(name=server-state)" 2>/dev/null | grep -q "running"; then
    echo "ERROR: WildFly does not appear to be running"
    echo "Please start WildFly first: ${WILDFLY_HOME}/bin/standalone.sh"
    exit 1
fi

echo ""
echo "Step 1: Adding MySQL JDBC Driver Module..."
echo "---------------------------------------------"

if [ -f "$MYSQL_DRIVER_PATH" ]; then
    $JBOSS_CLI --connect << EOF
module add --name=com.mysql --resources=${MYSQL_DRIVER_PATH} --dependencies=javax.api,javax.transaction.api
EOF
    echo "MySQL module added (or already exists)"
else
    echo "WARNING: MySQL driver not found at ${MYSQL_DRIVER_PATH}"
    echo "Please download mysql-connector-j and set MYSQL_DRIVER_PATH"
fi

echo ""
echo "Step 2: Configuring JDBC Driver..."
echo "---------------------------------------------"

$JBOSS_CLI --connect << EOF
batch
try
/subsystem=datasources/jdbc-driver=mysql:add(driver-name=mysql,driver-module-name=com.mysql,driver-class-name=com.mysql.cj.jdbc.Driver)
catch
echo "JDBC driver may already exist"
end-try
run-batch
EOF

echo ""
echo "Step 3: Creating MySQL Datasource..."
echo "---------------------------------------------"

$JBOSS_CLI --connect << EOF
batch
try
data-source add --name=MySqlDS --jndi-name=java:jboss/datasources/MySqlDS --driver-name=mysql --connection-url=jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME} --user-name=${DB_USER} --password=${DB_PASS} --min-pool-size=5 --max-pool-size=20 --valid-connection-checker-class-name=org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLValidConnectionChecker --exception-sorter-class-name=org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLExceptionSorter
catch
echo "Datasource may already exist"
end-try
run-batch
EOF

echo ""
echo "Step 4: Configuring IAM Virtual Host..."
echo "---------------------------------------------"

$JBOSS_CLI --connect << EOF
batch
try
/subsystem=undertow/server=default-server/host=iam-host:add(alias=["${DOMAIN}"])
catch
echo "iam-host may already exist"
end-try
run-batch
EOF

echo "Reloading server..."
$JBOSS_CLI --connect --command="reload"
sleep 5

echo ""
echo "Step 5: Deploying Application..."
echo "---------------------------------------------"

# Undeploy if exists
$JBOSS_CLI --connect --command="undeploy phoenix-iam.war" 2>/dev/null || true

# Deploy
$JBOSS_CLI --connect --command="deploy ${WAR_PATH}"

echo ""
echo "Step 6: Testing Datasource Connection..."
echo "---------------------------------------------"

$JBOSS_CLI --connect --command="/subsystem=datasources/data-source=MySqlDS:test-connection-in-pool"

echo ""
echo "============================================"
echo "Deployment Complete!"
echo "============================================"
echo ""
echo "Application should be available at:"
echo "  https://${DOMAIN}/rest-iam/authorize"
echo ""
echo "Next steps:"
echo "  1. Run configure-security.sh to set up HSTS"
echo "  2. Configure CAA DNS records"
echo "  3. Test the application"
echo ""
