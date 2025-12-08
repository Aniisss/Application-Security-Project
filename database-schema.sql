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

-- Example data for testing (CRITICAL: CHANGE THESE BEFORE PRODUCTION DEPLOYMENT!)
-- WARNING: The following INSERT statements contain INSECURE placeholder values
-- These are meant to be replaced with proper values before running this script

-- Insert a sample tenant for the main application
-- SECURITY WARNING: Replace 'CHANGE_THIS_SECRET_IN_PRODUCTION' with a strong random secret (min 32 chars)
-- Example command to generate: openssl rand -hex 32
INSERT INTO tenants (tenant_id, tenant_secret, redirect_uri, allowed_roles, required_scopes, supported_grant_types)
VALUES ('anis-app', 'CHANGE_THIS_SECRET_IN_PRODUCTION', 'https://anis-nsir.me/callback', 7, 'openid profile email', 'authorization_code,refresh_token');

-- Insert a sample admin user
-- CRITICAL SECURITY WARNING: The password hash below is INVALID and will NOT work for login
-- You MUST generate a proper Argon2 hash for your desired password before running this script
-- The placeholder values 'CHANGETHISINSECURESALT' and 'CHANGETHISINSECUREHASH' are NOT valid
-- After deployment, you can generate a hash using the application's Argon2Utility class
-- For initial setup, temporarily comment out this INSERT and create the admin user manually after deployment
-- INSERT INTO identities (username, password, roles, provided_scopes)
-- VALUES ('admin', '$argon2id$v=19$m=97579,t=23,p=2$PLACEHOLDER_HASH_REPLACE_ME', 7, 'openid profile email admin');

-- Optional: Insert a grant for the admin user to access the app without consent
-- Uncomment this AFTER creating the admin user manually
-- INSERT INTO grants (tenant_name, identity_id, approved_scopes)
-- VALUES ('anis-app', 1, 'openid profile email');
