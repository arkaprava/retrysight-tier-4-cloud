/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import '../styles/lambda-preview.css'

const TOOLS = ['Cursor', 'Claude Code', 'Codex', 'Kiro', 'Warp', 'Copilot']

const STATS = [
  { num: '7', desc: 'Supported coding agent sources' },
  { num: '15m', desc: 'Target time to first agent heartbeat' },
  { num: 'Self-host', desc: 'Docker + BYO RDBMS for enterprise' },
]

const FEATURES = [
  {
    wide: false,
    title: 'Fleet-wide retry visibility',
    desc: 'Collect edits, test failures, rejected diffs, compaction, and subagent events from every developer machine.',
  },
  {
    wide: false,
    title: 'Token & cost analytics',
    desc: 'Spend by model, user, and tool. Export executive PDFs when your FinOps team asks the hard questions.',
  },
  {
    wide: true,
    title: 'Self-host in one afternoon',
    desc: 'Docker Compose up, agent collector on workstations, dashboard on :8080. No vendor lock-in on your telemetry.',
    code: true,
  },
  {
    wide: false,
    title: 'Live GraphQL subscriptions',
    desc: 'taskUpdated and agentHeartbeat streams — catch stale collectors before your dashboards go quiet.',
  },
  {
    wide: false,
    title: 'Session replay & MCP',
    desc: 'Export prompt-ready replay documents. Query retry metrics from Cursor via the MCP server.',
  },
]

const LOG_LINES = [
  { time: '20:14:21', kind: 'ok', text: 'EDIT cursor · auth-refactor · turn 12' },
  { time: '20:14:18', kind: 'retry', text: 'TEST_FAIL claude-code · api-client · retry 3' },
  { time: '20:14:15', kind: 'ok', text: 'TOKEN_USAGE codex · 24.2k in / 1.1k out' },
  { time: '20:14:11', kind: 'retry', text: 'DIFF_REJECTED cursor · layout.tsx' },
  { time: '20:14:08', kind: 'ok', text: 'HEARTBEAT agent · dev-macbook-07 · 42ms' },
  { time: '20:14:03', kind: 'ok', text: 'COMPLETE task · onboarding-wizard' },
]

const CODE_SAMPLES: Record<string, string> = {
  docker: `# infra/scripts/up.sh — RetrySight stack
./infra/scripts/up.sh --build

# Dashboard → http://localhost:8080
# Manager API → http://localhost:28477`,
  yaml: `# ~/.agent-retry/config.yaml
agent:
  manager-url: http://localhost:28477
  api-key: \${AGENT_API_KEY}
  developer-email: you@company.com`,
  mcp: `# .cursor/mcp.json (excerpt)
{
  "mcpServers": {
    "retrysight": {
      "command": "node",
      "args": ["mcp/dist/index.js"]
    }
  }
}`,
}

const PLANS = [
  {
    tag: 'Core',
    popular: false,
    price: '$0',
    period: 'self-host',
    blurb: 'Ingest, dashboard, tasks, agents, and live feed.',
    items: ['Docker deploy', 'PostgreSQL', 'GraphQL API', 'Community docs'],
  },
  {
    tag: 'Analytics',
    popular: true,
    price: 'Contact',
    period: 'per fleet',
    blurb: 'Cost views, reports, git context, and archive.',
    items: ['Cost & retry pages', 'Executive exports', 'Tier gating', 'Email support'],
  },
  {
    tag: 'Enterprise',
    popular: false,
    price: 'Custom',
    period: 'annual',
    blurb: 'SSO, MCP, ClickHouse, and multi-tenant controls.',
    items: ['OIDC / SAML', 'API key rotation', 'BYO RDBMS', 'Dedicated onboarding'],
  },
]

