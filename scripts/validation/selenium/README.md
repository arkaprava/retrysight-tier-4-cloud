# Selenium UI smoke tests

Browser smoke coverage for the dashboard (login, nav, cost/retries when enabled, Insights drawer, sign-out).

## Prerequisites

- Manager on `MANAGER_URL` (default `http://localhost:28477`)
- UI on `UI_URL` (default `http://localhost:5173`)
- Google Chrome or Chromium installed (Selenium Manager fetches a matching driver)

## Run

```bash
# From repo root (creates a local venv on first run)
./scripts/validation/ui-selenium.sh

# Headed Chrome
SELENIUM_HEADLESS=0 ./scripts/validation/ui-selenium.sh

# Custom credentials / URL
UI_URL=http://127.0.0.1:5173 APM_EMAIL=secadmin@local APM_PASSWORD=changeme \
  ./scripts/validation/ui-selenium.sh
```

Or directly:

```bash
cd scripts/validation/selenium
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python3 ui_smoke.py
```

## Cases

| ID | Check |
|----|--------|
| U1 | Login page loads |
| U2 | Sign in |
| U3 | Dashboard shell |
| U4–U6 | Tasks / Collectors / Live |
| U7–U8 | Cost / Retries (skip if feature off) |
| U9 | Insights chat FAB |
| U10 | Sidebar copyright |
| U11 | Sign out |
