# Demo App - OAuth 2.0 Client

This is a demo application that implements OAuth 2.0 authorization code flow with PKCE for authentication with Phoenix IAM.

## Setup

1. Install dependencies:
```bash
npm install
```

2. Copy the environment example file:
```bash
cp .env.example .env
```

3. Update the `.env` file with your configuration if needed.

## Running the Application

### Development Mode
```bash
npm run dev
```

### Production Mode
```bash
npm start
```

## Configuration

### Session Cookies and Cross-Origin OAuth

This application uses `sameSite: 'none'` for session cookies to support OAuth redirects from the IAM server (which runs on a different origin). This is required because:

- The IAM server runs on `localhost:8080` (or another domain)
- The application runs on `localhost:3000`
- OAuth redirects from IAM to the application are cross-origin
- Without `sameSite: 'none'`, the session cookie won't be sent during the redirect

**Important Notes for Development:**

Modern browsers enforce that `sameSite: 'none'` requires `secure: true` (HTTPS). Since the development environment uses HTTP:

**Option 1: Use Chrome with Relaxed Cookie Policy (Easiest)**
```bash
# Windows
chrome.exe --disable-features=SameSiteByDefaultCookies

# macOS
open -a "Google Chrome" --args --disable-features=SameSiteByDefaultCookies

# Linux
google-chrome --disable-features=SameSiteByDefaultCookies
```

**Option 2: Use HTTPS in Development**
Set up a local HTTPS server with self-signed certificates.

**Option 3: Same-Origin Setup**
Run both the IAM and application on the same origin (same domain and port) using a reverse proxy.

### Environment Variables

- `IAM_BASE_URL`: Base URL of the Phoenix IAM server (default: `http://localhost:8080`)
- `CLIENT_ID`: OAuth client ID (default: `demo-app`)
- `REDIRECT_URI`: OAuth redirect URI (default: `http://localhost:3000/callback`)
- `SCOPE`: OAuth scopes (default: `openid profile`)
- `PORT`: Application port (default: `3000`)
- `HOST`: Application host (default: `localhost`)
- `SESSION_SECRET`: Secret for signing session cookies (change in production!)
- `NODE_ENV`: Environment mode (`development` or `production`)

## Security Features

- **PKCE (Proof Key for Code Exchange)**: Prevents authorization code interception attacks
- **CSRF Protection**: Uses state parameter to prevent cross-site request forgery
- **HTTP-Only Cookies**: Session cookies are not accessible via JavaScript
- **Secure Cookies in Production**: Cookies are only sent over HTTPS in production

## Architecture

- `src/index.js`: Main Express application with routes and session management
- `src/oauth.js`: OAuth 2.0 and PKCE utilities
- `src/config.js`: Configuration management
- `public/`: Static files (HTML, CSS, JS)
