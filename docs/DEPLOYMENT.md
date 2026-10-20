# Deployment guide

**Free tiers change often and are never guaranteed. Re-check each provider's current limits before relying on them,
and always keep a local demo as a backup for your viva.**

## Options
| | Oracle Cloud Always Free VM | Render Free web service |
|---|---|---|
| Always running | Yes (a real VM), if you can obtain one | No - sleeps after idle time; first request is slow |
| Effort | High: VM, firewall, Docker/Java, HTTPS (nginx + Let's Encrypt), needs a domain for easy HTTPS | Low: connect GitHub repo, use the Dockerfile |
| Watch out for | Card needed at sign-up; free capacity may be unavailable in some regions; inactive accounts/instances can be reclaimed | Limited CPU/RAM (Spring Boot start-up can be slow); files written to disk vanish on restart |
| Database | Keep Supabase (separate from host) | Keep Supabase |

Supabase free projects can pause when inactive - open the dashboard and wake the project before a demo.

## Required environment variables (production)
| Variable | Meaning |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `prod` (the Dockerfile sets this) |
| `DB_URL` | `jdbc:postgresql://<host>:5432/postgres?sslmode=require` (use the Session pooler host if the direct host is IPv6-only) |
| `DB_USERNAME`, `DB_PASSWORD` | from Supabase |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | creates the first admin (password >= 8 chars). Remove `ADMIN_PASSWORD` afterwards if you like |
| `PORT` | set automatically by Render; defaults to 8080 |
| `UPLOAD_DIR` | optional; where images are stored |

Production profile: no demo accounts, secure cookies, trusts `X-Forwarded-*` headers from the platform's HTTPS proxy.
Database traffic uses TLS (`sslmode=require`).

## Render (simplest)
1. Push the project to GitHub (`.env` is ignored - verify with `git status` that it is not listed).
2. Render dashboard -> New -> Web Service -> select the repo -> Runtime: **Docker** -> Free instance.
3. Add the environment variables above. Deploy. First start runs Flyway migrations.
4. Open the `.onrender.com` URL (HTTPS is provided by Render).

## Build and run manually
```
mvn clean package            # creates target/civictrack-1.0.0.jar
java -jar target/civictrack-1.0.0.jar          # reads environment variables
docker build -t civictrack . && docker run -p 8080:8080 --env-file .env civictrack
```

## Oracle VM outline
Create an Always Free VM, open port 80/443 in both the VCN security list and the OS firewall, install Docker,
run the container with `--env-file`, put nginx (or Caddy) in front for HTTPS, and keep the container running with `--restart unless-stopped`.

## Migrations and backups
- Schema changes: add `V3__description.sql` in `db/migration`; never edit `V1`/`V2` after they ran.
- Backups: Supabase dashboard (check what your plan includes) or `pg_dump` using the connection string.

## Deployment checklist
- [ ] `.env` and secrets not committed; passwords rotated if ever exposed
- [ ] `mvn test` passes
- [ ] Production env vars set, profile `prod`
- [ ] App starts, Flyway migrations applied (check logs)
- [ ] Log in as admin; change/remove any demo credentials
- [ ] Register a citizen, run the whole complaint lifecycle once
- [ ] Supabase project awake before the demo

## Troubleshooting
| Symptom | Likely cause / fix |
|---------|--------------------|
| `Could not resolve placeholder 'DB_URL'` | `.env` missing or not in the project root / env var not set |
| `Connection refused` / `Network is unreachable` | IPv6-only direct host: use Session pooler string; check password and `sslmode=require` |
| `Schema-validation: missing table / wrong column type` | entity and migration disagree; fix the entity or add a new migration |
| `Flyway ... checksum mismatch` | an applied migration was edited; revert it and add a new one |
| `prepared statement already exists` | using the Transaction pooler (6543); switch to Session pooler / direct |
| 502 or timeout on Render right after deploy | free instance still starting or waking; wait and retry |
| Uploaded images disappear | ephemeral disk on free hosting (expected) |
