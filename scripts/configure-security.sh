#!/bin/bash

#############################################################
# WildFly HSTS and Security Configuration Script
# 
# This script configures HSTS, HTTP-to-HTTPS redirect,
# and other security headers for WildFly/Undertow
#
# Usage: ./configure-security.sh [DOMAIN] [HSTS_MAX_AGE]
# Example: ./configure-security.sh anis-nsir.me 63072000
#
# Prerequisites:
# - WildFly must be running
# - TLS/SSL must be already configured
# - Application should be deployed
#
# Note on HSTS max-age values:
#   - Testing: 86400 (1 day)
#   - Staging: 604800 (1 week)
#   - Production: 31536000 (1 year) or 63072000 (2 years)
#   - HSTS Preload requirement: minimum 31536000 (1 year)
#
#############################################################

# Default domain if not provided
DOMAIN="${1:-anis-nsir.me}"
ADMIN_DOMAIN="admin.${DOMAIN}"

# HSTS max-age in seconds (default: 1 year for production)
# Start with 86400 (1 day) for testing, then increase gradually
HSTS_MAX_AGE="${2:-31536000}"

# WildFly home directory - adjust if needed
WILDFLY_HOME="${WILDFLY_HOME:-/opt/wildfly}"
JBOSS_CLI="${WILDFLY_HOME}/bin/jboss-cli.sh"

echo "============================================"
echo "WildFly Security Configuration Script"
echo "Domain: ${DOMAIN}"
echo "Admin Domain: ${ADMIN_DOMAIN}"
echo "HSTS Max-Age: ${HSTS_MAX_AGE} seconds"
echo "============================================"

# Check if WildFly CLI exists
if [ ! -f "$JBOSS_CLI" ]; then
    echo "ERROR: WildFly CLI not found at ${JBOSS_CLI}"
    echo "Please set WILDFLY_HOME environment variable"
    exit 1
fi

# Check if WildFly is running
if ! $JBOSS_CLI --connect --command=":read-attribute(name=server-state)" 2>/dev/null | grep -q "running"; then
    echo "ERROR: WildFly does not appear to be running"
    echo "Please start WildFly first: ${WILDFLY_HOME}/bin/standalone.sh"
    exit 1
fi

echo ""
echo "Step 1: Configuring static content directories..."
echo "---------------------------------------------"

# Create static content directories if they don't exist
echo "Creating static content directories..."
sudo mkdir -p /var/www/root
sudo mkdir -p /var/www/admin
sudo chown -R wildfly:wildfly /var/www/root /var/www/admin 2>/dev/null || true
echo "Note: Place your frontend SPA files in /var/www/root and /var/www/admin"

$JBOSS_CLI --connect << EOF
batch

# Configure root static content (optional - for SPA)
/subsystem=undertow/configuration=handler/file=welcome-content:write-attribute(name=path,value="/var/www/root")

run-batch
EOF

echo "Reloading server..."
$JBOSS_CLI --connect --command="reload"
sleep 5

echo ""
echo "Step 2: Configuring admin host and content..."
echo "---------------------------------------------"

$JBOSS_CLI --connect << EOF
batch

# Add admin content handler (serves files from /var/www/admin)
try
/subsystem=undertow/configuration=handler/file=admin-content:add(path="/var/www/admin")
catch
echo "admin-content handler may already exist"
end-try

# Add admin host
try
/subsystem=undertow/server=default-server/host=admin-host:add(alias=["${ADMIN_DOMAIN}"])
catch
echo "admin-host may already exist"
end-try

# Add location handler for admin host
try
/subsystem=undertow/server=default-server/host=admin-host/location=/:add(handler=admin-content)
catch
echo "admin-host location may already exist"
end-try

run-batch
EOF

echo "Reloading server..."
$JBOSS_CLI --connect --command="reload"
sleep 5

echo ""
echo "Step 3: Configuring HSTS header..."
echo "---------------------------------------------"

$JBOSS_CLI --connect << EOF
batch

# Add HSTS response header filter
try
/subsystem=undertow/configuration=filter/response-header=hsts:add(header-name=Strict-Transport-Security,header-value="max-age=${HSTS_MAX_AGE}; includeSubDomains; preload")
catch
echo "HSTS filter may already exist"
end-try

run-batch
EOF

echo ""
echo "Step 4: Configuring HTTP to HTTPS redirect..."
echo "---------------------------------------------"