function ProductMock() {
  return (
    <div className="lm-product-frame">
      <div className="lm-frame-bar">
        <span>app.retrysight.com/production</span>
        <span>● live</span>
      </div>
      <div className="lm-frame-body">
        <div className="lm-dash-main">
          <div className="lm-dash-header">
            <h3>Overview</h3>
            <span>fleet / last 7d</span>
          </div>
          <div className="lm-metrics">
            <div className="lm-metric">
              <div className="lm-metric-label">Retry rate</div>
              <div className="lm-metric-value">18.4%</div>
              <div className="lm-metric-delta down">▼ 2.1%</div>
            </div>
            <div className="lm-metric">
              <div className="lm-metric-label">Token spend</div>
              <div className="lm-metric-value">$4.2k</div>
              <div className="lm-metric-delta warn">▲ 6.3%</div>
            </div>
            <div className="lm-metric">
              <div className="lm-metric-label">Collectors</div>
              <div className="lm-metric-value">24</div>
              <div className="lm-metric-delta up">▲ 2 new</div>
            </div>
            <div className="lm-metric">
              <div className="lm-metric-label">Stale agents</div>
              <div className="lm-metric-value">2</div>
              <div className="lm-metric-delta warn">▲ alert</div>
            </div>
          </div>
          <div className="lm-chart-placeholder" aria-hidden="true" />
        </div>
        <div className="lm-log-panel">
          <div className="lm-log-title">
            <span className="lm-log-live" aria-hidden="true" />
            Live retry events
          </div>
          {LOG_LINES.map((line) => (
            <div key={line.time + line.text} className="lm-log-line">
              {line.time}{' '}
              <span className={line.kind}>{line.kind === 'retry' ? 'RETRY' : 'OK'}</span>{' '}
              {line.text}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function CodeTabs() {
  const tabs = ['docker', 'yaml', 'mcp'] as const
  const [active, setActive] = useState<(typeof tabs)[number]>('docker')

  return (
    <div className="lm-code-tabs">
      <div className="lm-code-tab-bar" role="tablist">
        {tabs.map((tab) => (
          <button
            key={tab}
            type="button"
            role="tab"
            aria-selected={active === tab}
            className={`lm-code-tab${active === tab ? ' active' : ''}`}
            onClick={() => setActive(tab)}
          >
            {tab === 'docker' ? 'Shell' : tab === 'yaml' ? 'YAML' : 'MCP'}
          </button>
        ))}
      </div>
      <pre className="lm-code-block">
        <code>{CODE_SAMPLES[active]}</code>
      </pre>
    </div>
  )
}

export default function LambdaMarketingPreviewPage() {
  const [annual, setAnnual] = useState(false)

  useEffect(() => {
    document.documentElement.classList.add('lambda-preview-active')
    return () => document.documentElement.classList.remove('lambda-preview-active')
  }, [])

  return (
    <div className="lambda-marketing">
      <div className="lm-preview-banner">
        <span>Astro Lambda theme preview — RetrySight content</span>
        <Link to="/marketing-preview/powerai">PowerAI</Link>
        <span>·</span>
        <Link to="/marketing-preview/ricoui">RicoUI</Link>
        <span>·</span>
        <span className="lm-active">Lambda</span>
        <span>·</span>
        <Link to="/login">Login</Link>
        <Link to="/">Dashboard →</Link>
      </div>

      <header className="lm-header">
        <div className="lm-container lm-header-inner">
          <Link className="lm-brand" to="/marketing-preview/lambda">
            <img src="/retrysight-logo.svg" alt="" width={28} height={28} />
            RetrySight
          </Link>
          <nav className="lm-nav" aria-label="Marketing">
            <a href="#features">Features</a>
            <a href="#pricing">Pricing</a>
            <a href="#docs">Quickstart</a>
          </nav>
          <div className="lm-header-actions">
            <Link className="lm-btn" to="/login">
              Sign in
            </Link>
            <a className="lm-btn lm-btn-primary" href="#cta">
              Start self-hosted
            </a>
          </div>
        </div>
      </header>

      <main>
        <section className="lm-hero lm-container">
          <div className="lm-hero-badge">
            <span>●</span> v1.0 — Multi-tool collectors, now GA
          </div>
          <h1>The observability layer for teams who ship with AI coding agents.</h1>
          <p className="lm-hero-lead">
            RetrySight tracks retries, token waste, and session activity across Cursor, Claude Code,
            Codex, and your entire agent fleet. Self-hosted. No SDK in your app. Sleep through the
            invoice surprise.
          </p>
          <div className="lm-hero-cta">
            <a className="lm-btn lm-btn-primary" href="#cta">
              Start self-hosted
            </a>
            <Link className="lm-btn" to="/">
              Open live dashboard
            </Link>
          </div>
          <p className="lm-trust">
            Self-hosted Docker
            <span>·</span>
            SSO on enterprise tiers
            <span>·</span>
            MCP for Cursor
          </p>
          <ProductMock />
        </section>

        <section className="lm-logos lm-container">
          <p className="lm-logos-label">Collectors for</p>
          <div className="lm-logos-row">
            {TOOLS.map((t) => (
              <span key={t}>{t}</span>
            ))}
          </div>
        </section>

        <section className="lm-stats lm-container">
          <div className="lm-stats-intro">
            <h2>Numbers platform teams ask for.</h2>
            <p style={{ color: 'var(--lm-text-subtle)', margin: 0, fontSize: '0.875rem' }}>
              RetrySight answers the FinOps and enablement questions vendor dashboards skip.
            </p>
          </div>
          <div className="lm-stats-grid">
            {STATS.map((s) => (
              <div key={s.desc}>
                <div className="lm-stat-num">{s.num}</div>
                <div className="lm-stat-desc">{s.desc}</div>
              </div>
            ))}
          </div>
        </section>

        <section className="lm-section lm-container" id="features">
          <div className="lm-section-head">
            <h2>Everything you need. Nothing you don&apos;t.</h2>
            <p style={{ margin: 0, fontSize: '0.875rem' }}>
              Focused primitives for coding-agent FinOps — typed ingest, live subscriptions, and
              replay infrastructure.
            </p>
          </div>
          <div className="lm-bento">
            {FEATURES.map((f) => (
              <article
                key={f.title}
                className={`lm-bento-card${f.wide ? ' lm-bento-wide' : ''}`}
              >
                <h3>{f.title}</h3>
                <p>{f.desc}</p>
                {f.code && <CodeTabs />}
              </article>
            ))}
          </div>
        </section>

        <section className="lm-section lm-container" id="pricing">
          <div className="lm-section-head">
            <h2>Pricing that scales with your fleet.</h2>
            <p style={{ margin: 0, fontSize: '0.875rem' }}>
              Placeholder tiers aligned with RetrySight installation profiles — swap before launch.
            </p>
          </div>
          <div className="lm-pricing-toggle">
            <button
              type="button"
              className={annual ? '' : 'active'}
              onClick={() => setAnnual(false)}
            >
              Monthly
            </button>
            <button
              type="button"
              className={annual ? 'active' : ''}
              onClick={() => setAnnual(true)}
            >
              Annual
            </button>
          </div>
          <div className="lm-pricing-grid">
            {PLANS.map((plan) => (
              <article
                key={plan.tag}
                className={`lm-price-card${plan.popular ? ' popular' : ''}`}
              >
                {plan.popular && <span className="lm-price-popular">Most popular</span>}
                <div className="lm-price-tag">{plan.tag}</div>
                <div className="lm-price-amount">{plan.price}</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--lm-text-subtle)' }}>
                  {annual && plan.price !== '$0' && plan.price !== 'Custom'
                    ? `${plan.period} · billed annually`
                    : plan.period}
                </div>
                <p style={{ fontSize: '0.8125rem', margin: '0.75rem 0 0' }}>{plan.blurb}</p>
                <ul>
                  {plan.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
                <a className={`lm-btn${plan.popular ? ' lm-btn-primary' : ''}`} href="#cta">
                  {plan.tag === 'Enterprise' ? 'Talk to sales' : 'Get started'}
                </a>
              </article>
            ))}
          </div>
        </section>

        <section className="lm-cta lm-container" id="cta">
          <h2>Compare Lambda vs RicoUI before you buy.</h2>
          <p>
            This page ports Astro Lambda&apos;s dark dev-tool aesthetic with RetrySight copy. Toggle
            to RicoUI for the warm editorial SaaS look — then pick a template for retrysight.com.
          </p>
          <div className="lm-hero-cta">
            <Link className="lm-btn lm-btn-primary" to="/marketing-preview/powerai">
              View PowerAI preview
            </Link>
            <Link className="lm-btn" to="/marketing-preview/ricoui">
              View RicoUI preview
            </Link>
          </div>
        </section>
      </main>

      <footer className="lm-footer lm-container">
        <p>
          © {new Date().getFullYear()} RetrySight · Lambda visual preview · Astro Lambda is a paid
          theme (~$79) by Lambda Studio
        </p>
      </footer>
    </div>
  )
}
