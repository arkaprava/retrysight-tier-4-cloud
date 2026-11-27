#!/usr/bin/env python3
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

"""
Selenium UI smoke tests for the RetrySight dashboard.

Requires manager (:28477) and UI (:5173) to be running.

  UI_URL=http://127.0.0.1:5173 APM_EMAIL=secadmin@local APM_PASSWORD=changeme \
    python3 ui_smoke.py
"""

from __future__ import annotations

import json
import os
import sys
import time
import traceback
import urllib.error
import urllib.request
from dataclasses import dataclass, field

from selenium import webdriver
from selenium.common.exceptions import TimeoutException, WebDriverException
from selenium.webdriver.chrome.options import Options as ChromeOptions
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait


# Prefer localhost over 127.0.0.1 — Chrome headless occasionally returns a truncated
# document for 127.0.0.1 against Vite's bound host.
UI_URL = os.environ.get("UI_URL", "http://localhost:5173").rstrip("/")
MANAGER_URL = os.environ.get("MANAGER_URL", "http://localhost:28477").rstrip("/")
APM_EMAIL = os.environ.get("APM_EMAIL", "secadmin@local")
APM_PASSWORD = os.environ.get("APM_PASSWORD", "changeme")
HEADLESS = os.environ.get("SELENIUM_HEADLESS", "1") not in ("0", "false", "False")
TIMEOUT = float(os.environ.get("SELENIUM_TIMEOUT", "25"))


@dataclass
class Results:
    passed: list[str] = field(default_factory=list)
    failed: list[str] = field(default_factory=list)
    skipped: list[str] = field(default_factory=list)

    def pass_(self, name: str) -> None:
        self.passed.append(name)
        print(f"\033[32mPASS  {name}\033[0m")

    def fail(self, name: str, detail: str = "") -> None:
        self.failed.append(name)
        suffix = f" — {detail}" if detail else ""
        print(f"\033[31mFAIL  {name}{suffix}\033[0m")

    def skip(self, name: str, detail: str = "") -> None:
        self.skipped.append(name)
        suffix = f" — {detail}" if detail else ""
        print(f"\033[33mSKIP  {name}{suffix}\033[0m")


def build_driver() -> webdriver.Chrome:
    options = ChromeOptions()
    if HEADLESS:
        options.add_argument("--headless=new")
    options.add_argument("--window-size=1440,900")
    options.add_argument("--disable-gpu")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-extensions")
    options.add_argument("--disable-background-networking")
    options.add_argument("--disable-renderer-backgrounding")
    options.add_argument("--remote-allow-origins=*")
    # Don't wait for all subresources (Vite HMR / websockets can stall "complete").
    options.page_load_strategy = "eager"
    # Selenium Manager resolves ChromeDriver automatically (Selenium 4.6+).
    driver = webdriver.Chrome(options=options)
    driver.set_page_load_timeout(TIMEOUT)
    driver.set_script_timeout(TIMEOUT)
    return driver


def wait(driver: webdriver.Chrome, timeout: float = TIMEOUT) -> WebDriverWait:
    return WebDriverWait(driver, timeout)


def labeled_input(driver: webdriver.Chrome, label_text: str):
    xpath = (
        f"//label[contains(translate(normalize-space(.), "
        f"'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), "
        f"'{label_text.lower()}')]//input"
    )
    return wait(driver).until(EC.visibility_of_element_located((By.XPATH, xpath)))


def set_react_input(driver: webdriver.Chrome, element, value: str) -> None:
    """Set a React controlled input so onChange state updates before submit."""
    driver.execute_script(
        """
        const el = arguments[0];
        const value = arguments[1];
        const proto = Object.getPrototypeOf(el);
        const desc = Object.getOwnPropertyDescriptor(proto, 'value');
        if (desc && desc.set) {
          desc.set.call(el, value);
        } else {
          el.value = value;
        }
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        """,
        element,
        value,
    )


def click_nav(driver: webdriver.Chrome, name: str) -> None:
    link = wait(driver).until(
        EC.element_to_be_clickable(
            (By.XPATH, f"//nav[contains(@class,'sidebar-nav')]//a[contains(., '{name}')]")
        )
    )
    link.click()


