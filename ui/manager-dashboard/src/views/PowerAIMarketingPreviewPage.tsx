/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import '../styles/powerai-preview.css'

const TOOLS = ['Cursor', 'Claude Code', 'Codex', 'Kiro', 'Warp', 'Copilot', 'MCP', 'OTel']

const STATS = [
  { value: '7+', label: 'Agent sources collected' },
  { value: '18%', label: 'Avg fleet retry rate (demo)' },
  { value: '$4.2k', label: 'Weekly token spend (demo)' },
  { value: '24', label: 'Active collectors (demo)' },
]

const FEATURES = [
  {
    icon: '↻',
    title: 'Retry observability',
    desc: 'Surface edits, test failures, rejected diffs, and compaction across every developer machine.',
  },
  {
    icon: '◈',
    title: 'Fleet dashboard',
    desc: 'KPIs, live subscriptions, and task timelines in one self-hosted console.',
  },
  {
    icon: '$',
    title: 'FinOps-ready cost',
    desc: 'Token spend by model, user, and tool — export reports when finance asks.',
  },
  {
    icon: '⎘',
    title: 'Session replay',
    desc: 'JSON, Markdown, or prompt-ready exports for debugging and eval workflows.',
  },
  {
    icon: '⚿',
    title: 'Enterprise controls',
    desc: 'SSO, API key rotation, BYO RDBMS, and tier-gated analytics.',
  },
  {
    icon: '◎',
    title: 'IDE-native MCP',
    desc: 'Query retry metrics from Cursor without leaving the editor.',
  },
]

const PLANS = [
  {
    name: 'Core',
    price: 'Free',
    sub: 'self-host',
    featured: false,
    items: ['Ingest + dashboard', 'Tasks & live feed', 'Docker compose', 'GraphQL API'],
  },
  {
    name: 'Analytics',
    price: 'Contact',
    sub: 'per fleet',
    featured: true,
    items: ['Cost & retry pages', 'Executive reports', 'Git context', 'Archive local'],
  },
  {
    name: 'Enterprise',
    price: 'Custom',
    sub: 'annual',
    featured: false,
    items: ['SSO / SAML', 'MCP + integrations', 'ClickHouse option', 'Dedicated support'],
  },
]

const EVENTS = [
  { tag: 'EDIT', text: 'cursor · auth-refactor · turn 12' },
  { tag: 'RETRY', text: 'TEST_FAIL · api-client · attempt 3' },
  { tag: 'TOKEN', text: 'codex · 24.2k in / 1.1k out' },
  { tag: 'RETRY', text: 'DIFF_REJECTED · layout.tsx' },
  { tag: 'LIVE', text: 'heartbeat · dev-macbook-07' },
]

