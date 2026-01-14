# Demo App Architecture & Flow

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         User Browser                            │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  http://localhost:3000                                    │   │
│  │  - Login Page (index.html)                               │   │
│  │  - Dashboard (dashboard.html)                            │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────┬───────────────────────┬────────────────────┘
                     │                       │
                     │ 1. Click Login        │ 5. Access Dashboard
                     ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│              Demo App (Express.js Server)                       │
│              http://localhost:3000                              │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐    │
│  │  Routes:                                               │    │
│  │  • GET  /            → Login page                      │    │
│  │  • GET  /login       → Start OAuth flow                │    │
│  │  • GET  /callback    → Handle OAuth callback           │    │
│  │  • GET  /dashboard   → Protected page                  │    │
│  │  • GET  /api/user    → User info API                   │    │
│  │  • GET  /logout      → Logout                          │    │
│  └────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐    │
│  │  Components:                                           │    │
│  │  • config.js   → Configuration                         │    │
│  │  • oauth.js    → PKCE & token utilities                │    │
│  │  • Session     → Secure session storage                │    │
│  └────────────────────────────────────────────────────────┘    │
└────────────────────┬───────────────────────┬────────────────────┘
                     │                       │
                     │ 2. Redirect to IAM    │ 4. Exchange code
                     │ 3. Callback with code │    for tokens
                     ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│              Phoenix IAM Server                                 │
│              http://localhost:8080                              │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐    │
│  │  Endpoints:                                            │    │
│  │  • GET  /authorize     → Authorization page            │    │
│  │  • POST /login/...     → User authentication           │    │
│  │  • POST /oauth/token   → Token endpoint                │    │
│  │  • GET  /jwk           → Public keys                   │    │
│  └────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐    │
│  │  Features:                                             │    │
│  │  • User authentication with Argon2                     │    │
│  │  • JWT token generation                                │    │
│  │  • PKCE validation (S256)                              │    │
│  │  • Grant management                                    │    │
│  └────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## OAuth 2.0 PKCE Flow Sequence

```
User          Demo App              Phoenix IAM          
 │                │                      │
 │  Visit /      │                      │
 ├──────────────>│                      │
 │  Login Page   │                      │
 │<──────────────┤                      │
 │                │                      │
 │  Click Login  │                      │
 ├──────────────>│                      │
 │                │                      │
 │                │ Generate PKCE:       │
 │                │ • code_verifier      │
 │                │ • code_challenge     │
 │                │ • state (CSRF)       │
 │                │                      │
 │  Redirect to IAM /authorize          │
 │  ?client_id=demo-app                 │
 │  &redirect_uri=.../callback          │
 │  &response_type=code                 │
 │  &code_challenge=...                 │
 │  &code_challenge_method=S256         │
 │  &scope=openid+profile               │
 │  &state=...                          │
 │<───────────────┤                      │
 ├─────────────────────────────────────>│
 │                │   Login Form         │
 │<──────────────────────────────────────┤
 │                │                      │
 │  Enter credentials                   │
 ├─────────────────────────────────────>│
 │                │                      │
 │                │   Authenticate &     │
 │                │   Validate PKCE      │
 │                │   Generate auth code │
 │                │                      │
 │  Redirect to /callback               │
 │  ?code=xyz&state=...                 │
 │<──────────────────────────────────────┤
 ├──────────────>│                      │
 │                │                      │
 │                │ Validate state       │
 │                │                      │
 │                │ POST /oauth/token    │
 │                │ grant_type=authz..   │
 │                │ code=xyz             │
 │                │ code_verifier=...    │
 │                ├─────────────────────>│
 │                │                      │
 │                │   Verify PKCE        │
 │                │   Generate tokens    │
 │                │                      │
 │                │ Token Response:      │
 │                │ • access_token       │
 │                │ • refresh_token      │
 │                │ • token_type         │
 │                │ • expires_in         │
 │                │<─────────────────────┤
 │                │                      │
 │                │ Decode JWT           │
 │                │ Store in session     │
 │                │                      │
 │  Redirect to /dashboard              │
 │<───────────────┤                      │
 │                │                      │
 │  Dashboard with user info            │
 │<───────────────┤                      │
 │                │                      │
```

## Data Flow

### 1. PKCE Generation (Client Side)
```javascript
code_verifier = random(32 bytes) → base64url
code_challenge = SHA256(code_verifier) → base64url
```

### 2. Authorization Request
```
GET /authorize?
  response_type=code
  &client_id=demo-app
  &redirect_uri=http://localhost:3000/callback
  &scope=openid+profile
  &code_challenge=XXXXXX
  &code_challenge_method=S256
  &state=YYYY
```

### 3. Authorization Response
```
GET /callback?
  code=AUTH_CODE_XYZ
  &state=YYYY
```

### 4. Token Request
```
POST /oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=AUTH_CODE_XYZ
&code_verifier=ORIGINAL_VERIFIER
```

### 5. Token Response
```json
{
  "token_type": "Bearer",
  "access_token": "eyJhbGc...",
  "expires_in": 1020,
  "scope": "openid profile",
  "refresh_token": "eyJhbGc..."
}
```