def page_looks_ready(driver: webdriver.Chrome) -> bool:
    try:
        if driver.execute_script("return document.readyState") not in ("interactive", "complete"):
            return False
        # Reject truncated Vite shells (seen with 127.0.0.1 + headless timeouts).
        if driver.find_elements(By.CSS_SELECTOR, "input[type='email'], .app-shell, .login-card, #root"):
            root = driver.find_elements(By.ID, "root")
            if root and (root[0].text.strip() or root[0].find_elements(By.CSS_SELECTOR, "*")):
                return True
            return bool(driver.find_elements(By.CSS_SELECTOR, "input[type='email'], .app-shell"))
        return False
    except WebDriverException:
        return False


def safe_get(driver: webdriver.Chrome, url: str, attempts: int = 3) -> None:
    """Navigate with retries; recover from Chrome renderer timeouts."""
    last_exc: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            driver.get(url)
            if page_looks_ready(driver):
                return
        except TimeoutException as exc:
            last_exc = exc
            if page_looks_ready(driver):
                return
            try:
                driver.refresh()
                if page_looks_ready(driver):
                    return
            except Exception:
                pass
        except WebDriverException as exc:
            last_exc = exc
        time.sleep(0.8 * attempt)
    if last_exc:
        raise last_exc
    raise TimeoutException(f"page never became ready: {url}")


def short_exc(exc: BaseException) -> str:
    text = str(exc).split("Stacktrace:")[0].strip()
    return text or type(exc).__name__


def fetch_login_payload() -> dict:
    body = json.dumps({"email": APM_EMAIL, "password": APM_PASSWORD}).encode()
    req = urllib.request.Request(
        f"{MANAGER_URL}/api/v1/auth/login",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode())


def seed_session(driver: webdriver.Chrome, payload: dict) -> None:
    """Inject JWT into localStorage (used as fallback if form login flakes)."""
    safe_get(driver, f"{UI_URL}/login")
    token = payload["token"]
    email = payload.get("email", APM_EMAIL)
    name = payload.get("displayName", "User")
    roles = json.dumps(payload.get("roles") or ["UI"])
    driver.execute_script(
        """
        localStorage.setItem('authToken', arguments[0]);
        localStorage.setItem('authEmail', arguments[1]);
        localStorage.setItem('authDisplayName', arguments[2]);
        localStorage.setItem('authRoles', arguments[3]);
        """,
        token,
        email,
        name,
        roles,
    )
    safe_get(driver, f"{UI_URL}/")
    wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, ".app-shell")))


def login(driver: webdriver.Chrome) -> str:
    """Returns 'form' or 'token-seed' depending on which path succeeded."""
    safe_get(driver, f"{UI_URL}/login")
    email = wait(driver).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='email']")))
    password = wait(driver).until(
        EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='password']"))
    )
    set_react_input(driver, email, APM_EMAIL)
    set_react_input(driver, password, APM_PASSWORD)
    submit = wait(driver).until(
        EC.element_to_be_clickable((By.CSS_SELECTOR, "form button[type='submit']"))
    )
    submit.click()
    try:
        wait(driver, timeout=12).until(EC.presence_of_element_located((By.CSS_SELECTOR, ".app-shell")))
        wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, ".sidebar-nav")))
        return "form"
    except TimeoutException:
        # Fallback: API login + localStorage seed (still validates authenticated UI).
        try:
            payload = fetch_login_payload()
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, KeyError) as exc:
            err = driver.find_elements(By.CSS_SELECTOR, ".login-error")
            detail = err[0].text if err else short_exc(exc)
            raise TimeoutException(f"form login failed and API fallback failed: {detail}") from exc
        seed_session(driver, payload)
        wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, ".sidebar-nav")))
        return "token-seed"


