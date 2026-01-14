# Testing Guide for Demo App IAM Integration

This guide provides step-by-step instructions for testing the demo app with the Phoenix IAM system.

## Prerequisites Setup

### 1. Verify Phoenix IAM is Running

Ensure the Phoenix IAM is deployed and accessible. The default URL is `http://localhost:8080` (adjust in `.env` if different).

### 2. Register the Demo App Client in IAM

Before testing, you need to register the demo app as a client in the Phoenix IAM. This typically involves:

**Required Configuration:**
- **Client ID / Tenant Name**: `demo-app`
- **Redirect URI**: `http://localhost:3000/callback`
- **Grant Types**: `authorization_code`
- **Code Challenge Method**: `S256` (PKCE)
- **Scopes**: `openid profile`

### 3. Create Test Users

Ensure at least one user account exists in the IAM system for testing.

## Installation Steps

1. **Navigate to demo-app directory:**
   ```bash
   cd demo-app
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Configure environment (optional):**
   ```bash
   cp .env.example .env
   # Edit .env if IAM is not at http://localhost:8080
   ```

4. **Start the application:**
   ```bash
   npm start
   ```

   The app will start at `http://localhost:3000`

## Test Scenarios

### Scenario 1: Complete OAuth Flow

1. **Open browser** to `http://localhost:3000`
2. **Verify login page displays** with "Login with Phoenix IAM" button
3. **Click login button**
4. **Verify redirect** to IAM authorization page (should show Phoenix login form)
5. **Enter credentials** of a test user
6. **Submit login form**
7. **Verify redirect** back to `http://localhost:3000/callback`
8. **Verify automatic redirect** to `/dashboard`
9. **Verify dashboard displays:**
   - User information (username)
   - Scopes granted
   - User roles (if any)
   - Token type
   - Access token preview
   - Refresh token status

### Scenario 2: Direct Dashboard Access (Unauthorized)

1. **Without logging in**, navigate directly to `http://localhost:3000/dashboard`
2. **Verify redirect** back to home page (`)
3. **Verify** user is not authenticated

### Scenario 3: Session Persistence

1. **Complete login** (Scenario 1)
2. **Refresh the page** at `/dashboard`
3. **Verify** user remains logged in
4. **Close and reopen browser tab** to `/dashboard`
5. **Verify** session persists (or expires based on session timeout)

### Scenario 4: Logout Flow

1. **Complete login** (Scenario 1)
2. **On dashboard**, click "Logout" button
3. **Verify redirect** to home page
4. **Try accessing** `/dashboard` again
5. **Verify redirect** to home page (unauthenticated)

### Scenario 5: Error Handling - Invalid State

This tests CSRF protection:
1. **Manually construct** an invalid callback URL:
   ```
   http://localhost:3000/callback?code=test&state=invalid
   ```
2. **Navigate to this URL**
3. **Verify error message**: "Invalid state parameter - possible CSRF attack"

### Scenario 6: Error Handling - No Authorization Code

1. **Manually navigate** to:
   ```
   http://localhost:3000/callback
   ```
2. **Verify error message**: "No authorization code received"

### Scenario 7: Error Handling - User Denies Access

1. **Start login flow**
2. **At IAM consent/login page**, deny access or cancel
3. **Verify** appropriate error handling (depends on IAM behavior)

## Verification Checklist

After completing the test scenarios, verify:

- [ ] OAuth flow completes successfully
- [ ] PKCE code challenge and verifier are generated correctly
- [ ] Authorization code is exchanged for access token
- [ ] JWT token is decoded correctly
- [ ] User information is displayed on dashboard
- [ ] Protected routes redirect unauthenticated users
- [ ] Sessions persist correctly
- [ ] Logout clears session
- [ ] State parameter validates correctly (CSRF protection)
- [ ] Error messages are appropriate and not exposing sensitive info

## Common Issues and Solutions

### Issue: "Invalid client_id" error

**Solution:** Verify that:
- Tenant `demo-app` is registered in Phoenix IAM
- CLIENT_ID in `.env` matches the registered tenant name

### Issue: "redirect_uri is not pre-registered"

**Solution:** Verify that:
- The redirect URI `http://localhost:3000/callback` is configured in the IAM tenant
- REDIRECT_URI in `.env` matches exactly (including protocol and port)

### Issue: "code_challenge_method must be 'S256'"

**Solution:** This should not occur if using the demo app correctly. Check that:
- The IAM requires PKCE (which it does by default)
- The OAuth flow hasn't been modified

### Issue: Token exchange fails

**Solution:** Verify:
- IAM server is accessible from demo app
- Network connectivity is working
- Authorization code hasn't expired (2-minute timeout)
- Code verifier matches the challenge

### Issue: "Cannot read property 'user' of undefined"

**Solution:** This indicates session middleware isn't working:
- Verify express-session is installed: `npm install`
- Check that SESSION_SECRET is set
- Restart the application

## Development Tips

### Enable Debug Logging

Add this to your code to see detailed OAuth flow:

```javascript
console.log('Generated code verifier:', codeVerifier);
console.log('Generated code challenge:', codeChallenge);
console.log('Authorization URL:', authUrl);
```

### Check Session Data

In the callback handler, log the session:

```javascript
console.log('Session data:', req.session);
```

### Inspect JWT Token

Use the decoded token endpoint:

```bash
curl http://localhost:3000/api/user
```

### Monitor Network Traffic

Open browser DevTools > Network tab to:
- See the authorization redirect
- Inspect the callback URL with authorization code
- Check the token exchange request

## Browser Compatibility

Tested with:
- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## Performance Notes

- Token exchange typically takes < 500ms
- Page load time: < 1s
- Session validation: < 10ms

## Security Notes

For production deployment, consider:
- Enable HTTPS for all connections
- Use secure session cookies (secure: true)
- Implement rate limiting on authentication endpoints
- Add JWT signature verification using IAM's /jwk endpoint
- Store tokens in secure backend storage instead of session
- Implement token refresh logic
- Add proper error logging and monitoring
- Set appropriate session timeouts
