# Phoenix IAM - Application Security Project

An OAuth 2.0/OpenID Connect Identity and Access Management (IAM) service built with Jakarta EE and designed to run on WildFly.

## 🚀 Quick Start

This application has been fully configured for deployment on WildFly with the domains:
- **IAM Service**: `iam.anis-nsir.me`
- **Main Application**: `anis-nsir.me`

### Documentation

- **[SUMMARY.md](SUMMARY.md)** - Start here! Complete overview of the project and deployment
- **[QUICKSTART.md](QUICKSTART.md)** - Quick reference for configuration and troubleshooting
- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Detailed step-by-step deployment guide

### Prerequisites

- WildFly Application Server 27+
- MySQL Database 8.0+
- Java 17+
- Maven 3.8+
- SSL Certificate for your domains

### Build

```bash
cd src
mvn clean package
```

This produces `target/phoenix-iam.war` ready for deployment.

### Deploy

Follow the comprehensive guides:
1. Read [SUMMARY.md](SUMMARY.md) for an overview
2. Follow [DEPLOYMENT.md](DEPLOYMENT.md) for detailed instructions
3. Use [QUICKSTART.md](QUICKSTART.md) as a quick reference

### Features

- ✅ OAuth 2.0 Authorization Code Flow with PKCE
- ✅ OpenID Connect support
- ✅ JWT tokens with Ed25519 signatures
- ✅ Argon2 password hashing
- ✅ Multi-tenant support
- ✅ Consent management
- ✅ Secure session handling
- ✅ CORS support
- ✅ WebSocket push notifications

### Security

- HTTPS enforced (HSTS)
- Secure cookies (HttpOnly, Secure, SameSite=Strict)
- Argon2 password hashing with strong parameters
- JWT signature verification
- PKCE for authorization code flow
- X-Content-Type-Options headers

### Architecture

```
┌─────────────────┐         ┌──────────────────┐         ┌─────────────┐
│   Frontend      │         │   Phoenix IAM    │         │   Backend   │
│ anis-nsir.me    │────────▶│ iam.anis-nsir.me │────────▶│  API/ERP    │
│                 │ OAuth   │                  │  JWT    │             │
└─────────────────┘         └──────────────────┘         └─────────────┘
```

### Project Status

- ✅ Build: Successful (phoenix-iam.war 2.7MB)
- ✅ Security Scan: No vulnerabilities found
- ✅ Code Review: Passed
- ✅ Documentation: Complete
- ✅ Ready for deployment

### License

[Include your license here]

---

For detailed information, see [SUMMARY.md](SUMMARY.md)