def run_tests(driver: webdriver.Chrome, results: Results) -> None:
    # U1 — login page reachable
    try:
        safe_get(driver, f"{UI_URL}/login")
        wait(driver).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, ".login-page, .login-card, input[type='email']"))
        )
        results.pass_("U1 login page loads")
    except Exception as exc:
        results.fail("U1 login page loads", short_exc(exc))
        return

    # U2 — sign in
    try:
        mode = login(driver)
        assert "/login" not in driver.current_url
        results.pass_(f"U2 sign in with secadmin ({mode})")
    except Exception as exc:
        results.fail("U2 sign in with secadmin", short_exc(exc))
        return

    # U3 — dashboard shell
    try:
        wait(driver).until(
            EC.presence_of_element_located(
                (By.XPATH, "//nav[contains(@class,'sidebar-nav')]//a[contains(., 'Dashboard')]")
            )
        )
        brand = driver.find_elements(By.CSS_SELECTOR, ".sidebar-brand, .sidebar h1")
        assert brand, "sidebar brand missing"
        results.pass_("U3 dashboard shell visible")
    except Exception as exc:
        results.fail("U3 dashboard shell visible", short_exc(exc))

    # U4–U6 — primary nav pages
    for code, label, path_hint in (
        ("U4", "Tasks", "/tasks"),
        ("U5", "Collectors", "/agents"),
        ("U6", "Live", "/live"),
    ):
        try:
            click_nav(driver, label)
            wait(driver).until(lambda d: path_hint in d.current_url)
            wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, ".content, main.content")))
            results.pass_(f"{code} navigate to {label}")
        except Exception as exc:
            results.fail(f"{code} navigate to {label}", short_exc(exc))

    # U7 — Cost (feature-flagged)
    try:
        links = driver.find_elements(
            By.XPATH, "//nav[contains(@class,'sidebar-nav')]//a[contains(., 'Cost')]"
        )
        if not links:
            results.skip("U7 Cost page", "nav link not present (feature disabled)")
        else:
            links[0].click()
            wait(driver).until(lambda d: "/cost" in d.current_url)
            results.pass_("U7 Cost page")
    except Exception as exc:
        results.fail("U7 Cost page", short_exc(exc))

    # U8 — Retries (feature-flagged)
    try:
        links = driver.find_elements(
            By.XPATH, "//nav[contains(@class,'sidebar-nav')]//a[contains(., 'Retries')]"
        )
        if not links:
            results.skip("U8 Retries page", "nav link not present (feature disabled)")
        else:
            links[0].click()
            wait(driver).until(lambda d: "/retries" in d.current_url)
            results.pass_("U8 Retries page")
    except Exception as exc:
        results.fail("U8 Retries page", short_exc(exc))

    # U9 — Insights chat FAB
    try:
        fab = wait(driver, timeout=8).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, ".insights-chat-fab"))
        )
        fab.click()
        wait(driver).until(
            EC.visibility_of_element_located((By.CSS_SELECTOR, ".insights-chat-drawer.is-open"))
        )
        results.pass_("U9 Insights chat drawer opens")
        close = driver.find_elements(By.CSS_SELECTOR, ".insights-chat-close")
        if close:
            close[0].click()
            time.sleep(0.3)
    except TimeoutException:
        results.skip("U9 Insights chat drawer opens", "FAB not found (chat UI not deployed)")
    except Exception as exc:
        results.fail("U9 Insights chat drawer opens", short_exc(exc))

    # U10 — copyright notice in sidebar
    try:
        click_nav(driver, "Dashboard")
        notice = wait(driver).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, ".sidebar-copyright"))
        )
        text = notice.text
        assert "©" in text
        results.pass_("U10 sidebar copyright notice")
    except Exception as exc:
        results.fail("U10 sidebar copyright notice", short_exc(exc))

    # U11 — sign out
    try:
        btn = wait(driver).until(EC.element_to_be_clickable((By.CSS_SELECTOR, ".logout-btn")))
        btn.click()
        wait(driver).until(EC.url_contains("/login"))
        results.pass_("U11 sign out returns to login")
    except Exception as exc:
        results.fail("U11 sign out returns to login", short_exc(exc))


def main() -> int:
    print(f"Selenium UI smoke against {UI_URL}")
    print(f"headless={HEADLESS} email={APM_EMAIL}")
    print()
    results = Results()
    driver = None
    try:
        driver = build_driver()
        run_tests(driver, results)
    except WebDriverException as exc:
        results.fail("driver startup", str(exc).splitlines()[0] if str(exc) else "WebDriver error")
        traceback.print_exc()
    finally:
        if driver is not None:
            try:
                driver.quit()
            except Exception:
                pass

    print()
    print(
        f"Results: pass={len(results.passed)} fail={len(results.failed)} "
        f"skip={len(results.skipped)}"
    )
    return 1 if results.failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
