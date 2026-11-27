# Contributing to RetrySight Cloud

Thanks for your interest! This repository is licensed under [Apache-2.0](LICENSE).

## Development setup

Requirements: Java 21, Maven 3.9+, Node.js 20+, Docker with Compose v2.

```bash
cp .env.example .env
./scripts/build.sh            # Maven + UI + Docker images
./scripts/up-with-agent.sh    # postgres, manager, dashboard, collector
```

Dashboard: http://localhost:8086 · Manager API: http://localhost:28477

## Tests

```bash
mvn -B verify                                  # Java modules
(cd ui/manager-dashboard && npm ci && npm run build)
helm lint helm/retrysight
./scripts/validation/smoke.sh                  # against a running stack
```

## Guidelines

- Keep pull requests focused: one logical change each, with a description of what and why.
- Say how you tested (commands run, platforms checked).
- Update docs when behaviour, environment variables or install steps change.
- New source files carry the SPDX header — `python3 scripts/licensing/add-copyright-headers.py`
  adds it and `python3 scripts/licensing/check-copyright-headers.py` checks it.
- Never commit secrets, `.env` files, keys or local databases.

## Tiers

The four RetrySight tiers live in separate repositories that share most of their code. If a fix
applies to every tier, mention it in your PR so it can be ported.

## License

By contributing you agree that your contributions are licensed under the Apache License, Version 2.0.