### 6. JWT Token Structure
```
Header:
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-id"
}

Payload:
{
  "sub": "username",
  "tenant_id": "demo-app",
  "scope": "openid profile",
  "groups": ["role1", "role2"],
  "iss": "urn:phoenix.xyz:iam",
  "aud": ["urn:phoenix.xyz:api"],
  "exp": 1234567890,
  "iat": 1234567890
}

Signature: VERIFY_WITH_PUBLIC_KEY
```

## Session Storage

```javascript
req.session = {
  user: {
    username: "john.doe",
    scopes: "openid profile",
    roles: ["Administrator"],
    accessToken: "eyJhbGc...",
    refreshToken: "eyJhbGc...",
    tokenType: "Bearer"
  },
  cookie: {
    httpOnly: true,
    secure: false, // true in production
    maxAge: 3600000 // 1 hour
  }
}
```

## Security Layers

```
┌─────────────────────────────────────────┐
│  1. PKCE (Code Interception Protection) │
│     ✓ Code verifier/challenge           │
│     ✓ S256 hash method                  │
└─────────────────────────────────────────┘
              ▼
┌─────────────────────────────────────────┐
│  2. State Parameter (CSRF Protection)   │
│     ✓ Random state generation           │
│     ✓ State validation on callback      │
└─────────────────────────────────────────┘
              ▼
┌─────────────────────────────────────────┐
│  3. HTTP-Only Cookies                   │
│     ✓ No JavaScript access              │
│     ✓ Secure transmission               │
└─────────────────────────────────────────┘
              ▼
┌─────────────────────────────────────────┐
│  4. Session Encryption                  │
│     ✓ express-session with secret       │
│     ✓ Server-side storage               │
└─────────────────────────────────────────┘
              ▼
┌─────────────────────────────────────────┐
│  5. JWT Token Validation                │
│     ✓ Token expiration checking         │
│     ✓ Audience validation               │
└─────────────────────────────────────────┘
```

## Component Relationships

```
┌──────────────────────────────────────────────────────────┐
│                     Demo App (src/)                      │
│                                                          │
│  ┌─────────────┐         ┌──────────────┐              │
│  │  index.js   │────────>│  config.js   │              │
│  │  (Server)   │         │  (Settings)  │              │
│  └──────┬──────┘         └──────────────┘              │
│         │                                               │
│         │ uses                                          │
│         ▼                                               │
│  ┌──────────────┐        ┌──────────────┐              │
│  │   oauth.js   │        │express-session│             │
│  │  (PKCE Utils)│        │  (Sessions)  │              │
│  └──────────────┘        └──────────────┘              │
│         │                        │                      │
│         └────────────┬───────────┘                      │
│                      │                                  │
│                      ▼                                  │
│              ┌───────────────┐                          │
│              │  Public Files │                          │
│              │  - index.html │                          │
│              │  - dashboard  │                          │
│              │  - style.css  │                          │
│              └───────────────┘                          │
└──────────────────────────────────────────────────────────┘
```

## File Structure

```
demo-app/
├── src/
│   ├── index.js          # Express server & OAuth flow
│   ├── config.js         # Configuration management
│   └── oauth.js          # PKCE & token utilities
│
├── public/
│   ├── index.html        # Login landing page
│   ├── dashboard.html    # Protected dashboard
│   └── style.css         # Styling
│
├── docs/
│   ├── README.md         # Full documentation
│   ├── QUICKSTART.md     # Quick start guide
│   ├── TESTING.md        # Testing scenarios
│   └── ARCHITECTURE.md   # This file
│
├── .env.example          # Configuration template
├── package.json          # Dependencies
└── node_modules/         # Installed packages (gitignored)
```

## Environment Configuration

```
┌────────────────────────────────────────────┐
│  Environment Variables (.env)              │
├────────────────────────────────────────────┤
│  IAM_BASE_URL      → Phoenix IAM location  │
│  CLIENT_ID         → OAuth client ID       │
│  REDIRECT_URI      → Callback URL          │
│  SCOPE             → OAuth scopes          │
│  PORT              → App server port       │
│  HOST              → App server host       │
│  SESSION_SECRET    → Session encryption    │
│  NODE_ENV          → Environment mode      │
└────────────────────────────────────────────┘
```

## Technology Stack

```
┌─────────────────┐
│   Frontend      │
├─────────────────┤
│ • HTML5         │
│ • CSS3          │
│ • JavaScript    │
│ • Fetch API     │
└─────────────────┘

┌─────────────────┐
│   Backend       │
├─────────────────┤
│ • Node.js 18+   │
│ • Express 5     │
│ • express-session│
│ • crypto (PKCE) │
└─────────────────┘

┌─────────────────┐
│   IAM System    │
├─────────────────┤
│ • Jakarta EE 11 │
│ • MicroProfile  │
│ • JWT (nimbus)  │
│ • Argon2        │
└─────────────────┘
```

---

This architecture demonstrates a production-ready OAuth 2.0 implementation pattern that can be adapted for real-world applications with minimal modifications.
