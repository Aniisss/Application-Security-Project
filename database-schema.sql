-- Phoenix IAM Database Schema
-- This script creates the necessary tables for the Phoenix IAM application

-- Drop tables if they exist (for fresh installation)
DROP TABLE IF EXISTS grants;
DROP TABLE IF EXISTS identities;
DROP TABLE IF EXISTS tenants;

-- Create tenants table
CREATE TABLE tenants (
    id SMALLINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(191) NOT NULL UNIQUE,
    tenant_secret VARCHAR(255) NOT NULL,
    redirect_uri VARCHAR(512) NOT NULL,
    allowed_roles BIGINT NOT NULL,
    required_scopes VARCHAR(512) NOT NULL,
    supported_grant_types VARCHAR(255) NOT NULL,
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create identities table
CREATE TABLE identities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(191) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    roles BIGINT NOT NULL,
    provided_scopes VARCHAR(512) NOT NULL,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create grants table
CREATE TABLE grants (
    tenant_name VARCHAR(191) NOT NULL,
    identity_id BIGINT NOT NULL,
    approved_scopes VARCHAR(512) NOT NULL,
    PRIMARY KEY (tenant_name, identity_id),
    FOREIGN KEY (identity_id) REFERENCES identities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Example data for testing (CHANGE THESE IN PRODUCTION!)
-- Insert a sample tenant for the main application
INSERT INTO tenants (tenant_id, tenant_secret, redirect_uri, allowed_roles, required_scopes, supported_grant_types)
VALUES ('anis-app', 'CHANGE_THIS_SECRET_IN_PRODUCTION', 'https://anis-nsir.me/callback', 7, 'openid profile email', 'authorization_code,refresh_token');

-- Insert a sample admin user (password: 'admin' - MUST BE CHANGED!)
-- This is an Argon2 hash - you should generate your own using the application's Argon2Utility
-- The default password is 'admin' - CHANGE THIS IMMEDIATELY!
-- To generate a proper hash, use the Argon2Utility class with your desired password
INSERT INTO identities (username, password, roles, provided_scopes)
VALUES ('admin', '$argon2id$v=19$m=97579,t=23,p=2$CHANGETHISINSECURESALT$CHANGETHISINSECUREHASH', 7, 'openid profile email admin');

-- Optional: Insert a grant for the admin user to access the app without consent
INSERT INTO grants (tenant_name, identity_id, approved_scopes)
VALUES ('anis-app', 1, 'openid profile email');
