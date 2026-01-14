const express = require('express');
const session = require('express-session');
const path = require('path');
const crypto = require('crypto');
require('dotenv').config();

const config = require('./config');
const oauth = require('./oauth');

const app = express();

// Session configuration for storing OAuth state
app.use(session({
  secret: config.sessionSecret,
  resave: false,
  saveUninitialized: false,
  cookie: { 
    secure: process.env.NODE_ENV === 'production', 
    httpOnly: true,
    maxAge: 3600000 // 1 hour
  }
}));

// Middleware to parse JSON and URL-encoded bodies
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Serve static files from the 'public' directory
app.use(express.static(path.join(__dirname, '../public')));

// Middleware to check if user is authenticated
function requireAuth(req, res, next) {
  if (!req.session.user) {
    return res.redirect('/');
  }
  next();
}

// Home page - shows login button if not authenticated
app.get('/', (req, res) => {
  if (req.session.user) {
    return res.redirect('/dashboard');
  }
  res.sendFile(path.join(__dirname, '../public/index.html'));
});

// Login endpoint - initiates OAuth flow
app.get('/login', (req, res) => {
  // Generate PKCE parameters
  const codeVerifier = oauth.generateCodeVerifier();
  const codeChallenge = oauth.generateCodeChallenge(codeVerifier);
  
  // Generate state for CSRF protection
  const state = crypto.randomBytes(16).toString('hex');
  
  // Store in session
  req.session.codeVerifier = codeVerifier;
  req.session.state = state;
  
  // Redirect to IAM authorization endpoint
  const authUrl = oauth.getAuthorizationUrl(codeChallenge, state);
  res.redirect(authUrl);
});

// OAuth callback endpoint
app.get('/callback', async (req, res) => {
  const { code, state, error, error_description } = req.query;
  
  // Check for errors
  if (error) {
    return res.status(400).send(`OAuth Error: ${error} - ${error_description || 'Unknown error'}`);
  }
  
  // Validate state parameter
  if (!state || state !== req.session.state) {
    return res.status(400).send('Invalid state parameter - possible CSRF attack');
  }
  
  // Validate code
  if (!code) {
    return res.status(400).send('No authorization code received');
  }
  
  try {
    // Exchange code for token
    const tokenResponse = await oauth.exchangeCodeForToken(code, req.session.codeVerifier);
    
    // Decode the access token to get user info
    const userInfo = oauth.decodeToken(tokenResponse.access_token);
    
    // Store user session
    // Note: For production, consider storing only a session ID and keeping tokens
    // in a more secure backend storage (e.g., Redis, encrypted database)
    // to prevent exposure in server logs or memory dumps
    req.session.user = {
      username: userInfo.sub,
      scopes: userInfo.scope,
      roles: userInfo.groups || [],
      accessToken: tokenResponse.access_token,
      refreshToken: tokenResponse.refresh_token,
      tokenType: tokenResponse.token_type
    };
    
    // Clean up OAuth state
    delete req.session.codeVerifier;
    delete req.session.state;
    
    // Redirect to dashboard
    res.redirect('/dashboard');
  } catch (error) {
    console.error('Token exchange error:', error);
    res.status(500).send(`Authentication failed: ${error.message}`);
  }
});

// Protected dashboard route
app.get('/dashboard', requireAuth, (req, res) => {
  res.sendFile(path.join(__dirname, '../public/dashboard.html'));
});

// API endpoint to get user info
app.get('/api/user', requireAuth, (req, res) => {
  res.json(req.session.user);
});

// Logout endpoint
app.get('/logout', (req, res) => {
  req.session.destroy((err) => {
    if (err) {
      console.error('Logout error:', err);
    }
    res.redirect('/');
  });
});

// Start the Server
app.listen(config.port, () => {
  console.log(`Server running at http://${config.host}:${config.port}`);
  console.log(`IAM Server: ${config.iamBaseUrl}`);
  console.log(`Client ID: ${config.clientId}`);
  console.log(`Redirect URI: ${config.redirectUri}`);
});