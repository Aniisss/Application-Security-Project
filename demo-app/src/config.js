// OAuth 2.0 Configuration for Phoenix IAM
module.exports = {
    // IAM Server Configuration
    iamBaseUrl: process.env.IAM_BASE_URL || 'http://localhost:8080',
    
    // OAuth Client Configuration
    clientId: process.env.CLIENT_ID || 'demo-app',
    redirectUri: process.env.REDIRECT_URI || 'http://localhost:3000/callback',
    
    // OAuth Scopes
    scope: process.env.SCOPE || 'openid profile',
    
    // Application Configuration
    port: process.env.PORT || 3000,
    host: process.env.HOST || 'localhost',
    sessionSecret: process.env.SESSION_SECRET || 'your-secret-key-change-in-production'
};
