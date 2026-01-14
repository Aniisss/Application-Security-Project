# Quick Start Guide - Phoenix IAM Demo App

## 🚀 Quick Start (3 minutes)

### Step 1: Install Dependencies
```bash
cd demo-app
npm install
```

### Step 2: Configure (Optional)
```bash
# Copy example environment file
cp .env.example .env

# Edit if your IAM is not at http://localhost:8080
# nano .env
```

### Step 3: Start the App
```bash
npm start
```

Visit: http://localhost:3000

## ✅ Before You Start

Make sure Phoenix IAM has:

1. **A registered tenant/client:**
   - Client ID: `demo-app`
   - Redirect URI: `http://localhost:3000/callback`
   - Grant type: `authorization_code`

2. **At least one user account** to test login

## 🎯 What You'll See

### Home Page (/)
- Clean landing page
- "Login with Phoenix IAM" button
- Feature list

### After Login (/dashboard)
- ✅ Your username
- 📋 Granted scopes
- 👥 Your roles
- 🔑 Access token info
- 🔐 Refresh token status

## 🔧 Troubleshooting

**Can't connect to IAM?**
- Check IAM is running: http://localhost:8080
- Update IAM_BASE_URL in .env

**Client ID error?**
- Register tenant `demo-app` in IAM
- Match CLIENT_ID in .env

**Redirect URI error?**
- Configure `http://localhost:3000/callback` in IAM tenant settings

## 📚 More Information

- Full README: [README.md](README.md)
- Testing Guide: [TESTING.md](TESTING.md)

## 🎨 Architecture

```
User → Demo App → Phoenix IAM
         ↓
    Express + OAuth 2.0 + PKCE
         ↓
    Session Storage → JWT Tokens
```

## 🔐 Security Features

✓ OAuth 2.0 Authorization Code Flow
✓ PKCE (Proof Key for Code Exchange)
✓ State parameter (CSRF protection)
✓ HTTP-only session cookies
✓ Secure session encryption

## 📝 Endpoints

- `GET /` - Home page
- `GET /login` - Initiate OAuth flow
- `GET /callback` - OAuth callback
- `GET /dashboard` - Protected dashboard (requires auth)
- `GET /api/user` - User info API (requires auth)
- `GET /logout` - Logout

## 💡 Development Mode

For auto-reload during development:
```bash
npm run dev
```

## 🌐 Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| IAM_BASE_URL | http://localhost:8080 | Phoenix IAM server URL |
| CLIENT_ID | demo-app | OAuth client ID |
| REDIRECT_URI | http://localhost:3000/callback | OAuth redirect URI |
| SCOPE | openid profile | OAuth scopes |
| PORT | 3000 | App server port |
| HOST | localhost | App server host |
| SESSION_SECRET | (change me) | Session encryption secret |

## ⚠️ Important Notes

- This is a **demo application** for educational purposes
- Change SESSION_SECRET in production
- Use HTTPS in production
- Consider token storage alternatives for production

## 🐛 Still Having Issues?

1. Check console logs in browser DevTools
2. Check terminal output for errors
3. Verify IAM is accessible: `curl http://localhost:8080/authorize`
4. Review [TESTING.md](TESTING.md) for detailed troubleshooting

---

**Ready to test?** → Just run `npm start` and open http://localhost:3000 🎉
