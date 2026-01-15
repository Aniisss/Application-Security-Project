const express = require('express');
const session = require('express-session');
const path = require('path');
const crypto = require('crypto');
require('dotenv').config();

const config = require('./config');
const oauth = require('./oauth');

const app = express();

// CORS MIDDLEWARE 
app. use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', 'http://localhost:8080');
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept, Authorization');
  res.header('Access-Control-Allow-Credentials', 'true');
  
  // Handle preflight requests
  if (req. method === 'OPTIONS') {
    res.sendStatus(200);
  } else {
    next();
  }
});

// Session configuration for storing OAuth state
app.use(session({
  secret: config.sessionSecret,
  resave: false,
  saveUninitialized: false,
  cookie: { 
    secure: false, // Set to false to work with HTTP in development
    httpOnly: true,
    maxAge: 3600000, // 1 hour
    sameSite: 'none' // Required for cross-origin OAuth redirects
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
  
  // Debug logging (development only)
  if (process.env.NODE_ENV !== 'production') {
    console.log('[LOGIN] Generated state:', state);
    console.log('[LOGIN] Generated codeVerifier:', codeVerifier.substring(0, 10) + '...');
    console.log('[LOGIN] Session ID:', req.sessionID);
  }
  
  // Store in session
  req.session.codeVerifier = codeVerifier;
  req.session.state = state;
  
  // Ensure session is saved before redirecting
  req.session.save((err) => {
    if (err) {
      console.error('[LOGIN] Session save error:', process.env.NODE_ENV === 'production' ? err.message : err);
      return res.status(500).send('Failed to save session');
    }
    
    if (process.env.NODE_ENV !== 'production') {
      console.log('[LOGIN] Session saved, redirecting to IAM');
    }
    
    // Redirect to IAM authorization endpoint
    const authUrl = oauth.getAuthorizationUrl(codeChallenge, state);
    res.redirect(authUrl);
  });
});

// OAuth callback endpoint
app.get('/callback', async (req, res) => {
  const { code, state, error, error_description } = req.query;
  
  // Debug logging (development only)
  if (process.env.NODE_ENV !== 'production') {
    console.log('[CALLBACK] Received callback request');
    console.log('[CALLBACK] Query params:', { 
      code: code ? 'present' : 'missing', 
      state: state && state.length > 8 ? state.substring(0, 8) + '...' : state || 'missing', 
      error 
    });
    console.log('[CALLBACK] Session state:', req.session.state && req.session.state.length > 8 ? req.session.state.substring(0, 8) + '...' : req.session.state || 'missing');
    console.log('[CALLBACK] Session codeVerifier:', req.session.codeVerifier ? 'present' : 'missing');
  }
  
  // Check for errors
  if (error) {
    console.error('[CALLBACK] OAuth error from IAM:', error, error_description);
    return res.status(400).send(`OAuth Error: ${error} - ${error_description || 'Unknown error'}`);
  }
  
  // Validate state parameter
  if (!state || state !== req.session.state) {
    console.error('[CALLBACK] State validation failed - possible CSRF attack');
    if (process.env.NODE_ENV !== 'production') {
      console.error('[CALLBACK] State mismatch details:', {
        received: state ? 'present' : 'missing',
        expected: req.session.state ? 'present' : 'missing',
        match: state === req.session.state
      });
    }
    return res.status(400).send('Invalid state parameter - possible CSRF attack');
  }
  
  // Validate code
  if (!code) {
    console.error('[CALLBACK] No authorization code received - possible authentication failure');
    return res.status(400).send('No authorization code received');
  }
  
  try {
    if (process.env.NODE_ENV !== 'production') {
      console.log('[CALLBACK] Exchanging code for token...');
    }
    
    // Exchange code for token
    const tokenResponse = await oauth.exchangeCodeForToken(code, req.session.codeVerifier);
    
    if (process.env.NODE_ENV !== 'production') {
      console.log('[CALLBACK] Token exchange successful');
    }
    
    // Decode the access token to get user info
    const userInfo = oauth.decodeToken(tokenResponse.access_token);
    
    if (process.env.NODE_ENV !== 'production') {
      console.log('[CALLBACK] User authenticated:', userInfo.sub);
    }
    
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
    console.error('[CALLBACK] Token exchange error:', error.message);
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