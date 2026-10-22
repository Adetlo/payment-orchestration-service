## Summary

<!-- What does this change and why? Link the issue if there is one. -->

## Type of change

- [ ] Bug fix
- [ ] New feature or behaviour change (update `docs/DESIGN.md` if the contract changes)
- [ ] Refactor / internal only
- [ ] Documentation
- [ ] Build, CI or tooling

## Checklist

- [ ] `mvn -B -ntp verify` passes in `backend/` (Spotless, SpotBugs, unit and integration tests)
- [ ] `npm run lint && npm run typecheck && npm test -- --run` pass in `frontend/`
- [ ] `shellcheck scripts/*.sh` passes if scripts changed
- [ ] `docker compose config -q` passes if compose or docker files changed
- [ ] Documentation and ADRs updated where behaviour changed
- [ ] No real account numbers, credentials or confidential material added (synthetic data only)

## How was this tested?

<!-- Commands run, scenarios exercised, screenshots if the UI changed. -->
