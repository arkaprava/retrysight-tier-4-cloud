/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import '../styles/ricoui-preview.css'

const TOOLS = ['Cursor', 'Claude Code', 'Codex', 'Kiro', 'Warp', 'Copilot']

const PROBLEMS = [
  {
    icon: '💸',
    title: 'Token spend is invisible until the invoice',
    desc: 'Engineering teams run $200–600/developer/month on AI coding tools. Retry loops and compaction burn tokens without showing up in vendor dashboards.',
  },
  {
    icon: '↻',
    title: 'Retries hide in local logs',
    desc: 'Edits, test failures, rejected diffs, and subagent loops never reach your observability stack. Platform teams fly blind across the fleet.',
  },
  {
    icon: '🧩',
    title: 'Multi-tool fleets fragment visibility',
    desc: 'Cursor Admin API covers one vendor. DIY scripts cover another. No single pane for retries, cost, and collector health.',
  },
]

const FEATURES = [
  {
    icon: '◈',
    title: 'Multi-vendor collectors',
    desc: 'Lightweight agents tail local logs from Cursor, Claude Code, Codex, Kiro, Warp, Copilot, and more.',
  },
  {
    icon: '↻',
    title: 'Retry event taxonomy',
    desc: 'Track edits, test failures, diff decisions, compaction, subagents, and session boundaries per task.',
  },
  {
    icon: '$',
    title: 'Token & cost analytics',
    desc: 'Spend by model, user, and tool. Executive reports and budget views for FinOps-minded EMs.',
  },
  {
    icon: '◎',
    title: 'Live fleet feed',
    desc: 'GraphQL subscriptions for task updates and agent heartbeats — see collectors go stale before data gaps.',
  },
  {
    icon: '⎘',
    title: 'Session replay',
    desc: 'Export JSON, Markdown, or prompt-ready replay documents for debugging and eval workflows.',
  },
  {
    icon: '⚿',
    title: 'Self-hosted & SSO',
    desc: 'Docker deploy, BYO RDBMS, OIDC/SAML, and API key rotation for enterprise platform teams.',
  },
]

const PLANS = [
  {
    name: 'Core',
    price: 'Self-host',
    desc: 'Ingest, dashboard, tasks, agents, live feed',
    featured: false,
  },
  {
    name: 'Analytics',
    price: 'Contact',
    desc: 'Cost views, reports, git context, archive',
    featured: true,
  },
  {
    name: 'Enterprise',
    price: 'Contact',
    desc: 'MCP, SSO, ClickHouse, multi-tenant',
    featured: false,
  },
]

const FAQ = [
  {
    q: 'How is RetrySight different from LangSmith or Braintrust?',
    a: 'Those tools trace production LLM apps. RetrySight ingests local IDE agent logs — retries, tokens, and session events from coding tools on developer machines.',
  },
  {
    q: 'Do developers need to change how they work?',
    a: 'No SDK in your app code. A collector agent tails existing log files and pushes batches to your manager. Optional MCP tools query metrics from the IDE.',
  },
  {
    q: 'Can we self-host?',
    a: 'Yes. Docker Compose is the default path. PostgreSQL is the system of record; BYO RDBMS (Postgres, MySQL, SQL Server, Oracle) is supported for enterprise tiers.',
  },
  {
    q: 'Is this page the final marketing site?',
    a: 'This is a RicoUI theme preview inside the manager dashboard so you can compare aesthetics before committing to retrysight.com.',
  },
]