function DashboardShot() {
  const bars = [35, 55, 42, 70, 48, 85, 62]
  return (
    <div className="pa-dash">
      <div className="pa-dash-top">
        <strong>RetrySight · Production fleet</strong>
        <span className="pa-gradient-label">● Live</span>
      </div>
      <div className="pa-dash-body">
        <div className="pa-dash-main">
          <div className="pa-metrics">
            <div className="pa-metric">
              <div className="pa-metric-label">Retry rate</div>
              <div className="pa-metric-value">18.4%</div>
              <div className="pa-metric-delta">▼ 2.1% week</div>
            </div>
            <div className="pa-metric">
              <div className="pa-metric-label">Token spend</div>
              <div className="pa-metric-value">$4.2k</div>
              <div className="pa-metric-delta">▲ 6.3% week</div>
            </div>
          </div>
          <div className="pa-chart">
            <div className="pa-chart-bars" aria-hidden="true">
              {bars.map((h, i) => (
                <div key={i} className="pa-chart-bar" style={{ height: `${h}%` }} />
              ))}
            </div>
          </div>
        </div>
        <div className="pa-side-panel">
          <div className="pa-side-title">Live retry stream</div>
          {EVENTS.map((e) => (
            <div key={e.text} className="pa-event">
              <span className="tag">{e.tag}</span> {e.text}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export default function PowerAIMarketingPreviewPage() {
  useEffect(() => {
    document.documentElement.classList.add('powerai-preview-active')
    return () => document.documentElement.classList.remove('powerai-preview-active')
  }, [])

  return (
    <div className="powerai-marketing">
      <div className="pa-center-line" aria-hidden="true" />
      <div className="pa-glow" aria-hidden="true" />

      <div className="pa-preview-banner">
        <span>PowerAI Astro preview — RetrySight content</span>
        <Link to="/marketing-preview/lambda">Lambda</Link>
        <span>·</span>
        <Link to="/marketing-preview/ricoui">RicoUI</Link>
        <span>·</span>
        <span className="pa-active">PowerAI</span>
        <span>·</span>
        <Link to="/login">Login</Link>
        <Link to="/">Dashboard →</Link>
      </div>

      <header className="pa-header">
        <div className="pa-header-inner">
          <Link className="pa-brand" to="/marketing-preview/powerai">
            <img src="/retrysight-logo.svg" alt="" width={28} height={28} />
            RetrySight
          </Link>
          <nav className="pa-nav" aria-label="Marketing">
            <a href="#features">Features</a>
            <a href="#integrations">Integrations</a>
            <a href="#pricing">Pricing</a>
          </nav>
          <div className="pa-header-actions">
            <Link className="pa-btn pa-btn-dark" to="/login">
              Sign in
            </Link>
            <a className="pa-btn pa-btn-primary" href="#cta">
              Get started
            </a>
          </div>
        </div>
      </header>

      <div className="pa-main-wrap">
        <section className="pa-hero pa-container">
          <div className="pa-hero-badge-wrap">
            <div className="pa-hero-badge">
              <div className="pa-avatar-stack" aria-hidden="true">
                <span className="pa-avatar">C</span>
                <span className="pa-avatar">A</span>
                <span className="pa-avatar">R</span>
              </div>
              <span className="pa-gradient-label">Fleet observability for AI coding agents</span>
            </div>
          </div>

          <h1>
            Powering the next
            <br />
            <span className="pa-gradient-text">generation of agent FinOps</span>
          </h1>
          <p className="pa-hero-lead">
            RetrySight tracks retries, token waste, and session activity across Cursor, Claude Code,
            Codex, and your entire collector fleet — self-hosted, with the polish of a modern AI SaaS
            launch site.
          </p>
          <div className="pa-hero-cta">
            <a className="pa-btn pa-btn-primary" href="#cta">
              Start self-hosted
            </a>
            <Link className="pa-btn pa-btn-dark" to="/">
              Open live dashboard
            </Link>
          </div>

          <div className="pa-hero-shot">
            <DashboardShot />
          </div>
        </section>

        <section className="pa-section pa-container">
          <div className="pa-stats">
            {STATS.map((s) => (
              <div key={s.label}>
                <div className="pa-stat-value">{s.value}</div>
                <div className="pa-stat-label">{s.label}</div>
              </div>
            ))}
          </div>
        </section>

        <section className="pa-section pa-container" id="features">
          <div className="pa-section-head">
            <h2>
              Essential features for <span className="pa-gradient-text">coding agent teams</span>
            </h2>
            <p style={{ margin: 0, fontSize: '0.9375rem' }}>
              PowerAI&apos;s dark gradient aesthetic meets RetrySight&apos;s observability story.
            </p>
          </div>
          <div className="pa-features">
            {FEATURES.map((f) => (
              <article key={f.title} className="pa-feature">
                <div className="pa-feature-icon" aria-hidden="true">
                  {f.icon}
                </div>
                <h3>{f.title}</h3>
                <p>{f.desc}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="pa-section pa-container" id="integrations">
          <div className="pa-section-head">
            <h2>Works with your agent stack</h2>
            <p style={{ margin: 0, fontSize: '0.9375rem' }}>
              Collectors and integrations RetrySight ships today — swap logos for SVGs on the real site.
            </p>
          </div>
          <div className="pa-integrations">
            {TOOLS.map((t) => (
              <span key={t} className="pa-integration">
                {t}
              </span>
            ))}
          </div>
        </section>

        <section className="pa-section pa-container" id="pricing">
          <div className="pa-section-head">
            <h2>Plans that scale with your fleet</h2>
          </div>
          <div className="pa-pricing-grid">
            {PLANS.map((plan) => (
              <article
                key={plan.name}
                className={`pa-price-card${plan.featured ? ' featured' : ''}`}
              >
                <div className="pa-price-name">{plan.name}</div>
                <div className="pa-price-amount">{plan.price}</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--pa-text-muted)' }}>{plan.sub}</div>
                <ul>
                  {plan.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
                <a className={`pa-btn${plan.featured ? ' pa-btn-primary' : ' pa-btn-dark'}`} href="#cta">
                  {plan.featured ? 'Book demo' : 'Learn more'}
                </a>
              </article>
            ))}
          </div>
        </section>

        <section className="pa-section pa-container">
          <div className="pa-testimonial">
            <blockquote>
              &ldquo;We finally see retry loops before they show up on the invoice. RetrySight turned
              local agent logs into something platform leadership can actually act on.&rdquo;
            </blockquote>
            <cite>— Platform lead · multi-tool AI coding fleet (demo quote)</cite>
          </div>
        </section>

        <section className="pa-cta pa-container" id="cta">
          <h2>
            Compare PowerAI, Lambda, and RicoUI for <span className="pa-gradient-text">retrysight.com</span>
          </h2>
          <p>
            PowerAI leans AI-startup bold (purple gradients, glass cards). Lambda is minimal dev-tool.
            RicoUI is warm editorial SaaS. Pick the reach aesthetic that fits your buyer.
          </p>
          <div className="pa-hero-cta">
            <Link className="pa-btn pa-btn-primary" to="/marketing-preview/lambda">
              View Lambda
            </Link>
            <Link className="pa-btn pa-btn-dark" to="/marketing-preview/ricoui">
              View RicoUI
            </Link>
            <a
              className="pa-btn pa-btn-dark"
              href="https://github.com/sitepins/powerai-astro"
              target="_blank"
              rel="noreferrer"
            >
              PowerAI repo ↗
            </a>
          </div>
          <div className="pa-footer-title" aria-hidden="true">
            RETRYSIGHT
          </div>
        </section>
      </div>

      <footer className="pa-footer">
        © {new Date().getFullYear()} RetrySight · PowerAI visual preview · Free theme by Sitepins
      </footer>
    </div>
  )
}