$JBOSS_CLI --connect << EOF
batch

# Add HTTP to HTTPS redirect filter
try
/subsystem=undertow/configuration=filter/rewrite=http-to-https:add(target="https://%v%U",redirect=true)
catch
echo "http-to-https filter may already exist"
end-try

run-batch
EOF

echo ""
echo "Step 5: Applying filters to hosts..."
echo "---------------------------------------------"

$JBOSS_CLI --connect << EOF
batch

# Apply HSTS to default-host (only on HTTPS - port 443)
try
/subsystem=undertow/server=default-server/host=default-host/filter-ref=hsts:add(predicate="equals(%p,443)")
catch
echo "HSTS filter-ref on default-host may already exist"
end-try

# Apply HTTP to HTTPS redirect on default-host (on HTTP - port 80)
try
/subsystem=undertow/server=default-server/host=default-host/filter-ref=http-to-https:add(predicate="equals(%p,80)")
catch
echo "http-to-https filter-ref on default-host may already exist"
end-try

# Apply HSTS to admin-host
try
/subsystem=undertow/server=default-server/host=admin-host/filter-ref=hsts:add(predicate="equals(%p,443)")
catch
echo "HSTS filter-ref on admin-host may already exist"
end-try

# Apply HTTP to HTTPS redirect on admin-host
try
/subsystem=undertow/server=default-server/host=admin-host/filter-ref=http-to-https:add(predicate="equals(%p,80)")
catch
echo "http-to-https filter-ref on admin-host may already exist"
end-try

run-batch
EOF

echo ""
echo "Step 6: Configuring SPA routing..."
echo "---------------------------------------------"

$JBOSS_CLI --connect << EOF
batch

# Configure SPA fallback for default host
try
/subsystem=undertow/configuration=filter/error-page=spa-default-router:add(code="404",path="/var/www/root/index.html")
catch
echo "spa-default-router may already exist"
end-try

try
/subsystem=undertow/server=default-server/host=default-host/filter-ref=spa-default-router:add(predicate="true")
catch
echo "spa-default-router filter-ref may already exist"
end-try

# Configure SPA fallback for admin host
try
/subsystem=undertow/configuration=filter/error-page=spa-admin-router:add(code="404",path="/var/www/admin/index.html")
catch
echo "spa-admin-router may already exist"
end-try

try
/subsystem=undertow/server=default-server/host=admin-host/filter-ref=spa-admin-router:add(predicate="true")
catch
echo "spa-admin-router filter-ref may already exist"
end-try

run-batch
EOF

echo ""
echo "Step 7: Configuring Cache-Control header..."
echo "---------------------------------------------"

$JBOSS_CLI --connect << EOF
batch

# Add Cache-Control header
try
/subsystem=undertow/configuration=filter/response-header=cache-control:add(header-name=Cache-Control,header-value="private,no-cache")
catch
echo "cache-control filter may already exist"
end-try

try
/subsystem=undertow/server=default-server/host=admin-host/filter-ref=cache-control:add(predicate="true")
catch
echo "cache-control filter-ref may already exist"
end-try

run-batch
EOF

echo "Reloading server..."
$JBOSS_CLI --connect --command="reload"
sleep 5

echo ""
echo "Step 8: Configuring X-Content-Type-Options header..."
echo "---------------------------------------------"

$JBOSS_CLI --connect << EOF
batch

# Add X-Content-Type-Options header
try
/subsystem=undertow/configuration=filter/response-header=content-type-options:add(header-name=X-Content-Type-Options,header-value="nosniff")
catch
echo "content-type-options filter may already exist"
end-try

try
/subsystem=undertow/server=default-server/host=default-host/filter-ref=content-type-options:add(predicate="true")
catch
echo "content-type-options filter-ref may already exist"
end-try

run-batch
EOF

echo ""
echo "Final reload..."
$JBOSS_CLI --connect --command="reload"
sleep 5

echo ""
echo "============================================"
echo "Security Configuration Complete!"
echo "============================================"
echo ""
echo "Verification Commands:"
echo "  curl -I https://${DOMAIN}"
echo "  curl -I http://${DOMAIN}"
echo ""
echo "Expected HTTPS headers:"
echo "  strict-transport-security: max-age=${HSTS_MAX_AGE}; includeSubDomains; preload"
echo "  x-content-type-options: nosniff"
echo ""
echo "HTTP should redirect to HTTPS with 301/302"
echo ""
