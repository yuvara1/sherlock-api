# Sherlock authentication service

Spring Boot 3.4 / Java 21 authentication microservice for Sherlock, structured with
Clean (hexagonal) Architecture.

## Features

- Registration and BCrypt password hashing (cost 12)
- Sign-in with a durable five-attempt / five-minute throttle
- Short-lived JWT access tokens plus rotating, hashed, single-use refresh tokens
- TOTP MFA setup, verification, sign-in challenge, and protected removal (secrets encrypted with AES-256-GCM)
- Generic password-reset requests with single-use, hashed reset tokens
- Session listing and revocation, plus a per-user audit history
- Flyway migrations that run on both PostgreSQL and H2 (PostgreSQL mode), health probes, tests, and a non-root container

## Architecture

```
            ┌──────────────────────────── presentation.rest ────────────────────────────┐
 HTTP ───▶  │ AuthController · UserController · AuthDtos (request/response) · ApiExceptionHandler │
            └───────────────────────────────┬──────────────────────────────────────────┘
                                            │ depends on (inbound ports only)
            ┌───────────────────────────────▼─────────── application ───────────────────┐
            │ port.in   RegisterUseCase, LoginUseCase, TokenRefreshUseCase, SessionUseCase, │
            │           PasswordResetUseCase, MfaUseCase, UserQueryUseCase, AuditQueryUseCase│
            │ service   RegistrationService, LoginService, LoginThrottleService,             │
            │           TokenRefreshService, SessionService, PasswordResetService,           │
            │           MfaService, AccountQueryService (+ SessionIssuer, AuditRecorder)    │
            │ dto       commands / results / RequestContext                                  │
            │ port.out  *RepositoryPort, TokenProviderPort, PasswordHasherPort, TotpPort,    │
            │           EncryptionPort, SecureTokenPort, PasswordResetNotifierPort,          │
            │           AuthSettingsPort                                                     │
            └───────────────┬───────────────────────────────────────▲──────────────────────┘
                            │ uses                                  │ implements (adapters)
            ┌───────────────▼──────── domain ───────┐   ┌───────────┴──────── infrastructure ─────────┐
            │ model      UserAccount, Organization,  │   │ persistence  Spring Data repos + adapters  │
            │            AuthSession, AuditLog, …    │   │ security     JWT, filter, SecurityConfig,  │
            │ exception  AuthException               │   │              BCrypt                        │
            │ service    PasswordPolicy              │   │ crypto       AES-GCM, SHA-256 tokens, TOTP │
            └────────────────────────────────────────┘   │ mail         SMTP / logging notifier       │
                                                         │ config       AuthProperties, bean wiring   │
                                                         │ seed         DemoDataSeeder                │
                                                         └────────────────────────────────────────────┘
```

Dependencies point inward. The domain has no Spring dependencies; JPA annotations stay on
entities as a pragmatic choice. The application layer depends only on the domain and on its
own ports. Infrastructure implements the outbound ports.

### Package layout (`in.sherlock.auth`)

```
SherlockAuthApplication
domain/
  model/            UserAccount, Organization, AuthSession, AuditLog, AuditEvent, Role,
                    PasswordResetToken, LoginThrottle
  exception/        AuthException
  service/          PasswordPolicy
application/
  port/in/          *UseCase interfaces
  port/out/         persistence, token, hashing, TOTP, encryption, notifier, settings ports
  service/          use-case implementations
  dto/              commands, results, RequestContext, PageResult
infrastructure/
  persistence/      *JpaRepository + *PersistenceAdapter
  security/         SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter,
                    AuthPrincipal, BCryptPasswordHasher
  crypto/           AesGcmEncryptionAdapter, SecureTokenAdapter, TotpAdapter
  mail/             SmtpPasswordResetNotifier, LoggingPasswordResetNotifier
  config/           AuthProperties, ApplicationConfig
  seed/             DemoDataSeeder
presentation/
  rest/             AuthController, UserController, AuthDtos, ApiExceptionHandler
```

## Running locally

### 1. Dev profile (no external dependencies)

```sh
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile (`src/main/resources/application-dev.yml`) does the following:

- Uses in-memory H2 in PostgreSQL mode. Flyway applies the same migrations used in production.
- Uses fixed, publicly committed JWT and MFA keys. They are for development only and must never be used in production.
- Disables SMTP. Password-reset links are printed to the console.
- Seeds the demo accounts listed below (`sherlock.seed.enabled=true`).

The API listens on `http://localhost:8081/api/v1`. Point the frontend at it with
`VITE_API_BASE_URL=http://localhost:8081`.

