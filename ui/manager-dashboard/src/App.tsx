/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import SettingsMenu from './components/SettingsMenu'
import DashboardPage from './views/DashboardPage'
import TasksPage from './views/TasksPage'
import AgentsPage from './views/AgentsPage'
import TaskDetailPage from './views/TaskDetailPage'
import LivePage from './views/LivePage'
import LoginPage from './views/LoginPage'
import AuthCallbackPage from './views/AuthCallbackPage'
import ReportsPage from './views/ReportsPage'
import CostPage from './views/CostPage'
import RetriesPage from './views/RetriesPage'
import SecuritySettingsPage from './views/SecuritySettingsPage'
import ApiKeysPage from './views/ApiKeysPage'
import EvaluationsPage from './views/EvaluationsPage'
import ExperimentsPage from './views/ExperimentsPage'
import PlaygroundPage from './views/PlaygroundPage'
import TracesPage from './views/TracesPage'
import PatternsPage from './views/PatternsPage'
import ArchiveRetentionPage from './views/ArchiveRetentionPage'
import MarketingPreviewPage from './views/MarketingPreviewPage'
import LambdaMarketingPreviewPage from './views/LambdaMarketingPreviewPage'
import PowerAIMarketingPreviewPage from './views/PowerAIMarketingPreviewPage'
import InsightsChatDrawer from './components/InsightsChatDrawer'
import { usePlatformFeatures } from './platformFeatures'
import { OWNERSHIP } from './ownership'

function ProtectedShell() {
  const { token, displayName, logout, isSecadmin } = useAuth()
  const { costEnabled, reportsEnabled, evaluationsEnabled, evalExperimentsEnabled, traceQueryEnabled, patternsEnabled } = usePlatformFeatures()
  const initials = (displayName ?? 'User')
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  if (!token) {
    return <Navigate to="/login" replace />
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <img
            className="sidebar-logo"
            src="/retrysight-logo.svg"
            alt=""
            width={36}
            height={36}
            aria-hidden="true"
          />
          <h1>RetrySight</h1>
        </div>
        <p className="sidebar-tagline">Performance Monitoring</p>
        <div className="sidebar-user">
          <span className="sidebar-user-avatar" aria-hidden="true">{initials}</span>
          <span className="sidebar-user-name">{displayName ?? 'User'}</span>
          <SettingsMenu />
        </div>
        <nav className="sidebar-nav">
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'active' : '')}>
            <span className="nav-icon" aria-hidden="true">◈</span>
            Dashboard
          </NavLink>
          <NavLink to="/live" className={({ isActive }) => (isActive ? 'active' : '')}>
            <span className="nav-icon" aria-hidden="true">◎</span>
            Live
            <span className="nav-live-dot" aria-hidden="true" />
          </NavLink>
          <NavLink to="/tasks" className={({ isActive }) => (isActive ? 'active' : '')}>
            <span className="nav-icon" aria-hidden="true">▤</span>
            Tasks
          </NavLink>
          <NavLink to="/agents" className={({ isActive }) => (isActive ? 'active' : '')}>
            <span className="nav-icon" aria-hidden="true">⬡</span>
            Collectors
          </NavLink>
          {reportsEnabled && (
            <NavLink to="/reports" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">▣</span>
              Reports
            </NavLink>
          )}
          {evaluationsEnabled && (
            <NavLink to="/evaluations" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">✓</span>
              Evaluations
            </NavLink>
          )}
          {evalExperimentsEnabled && (
            <NavLink to="/experiments" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">⚗</span>
              Experiments
            </NavLink>
          )}
          {evaluationsEnabled && (
            <NavLink to="/playground" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">▶</span>
              Playground
            </NavLink>
          )}
          {traceQueryEnabled && (
            <NavLink to="/traces" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">⎇</span>
              Traces
            </NavLink>
          )}
          {patternsEnabled && (
            <NavLink to="/patterns" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">◫</span>
              Patterns
            </NavLink>
          )}
          {costEnabled && (
            <NavLink to="/cost" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">$</span>
              Cost
            </NavLink>
          )}
          {costEnabled && (
            <NavLink to="/retries" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">↻</span>
              Retries
            </NavLink>
          )}
          {/* SECADMIN is the platform superuser and sees every admin page. */}
          {isSecadmin && (
            <NavLink to="/admin/security" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">⚿</span>
              Security
            </NavLink>
          )}
          {isSecadmin && (
            <NavLink to="/admin/api-keys" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">🔑</span>
              API Keys
            </NavLink>
          )}
          {isSecadmin && (
            <NavLink to="/admin/archive" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon" aria-hidden="true">▤</span>
              Archive
            </NavLink>
          )}
          <NavLink to="/marketing-preview/powerai" className={({ isActive }) => (isActive ? 'active' : '')}>
            <span className="nav-icon" aria-hidden="true">◉</span>
            PowerAI preview
          </NavLink>
          <NavLink to="/marketing-preview/lambda" className={({ isActive }) => (isActive ? 'active' : '')}>
            <span className="nav-icon" aria-hidden="true">◐</span>
            Lambda preview
          </NavLink>
          <NavLink to="/marketing-preview/ricoui" className={({ isActive }) => (isActive ? 'active' : '')}>
            <span className="nav-icon" aria-hidden="true">◑</span>
            RicoUI preview
          </NavLink>
        </nav>
        <div className="sidebar-footer">
          <button className="logout-btn" onClick={logout}>Sign out</button>
          <p className="sidebar-copyright" title={OWNERSHIP.notice}>
            © {OWNERSHIP.year} {OWNERSHIP.owner}
            <span>{OWNERSHIP.license}</span>
          </p>
        </div>
      </aside>
      <main className="content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/live" element={<LivePage />} />
          <Route path="/tasks" element={<TasksPage />} />
          <Route path="/tasks/:id" element={<TaskDetailPage />} />
          <Route path="/agents" element={<AgentsPage />} />
          <Route path="/evaluations" element={<EvaluationsPage />} />
          <Route path="/experiments" element={<ExperimentsPage />} />
          <Route path="/playground" element={<PlaygroundPage />} />
          <Route path="/traces" element={<TracesPage />} />
          <Route path="/patterns" element={<PatternsPage />} />
          {reportsEnabled && <Route path="/reports" element={<ReportsPage />} />}
          {costEnabled && <Route path="/cost" element={<CostPage />} />}
          {costEnabled && <Route path="/retries" element={<RetriesPage />} />}
          <Route path="/admin/security" element={<SecuritySettingsPage />} />
          <Route path="/admin/api-keys" element={<ApiKeysPage />} />
          <Route path="/admin/archive" element={<ArchiveRetentionPage />} />
        </Routes>
      </main>
      <InsightsChatDrawer />
    </div>
  )
}

export default function App() {
  const { token } = useAuth()

  return (
    <Routes>
      <Route path="/marketing-preview" element={<Navigate to="/marketing-preview/lambda" replace />} />
      <Route path="/marketing-preview/ricoui" element={<MarketingPreviewPage />} />
      <Route path="/marketing-preview/powerai" element={<PowerAIMarketingPreviewPage />} />
      <Route path="/marketing-preview/lambda" element={<LambdaMarketingPreviewPage />} />
      <Route path="/login" element={token ? <Navigate to="/" replace /> : <LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
      <Route path="/*" element={<ProtectedShell />} />
    </Routes>
  )
}