function BrowserMock() {
  const bars = [32, 48, 40, 64, 56, 80, 72]
  return (
    <div className="rf-browser">
      <div className="rf-browser-chrome">
        <div className="rf-browser-dots">
          <span />
          <span />
          <span />
        </div>
        <div className="rf-browser-url">app.retrysight.com/dashboard</div>
      </div>
      <div className="rf-mock-body">
        <div className="rf-mock-toolbar">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span className="rf-live-dot" aria-hidden="true" />
            <span style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Dashboard</span>
          </div>
          <span style={{ fontSize: '0.6875rem', color: 'var(--rf-text-muted)' }}>Last 7 days</span>
        </div>
        <div className="rf-stat-grid">
          <div className="rf-stat-card">
            <div className="rf-stat-label">Retry rate</div>
            <div className="rf-stat-value">18.4%</div>
            <div className="rf-stat-delta">−2.1% vs prior week</div>
          </div>
          <div className="rf-stat-card">
            <div className="rf-stat-label">Token spend</div>
            <div className="rf-stat-value">$4.2k</div>
            <div className="rf-stat-delta">+6.3% WoW</div>
          </div>
          <div className="rf-stat-card">
            <div className="rf-stat-label">Active collectors</div>
            <div className="rf-stat-value">24</div>
            <div className="rf-stat-delta">2 stale</div>
          </div>
          <div className="rf-stat-card">
            <div className="rf-stat-label">Top retry task</div>
            <div className="rf-stat-value" style={{ fontSize: '1rem' }}>auth-refactor</div>
            <div className="rf-stat-delta">14 events</div>
          </div>
        </div>
        <div className="rf-chart-bars" aria-hidden="true">
          {bars.map((h, i) => (
            <div
              key={i}
              className="rf-chart-bar"
              style={{ height: `${h}%`, animationDelay: `${i * 90}ms` }}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

export default function MarketingPreviewPage() {
  const [dark, setDark] = useState(true)

  useEffect(() => {
    document.documentElement.classList.add('ricoui-preview-active')
    return () => document.documentElement.classList.remove('ricoui-preview-active')
  }, [])

  return (
    <div className={`ricoui-marketing${dark ? ' dark' : ''}`}>
      <div className="rf-preview-banner">
        <span>RicoUI theme preview for retrysight.com</span>
        <Link to="/marketing-preview/powerai">PowerAI</Link>
        <span>·</span>
        <Link to="/marketing-preview/lambda">Lambda</Link>
        <span>·</span>
        <span style={{ fontWeight: 600, color: 'var(--rf-text-heading)' }}>RicoUI</span>
        <span>·</span>
        <Link to="/login">Login</Link>
        <Link to="/">Dashboard →</Link>
      </div>

      <header className="rf-header">
        <div className="rf-header-inner">
          <a className="rf-brand" href="#top">
            <img src="/retrysight-logo.svg" alt="" width={32} height={32} />
            <span>RetrySight</span>
          </a>
          <nav className="rf-nav" aria-label="Marketing">
            <a href="#features">Features</a>
            <a href="#tools">Tools</a>
            <a href="#pricing">Pricing</a>
            <a href="#faq">FAQ</a>
          </nav>
          <div className="rf-header-actions">
            <button
              type="button"
              className="rf-theme-toggle"
              onClick={() => setDark((d) => !d)}
              aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
            >
              {dark ? '☀' : '☾'}
            </button>
            <Link className="rf-btn rf-btn-ghost" to="/login">
              Sign in
            </Link>
            <a className="rf-btn rf-btn-primary" href="#cta">
              Self-host in 10 min
            </a>
          </div>
        </div>
      </header>

      <main id="top">
        <section className="rf-hero rf-container">
          <div className="rf-hero-grid">
            <div>
              <div className="rf-badges">
                <span className="rf-badge rf-badge-primary">Self-hosted</span>
                <span className="rf-badge rf-badge-accent">Coding Agent FinOps</span>
              </div>
              <h1>See every retry. Know every dollar.</h1>
              <p className="rf-hero-lead">
                Fleet observability for AI coding agents — retries, token waste, and session
                timelines across Cursor, Claude Code, Codex, and more. Built for platform teams
                who need visibility before the invoice arrives.
              </p>
              <div className="rf-hero-cta">
                <a className="rf-btn rf-btn-primary" href="#cta">
                  Start self-hosted
                </a>
                <a className="rf-btn rf-btn-ghost" href="#features">
                  View features →
                </a>
              </div>
            </div>
            <BrowserMock />
          </div>
        </section>

        <section className="rf-section rf-container" id="tools">
          <p style={{ textAlign: 'center', fontSize: '0.8125rem', color: 'var(--rf-text-muted)', marginBottom: '1rem' }}>
            Collectors for the tools your fleet already uses
          </p>
          <div className="rf-logo-strip">
            {TOOLS.map((t) => (
              <span key={t} className="rf-logo-pill">
                {t}
              </span>
            ))}
          </div>
        </section>

        <hr className="rf-divider rf-container" />

        <section className="rf-section rf-container">
          <div className="rf-section-header">
            <h2>Where coding agent waste stays hidden</h2>
            <p>
              FinOps pressure is real. RetrySight surfaces the signals vendor dashboards miss —
              across every developer machine in your fleet.
            </p>
          </div>
          <div className="rf-card-grid cols-3">
            {PROBLEMS.map((p) => (
              <article key={p.title} className="rf-card">
                <div className="rf-card-icon" aria-hidden="true">
                  {p.icon}
                </div>
                <h3>{p.title}</h3>
                <p>{p.desc}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="rf-section rf-container" id="features">
          <div className="rf-section-header">
            <h2>Built for platform teams, not generic SaaS</h2>
            <p>RetrySight ships what your functional spec already implements — retries, cost, live feed, and enterprise controls.</p>
          </div>
          <div className="rf-card-grid cols-3">
            {FEATURES.map((f) => (
              <article key={f.title} className="rf-card">
                <div className="rf-card-icon" aria-hidden="true">
                  {f.icon}
                </div>
                <h3>{f.title}</h3>
                <p>{f.desc}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="rf-section rf-container">
          <div className="rf-section-header">
            <h2>Your product, in the hero</h2>
            <p>
              RicoUI uses a browser-frame dashboard mock — swap this block for a real screenshot of
              the RetrySight console you already run locally.
            </p>
          </div>
          <div style={{ maxWidth: '56rem', margin: '0 auto' }}>
            <BrowserMock />
          </div>
        </section>

        <hr className="rf-divider rf-container" />

        <section className="rf-section rf-container" id="pricing">
          <div className="rf-section-header">
            <h2>Installation tiers</h2>
            <p>Placeholder pricing layout from RicoUI — align tiers with your tier-core → tier-cloud model.</p>
          </div>
          <div className="rf-pricing-grid">
            {PLANS.map((plan) => (
              <article
                key={plan.name}
                className={`rf-price-card${plan.featured ? ' featured' : ''}`}
              >
                <h3>{plan.name}</h3>
                <div className="rf-price">{plan.price}</div>
                <ul>
                  <li>{plan.desc}</li>
                  <li>Docker deploy</li>
                  <li>GraphQL API</li>
                </ul>
                <a className={`rf-btn${plan.featured ? ' rf-btn-primary' : ' rf-btn-ghost'}`} href="#cta">
                  {plan.featured ? 'Book demo' : 'Learn more'}
                </a>
              </article>
            ))}
          </div>
        </section>

        <section className="rf-section rf-container" id="faq">
          <div className="rf-section-header">
            <h2>Frequently asked questions</h2>
          </div>
          <div className="rf-faq">
            {FAQ.map((item) => (
              <details key={item.q} className="rf-faq-item">
                <summary>{item.q}</summary>
                <p>{item.a}</p>
              </details>
            ))}
          </div>
        </section>

        <section className="rf-section rf-container" id="cta">
          <div className="rf-cta-box">
            <h2 style={{ marginBottom: '0.75rem' }}>Evaluate RicoUI for retrysight.com</h2>
            <p style={{ color: 'var(--rf-text-muted)', marginBottom: '1.5rem' }}>
              This preview uses RicoUI design tokens with RetrySight copy. Compare side-by-side with
              your current AgentOps-style dashboard, then decide if RicoUI is the free Lambda alternative.
            </p>
            <div className="rf-hero-cta" style={{ justifyContent: 'center' }}>
              <Link className="rf-btn rf-btn-primary" to="/">
                Open live dashboard
              </Link>
              <a
                className="rf-btn rf-btn-ghost"
                href="https://ricofast.pages.dev/"
                target="_blank"
                rel="noreferrer"
              >
                RicoUI demo ↗
              </a>
            </div>
          </div>
        </section>
      </main>

      <footer className="rf-footer rf-container">
        <p>© {new Date().getFullYear()} RetrySight · RicoUI preview (MIT) · Apache-2.0</p>
      </footer>
    </div>
  )
}