### 2. Docker Compose with PostgreSQL

Copy `example.env` to `.env` and fill in the three required secrets
(`openssl rand -base64 64` for `JWT_SECRET_BASE64`, `openssl rand -base64 32` for
`MFA_ENCRYPTION_KEY_BASE64`, and any strong `DATABASE_PASSWORD`). Then run:

```sh
docker compose --env-file .env up --build
```

To get the demo accounts against Postgres, set `SEED_DEMO_DATA=true`. Never do this in production.

## Demo credentials

These accounts exist only when `sherlock.seed.enabled=true`, which is on in `dev` and off by default.
The seeder is idempotent: it leaves existing accounts unchanged. All three accounts belong to the organisation **Sherlock Demo**.

| Email                | Password            | Role      | MFA |
|----------------------|---------------------|-----------|-----|
| `admin@sherlock.dev` | `Sherlock@Owner123` | OWNER     | No  |
| `demo@sherlock.dev`  | `Sherlock@Guest123` | DEVELOPER | No  |
| `mfa@sherlock.dev`   | `Sherlock@Mfa1234`  | DEVELOPER | TOTP, secret `JBSWY3DPEHPK3PXP` |

For the MFA account, add the secret `JBSWY3DPEHPK3PXP` to any authenticator app, or run
`oathtool --totp -b JBSWY3DPEHPK3PXP` to get the 6-digit code for `POST /api/v1/auth/login/mfa`.

The password policy rejects passwords that contain the email local part, so
`Sherlock@Admin123` and `Sherlock@Demo123` are not allowed for those accounts.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8081` | HTTP port |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | — | JDBC connection (required outside `dev`) |
| `JWT_SECRET_BASE64` | — | ≥64 random bytes, Base64 (required outside `dev`) |
| `MFA_ENCRYPTION_KEY_BASE64` | — | exactly 32 random bytes, Base64 (required outside `dev`) |
| `ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:8443,https://sherlock-in.vercel.app` | Comma-separated CORS origins |
| `PASSWORD_RESET_URL` | `https://sherlock-in.vercel.app/reset-password` | Base URL put in reset links |
| `SEED_DEMO_DATA` | `false` | Create the demo accounts |
| `MAIL_ENABLED`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | mail off | SMTP delivery of reset emails |

## Endpoints

All endpoints are under `/api/v1`. Errors are returned as RFC 7807 problem JSON with a `code` field.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | – | Create an account and organisation. Returns 201 with `{user, tokens, organization}` |
| POST | `/auth/login` | – | Returns 200 `{user, tokens, mfaPending:false}`, or 202 `{mfaPending:true, mfaToken}` |
| POST | `/auth/login/mfa` | – | `{mfaToken, code}`. Completes the MFA sign-in |
| POST | `/auth/refresh` | – | `{refreshToken}`. Rotates the token and returns `{accessToken, refreshToken, expiresIn}` |
| POST | `/auth/logout` | Bearer | Revokes the current session (204) |
| POST | `/auth/forgot-password` | – | `{email}`. Always returns a generic message |
| POST | `/auth/reset-password` | – | `{token, newPassword}`. Revokes all sessions |
| GET | `/auth/me` | Bearer | Current user |
| GET | `/users/me` | Bearer | Current user (alias) |
| GET | `/auth/sessions` | Bearer | Active sessions (`current` flag) |
| DELETE | `/auth/sessions/{id}` | Bearer | Revoke one session (204) |
| GET | `/auth/audit-log?page=0&pageSize=25` | Bearer | Paged audit history |
| POST | `/auth/mfa/enable` | Bearer | Start TOTP setup and return `{otpAuthUrl, secret}` |
| POST | `/auth/mfa/verify` | Bearer | `{code}`. Activates MFA (204) |
| DELETE | `/auth/mfa` | Bearer | `{password, code}`. Disables MFA and revokes sessions (204) |
| GET | `/actuator/health` | – | Liveness and readiness |

## Verification

```sh
cd backend
mvn -B -ntp verify
```

The tests run against H2 in PostgreSQL mode. `DevProfileSeedTest` boots the `dev` profile
and signs in with each seeded account.

## Deployment requirements

Deploy `backend/Dockerfile` with PostgreSQL and supply every variable in `example.env`, using
unique, cryptographically random secrets per environment. Do not enable the `dev` profile or
`SEED_DEMO_DATA` in production. Terminate TLS at the load balancer and restrict direct
container access to the trusted proxy or network. Back up the database. Use `/actuator/health`
for readiness and liveness probes.
