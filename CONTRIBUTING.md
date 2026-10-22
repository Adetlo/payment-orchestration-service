# Contributing

Thanks for your interest. This is an independent educational portfolio project built on
synthetic data; contributions that keep it small, honest and well-tested are welcome.

## Ground rules

* **Synthetic only.** Never add real account numbers, credentials, customer data, employer
  material or production configuration. Account references must match the synthetic pattern
  in `docs/DESIGN.md` (for example `GB12PAYLAB00000001`).
* **The design document is the contract.** `docs/DESIGN.md` defines the API, the state
  machine, the event envelope and the infrastructure. If a change alters behaviour described
  there, update the document in the same pull request and, for significant decisions, add an
  ADR under `docs/adr/`.
* **Keep the demo working.** `scripts/demo.sh` and the compose smoke test in CI must pass.

## Development setup

Prerequisites: Docker with Compose v2, Java 21, Maven 3.9+, Node 22, `jq`, optionally `k6`
and `shellcheck`.

```bash
# Full stack
docker compose up --build

# Backend only (uses the local profile against localhost infrastructure)
docker compose up -d postgres kafka redis
cd backend && mvn spring-boot:run

# Frontend dev server (proxies /api to localhost:8080)
cd frontend && npm ci && npm run dev
```

## Before opening a pull request

| Area      | Command                                                              |
|-----------|----------------------------------------------------------------------|
| Backend   | `cd backend && mvn -B -ntp verify` (Spotless, SpotBugs, tests, JaCoCo) |
| Frontend  | `cd frontend && npm run lint && npm run typecheck && npm run format:check && npm test -- --run && npm run build` |
| e2e       | `cd e2e && npx tsc --noEmit -p .`                                    |
| Scripts   | `shellcheck -x scripts/*.sh`                                         |
| Compose   | `docker compose config -q`                                           |
| Demo      | `scripts/demo.sh` (starts the stack) or `scripts/demo.sh --no-start` |

Formatting: the backend uses google-java-format (AOSP style) through Spotless
(`mvn spotless:apply`); the frontend uses Prettier (`npm run format`). Editor defaults are
in `.editorconfig`.

## Pull requests

* One topic per pull request; small is good.
* Use the pull request template and fill in how the change was tested.
* Conventional commit prefixes are appreciated (`feat:`, `fix:`, `docs:`, `ci:`, `refactor:`,
  `test:`, `chore:`) but not enforced.
* CI must be green: backend, frontend, Docker build, compose smoke test, Trivy, ShellCheck
  and CodeQL.

## Reporting issues

Use the issue templates. Security problems go through the process in `SECURITY.md`, not a
public issue.

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md).
