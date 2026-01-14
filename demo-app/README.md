# Phoenix IAM Demo Application

This demo application demonstrates the OAuth 2.0 Authorization Code Flow with PKCE (Proof Key for Code Exchange) integration with the Phoenix IAM system.

## Features

- **OAuth 2.0 Authorization Code Flow**: Full implementation of the authorization code flow
- **PKCE Support**: Implements PKCE for enhanced security
- **JWT Token Authentication**: Validates and decodes JWT tokens from the IAM
- **Session Management**: Secure session handling with express-session
- **Protected Routes**: Example of route protection requiring authentication

## Architecture

The demo app follows a simple architecture:

```
User Browser <-> Demo App (Express.js) <-> Phoenix IAM
```

### OAuth Flow

1. User clicks "Login with Phoenix IAM"
2. Demo app generates PKCE code verifier and challenge
3. Demo app redirects to IAM's `/authorize` endpoint with:
   - `client_id`: demo-app
   - `redirect_uri`: http://localhost:3000/callback
   - `response_type`: code
   - `code_challenge`: SHA256(code_verifier)
   - `code_challenge_method`: S256
   - `scope`: openid profile
   - `state`: random CSRF token

4. User authenticates with Phoenix IAM
5. IAM redirects back to demo app's `/callback` with authorization code
6. Demo app exchanges code for access token at `/oauth/token` using:
   - `grant_type`: authorization_code
   - `code`: authorization code
   - `code_verifier`: original PKCE verifier

7. Demo app receives access token and refresh token
8. Demo app decodes JWT to extract user info
9. Demo app creates session and redirects to dashboard

## Configuration

Create a `.env` file based on `.env.example`:

```bash
cp .env.example .env
```

Key configuration variables:

- `IAM_BASE_URL`: URL of the Phoenix IAM server (default: http://localhost:8080)
- `CLIENT_ID`: OAuth client ID (must be registered in IAM)
- `REDIRECT_URI`: Callback URL for OAuth flow
- `SCOPE`: OAuth scopes requested
- `SESSION_SECRET`: Secret for session encryption

## Running the Demo

### Prerequisites

1. Phoenix IAM must be running and accessible
2. A tenant/client must be registered in the IAM with:
   - Client ID: `demo-app` (or match your CLIENT_ID)
   - Redirect URI: `http://localhost:3000/callback` (or match your REDIRECT_URI)
   - Supported grant types: `authorization_code`
   - Required scopes: `openid profile`

3. At least one user must exist in the IAM

### Start the Application

```bash
# Install dependencies
npm install

# Start the application
npm start

# Or for development with auto-reload
npm run dev
```

The application will be available at `http://localhost:3000`

## Endpoints

### Public Endpoints

- `GET /` - Home page with login button
- `GET /login` - Initiates OAuth flow
- `GET /callback` - OAuth callback endpoint

### Protected Endpoints

- `GET /dashboard` - User dashboard (requires authentication)
- `GET /api/user` - Returns user info from session (requires authentication)
- `GET /logout` - Destroys session and logs out user

## Security Features

1. **PKCE**: Protects against authorization code interception
2. **State Parameter**: CSRF protection for OAuth flow
3. **HTTP-Only Cookies**: Session cookies are HTTP-only
4. **Secure Sessions**: Session data is encrypted
5. **Input Validation**: All OAuth parameters are validated

## Dependencies

- `express`: Web framework
- `express-session`: Session management
- `dotenv`: Environment variable loading

## Testing the Integration

1. Start Phoenix IAM
2. Start the demo app
3. Open browser to `http://localhost:3000`
4. Click "Login with Phoenix IAM"
5. Enter valid IAM credentials
6. Verify redirect to dashboard with user information
7. Test logout functionality

## Troubleshooting

### Common Issues

1. **"Invalid client_id" error**
   - Ensure the tenant is registered in Phoenix IAM
   - Check that CLIENT_ID matches the registered tenant name

2. **"redirect_uri is not pre-registered" error**
   - Ensure the redirect URI in the tenant configuration matches REDIRECT_URI

3. **"code_challenge_method must be 'S256'" error**
   - This should not occur if using this demo app correctly

4. **Token exchange fails**
   - Verify the IAM server is accessible
   - Check network connectivity
   - Verify the authorization code hasn't expired (2 minutes)

## File Structure

```
demo-app/
├── src/
│   ├── index.js      # Main application file
│   ├── config.js     # Configuration management
│   └── oauth.js      # OAuth utilities (PKCE, token exchange)
├── public/
│   ├── index.html    # Login page
│   ├── dashboard.html # Dashboard page
│   └── style.css     # Styling
├── package.json      # Dependencies
└── .env.example      # Example configuration
```

## Notes

- This is a demonstration application and should not be used in production without proper security review
- The session secret should be changed in production
- In production, use HTTPS for all communications
- Token validation should be enhanced to verify JWT signatures using the IAM's public key from `/jwk` endpoint
