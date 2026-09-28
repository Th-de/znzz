# Contributing

Thanks for your interest. The project is small, so the process is light.

## Ground rules

- Open an issue before a large change so we can agree on the approach.
- Business rules (fees, deadlines, state transitions) live in `backend/.../domain` and
  `application.yml`. Do not hard-code amounts or hours elsewhere.
- Never commit secrets. Local overrides go in `application-local.yml` (git-ignored); see
  `application-local.yml.example`.
- Schema changes: add a new `db/alter_vN_*.sql` **and** the matching idempotent step in
  `SchemaPatcher`, so existing databases upgrade automatically.

## Development

```bash
docker compose up -d
cd backend   && mvn -DskipTests spring-boot:run
cd client-web && npm install && npm run dev
cd admin-web  && npm install && npm run dev
```

Reset demo data with `db/reset_demo_data.sql`.

## Pull requests

1. Branch from `master`: `feat/<topic>` or `fix/<topic>`.
2. Keep one concern per PR; describe the state transitions or screens affected.
3. CI must pass: `mvn package` (compiles and runs unit tests) and `vite build` for both web apps.
   New business rules in `domain/` should come with a unit test next to the existing ones in
   `backend/src/test`.
4. Chinese or English is fine for issues, commits and comments.

## Reporting security issues

Please do not open a public issue for vulnerabilities. Email the maintainer listed on the
GitHub profile instead.
