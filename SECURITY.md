# Security policy

## Scope

This repository is an **independent educational portfolio project** that processes only
synthetic data. It is not connected to any payment network, bank, or production system, and
it is not deployed as a service by the author. There is no bug bounty.

Still, the code is meant to demonstrate production-style practices, so security reports are
welcome and will be handled seriously.

## Supported versions

| Version                 | Supported |
|-------------------------|-----------|
| `main` branch           | Yes       |
| Latest tagged release   | Yes       |
| Older tags              | No        |

## Reporting a vulnerability

Please use **GitHub private vulnerability reporting**:

1. Open the repository's *Security* tab.
2. Choose *Report a vulnerability* and describe the issue, affected component
   (backend, frontend, compose/infra, CI) and reproduction steps.

Do not open a public issue for security problems. You should receive an acknowledgement
within 7 days. Fixes for confirmed issues are published as a normal commit or release with a
short note in the changelog or release description; credit is given if you wish.

## What counts

In scope: the Spring Boot API, the React UI and nginx configuration, the Docker images and
compose stack, the CI workflows, and the scripts in `scripts/`.

Out of scope (known, documented limitations, see `README.md` "Security model"):

* No authentication or authorisation on the API: this is a stated demo limitation.
* Default credentials for PostgreSQL and Grafana in `docker-compose.yml`: the stack is
  local-only and binds to localhost for development.
* The `X-Demo-Failure` header and the `demo` Spring profile: failure injection is intentional
  and is hard-disabled by the `prod` profile.
* Findings in third-party dependencies that are already tracked by Dependabot or Trivy in CI,
  unless you have a working exploit against this project.

## Automated checks

Every push and pull request runs CodeQL (Java and JavaScript/TypeScript), Trivy
(dependency vulnerabilities and misconfiguration), and Dependabot keeps Maven, npm, Docker
and GitHub Actions dependencies current.
