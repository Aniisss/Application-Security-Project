const crypto = require('crypto');
const config = require('./config');

/**
 * PKCE (Proof Key for Code Exchange) utilities
 * Required by the Phoenix IAM for authorization code flow
 */

/**
 * Generate a cryptographically random code verifier
 * @returns {string} Base64 URL-encoded random string
 */
function generateCodeVerifier() {
    return base64URLEncode(crypto.randomBytes(32));
}

/**
 * Generate code challenge from code verifier using SHA256
 * @param {string} verifier - The code verifier
 * @returns {string} Base64 URL-encoded SHA256 hash
 */
function generateCodeChallenge(verifier) {
    return base64URLEncode(crypto.createHash('sha256').update(verifier).digest());
}

/**
 * Base64 URL encode (without padding)
 * @param {Buffer} buffer - Buffer to encode
 * @returns {string} Base64 URL-encoded string
 */
function base64URLEncode(buffer) {
    return buffer.toString('base64')
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=/g, '');
}

/**
 * Build the authorization URL for the IAM
 * @param {string} codeChallenge - PKCE code challenge
 * @param {string} state - Random state parameter for CSRF protection
 * @returns {string} Complete authorization URL
 */
function getAuthorizationUrl(codeChallenge, state) {
    const params = new URLSearchParams({
        response_type: 'code',
        client_id: config.clientId,
        redirect_uri: config.redirectUri,
        scope: config.scope,
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        state: state
    });
    
    return `${config.iamBaseUrl}/authorize?${params.toString()}`;
}

/**
 * Exchange authorization code for access token
 * @param {string} code - Authorization code from IAM
 * @param {string} codeVerifier - PKCE code verifier
 * @returns {Promise<Object>} Token response
 */
async function exchangeCodeForToken(code, codeVerifier) {
    const params = new URLSearchParams({
        grant_type: 'authorization_code',
        code: code,
        code_verifier: codeVerifier
    });

    // Node.js 18+ has native fetch, older versions need a polyfill
    // For production, consider using node-fetch or axios for better compatibility
    const fetchFn = globalThis.fetch || require('node-fetch');
    
    const response = await fetchFn(`${config.iamBaseUrl}/oauth/token`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: params.toString()
    });

    if (!response.ok) {
        const error = await response.text();
        throw new Error(`Token exchange failed: ${error}`);
    }

    return await response.json();
}

/**
 * Decode JWT token (without verification - for display purposes only)
 * @param {string} token - JWT token
 * @returns {Object} Decoded token payload
 */
function decodeToken(token) {
    const parts = token.split('.');
    if (parts.length !== 3) {
        throw new Error('Invalid JWT token');
    }
    
    const payload = parts[1];
    // JWT uses base64url encoding, so we need to convert to standard base64
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    // Add padding if necessary
    const padded = base64 + '='.repeat((4 - base64.length % 4) % 4);
    const decoded = Buffer.from(padded, 'base64').toString('utf8');
    return JSON.parse(decoded);
}

module.exports = {
    generateCodeVerifier,
    generateCodeChallenge,
    getAuthorizationUrl,
    exchangeCodeForToken,
    decodeToken
};
