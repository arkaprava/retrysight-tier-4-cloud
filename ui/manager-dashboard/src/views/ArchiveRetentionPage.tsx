/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { useMutation, useQuery } from '@apollo/client'
import { useAuth } from '../auth/AuthContext'
import {
  ARCHIVE_COLD_OBJECTS,
  ARCHIVE_POLICY,
  ARCHIVE_RUNS,
  HOT_DATA_PARTITIONS,
  REHYDRATE_TOKEN_USAGE_ARCHIVE,
  RUN_ARCHIVE_JOB,
  TEST_ARCHIVE_DESTINATION,
  UPDATE_ARCHIVE_POLICY,
} from '../graphql/client'
import { usePlatformFeatures } from '../platformFeatures'

type ArchiveCapabilities = {
  retentionPurge: boolean
  archiveLocal: boolean
  archiveNetwork: boolean
  archiveObjectStorage: boolean
  archiveRestore: boolean
  rollupAnalytics: boolean
  partitionMaintenance: boolean
}

type ArchivePolicyData = {
  enabled: boolean
  mode: string
  scheduleCron: string
  taskEventsDays: number
  tokenUsageDays: number
  taskHeadersDays: number
  datasets: string[]
  destinationType: string
  localPath: string | null
  networkPath: string | null
  s3Bucket: string | null
  s3Prefix: string | null
  s3Region: string | null
  s3Endpoint: string | null
  s3AccessKeyEnv: string | null
  s3SecretKeyEnv: string | null
  legalHold: boolean
  updatedAt: string
  capabilities: ArchiveCapabilities
}

const DATASET_OPTIONS = ['TASK_EVENTS', 'TOKEN_USAGE', 'TASKS'] as const

export default function ArchiveRetentionPage() {
  const { token, isSecadmin } = useAuth()
  const {
    archiveRestoreEnabled,
    partitionMaintenanceEnabled,
  } = usePlatformFeatures()

  const { data, loading, refetch } = useQuery<{ archivePolicy: ArchivePolicyData }>(ARCHIVE_POLICY, {
    skip: !isSecadmin,
  })
  const { data: runsData, refetch: refetchRuns } = useQuery(ARCHIVE_RUNS, { skip: !isSecadmin })
  const { data: partitionsData } = useQuery(HOT_DATA_PARTITIONS, {
    skip: !isSecadmin || !partitionMaintenanceEnabled,
  })

  const [updatePolicy, { loading: saving }] = useMutation(UPDATE_ARCHIVE_POLICY)
  const [testDestination, { loading: testing }] = useMutation(TEST_ARCHIVE_DESTINATION)
  const [runArchiveJob, { loading: running }] = useMutation(RUN_ARCHIVE_JOB)
  const [rehydrateArchive, { loading: rehydrating }] = useMutation(REHYDRATE_TOKEN_USAGE_ARCHIVE)

  const [enabled, setEnabled] = useState(false)
  const [mode, setMode] = useState('DELETE_ONLY')
  const [scheduleCron, setScheduleCron] = useState('0 30 2 * * *')
  const [taskEventsDays, setTaskEventsDays] = useState(90)
  const [tokenUsageDays, setTokenUsageDays] = useState(90)
  const [taskHeadersDays, setTaskHeadersDays] = useState(365)
  const [datasets, setDatasets] = useState<string[]>(['TASK_EVENTS', 'TOKEN_USAGE'])
  const [destinationType, setDestinationType] = useState('LOCAL_FS')
  const [localPath, setLocalPath] = useState('')
  const [networkPath, setNetworkPath] = useState('')
  const [s3Bucket, setS3Bucket] = useState('')
  const [s3Prefix, setS3Prefix] = useState('')
  const [s3Region, setS3Region] = useState('')
  const [s3Endpoint, setS3Endpoint] = useState('')
  const [s3AccessKeyEnv, setS3AccessKeyEnv] = useState('')
  const [s3SecretKeyEnv, setS3SecretKeyEnv] = useState('')
  const [legalHold, setLegalHold] = useState(false)

  const [coldDataset, setColdDataset] = useState('')
  const [coldDayPrefix, setColdDayPrefix] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const policy = data?.archivePolicy
  const caps = policy?.capabilities

  const {
    data: coldData,
    loading: coldLoading,
    refetch: refetchCold,
  } = useQuery(ARCHIVE_COLD_OBJECTS, {
    skip: !isSecadmin || !archiveRestoreEnabled || !(caps?.archiveRestore ?? false),
    variables: {
      dataset: coldDataset || null,
      dayPrefix: coldDayPrefix || null,
    },
  })

  useEffect(() => {
    if (!policy) return
    setEnabled(policy.enabled)
    setMode(policy.mode)
    setScheduleCron(policy.scheduleCron)
    setTaskEventsDays(policy.taskEventsDays)
    setTokenUsageDays(policy.tokenUsageDays)
    setTaskHeadersDays(policy.taskHeadersDays)
    setDatasets(policy.datasets?.length ? [...policy.datasets] : ['TASK_EVENTS', 'TOKEN_USAGE'])
    setDestinationType(policy.destinationType)
    setLocalPath(policy.localPath ?? '')
    setNetworkPath(policy.networkPath ?? '')
    setS3Bucket(policy.s3Bucket ?? '')
    setS3Prefix(policy.s3Prefix ?? '')
    setS3Region(policy.s3Region ?? '')
    setS3Endpoint(policy.s3Endpoint ?? '')
    setS3AccessKeyEnv(policy.s3AccessKeyEnv ?? '')
    setS3SecretKeyEnv(policy.s3SecretKeyEnv ?? '')
    setLegalHold(policy.legalHold)
  }, [policy])

  const destinationOptions = useMemo(() => {
    const options: { value: string; label: string; enabled: boolean }[] = [
      { value: 'LOCAL_FS', label: 'Local filesystem', enabled: caps?.archiveLocal ?? false },
      { value: 'NETWORK_FS', label: 'Network filesystem', enabled: caps?.archiveNetwork ?? false },
      { value: 'S3', label: 'Object storage (S3)', enabled: caps?.archiveObjectStorage ?? false },
    ]
    return options
  }, [caps])

  if (!isSecadmin) {
    return <Navigate to="/" replace />
  }

  function toggleDataset(dataset: string) {
    setDatasets((prev) =>
      prev.includes(dataset) ? prev.filter((d) => d !== dataset) : [...prev, dataset],
    )
  }

  async function handleSave(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSuccess(null)
    try {
      await updatePolicy({
        variables: {
          input: {
            enabled,
            mode,
            scheduleCron,
            taskEventsDays,
            tokenUsageDays,
            taskHeadersDays,
            datasets,
            destinationType,
            localPath: localPath || null,
            networkPath: networkPath || null,
            s3Bucket: s3Bucket || null,
            s3Prefix: s3Prefix || null,
            s3Region: s3Region || null,
            s3Endpoint: s3Endpoint || null,
            s3AccessKeyEnv: s3AccessKeyEnv || null,
            s3SecretKeyEnv: s3SecretKeyEnv || null,
            legalHold,
          },
        },
      })
      setSuccess('Archive policy saved.')
      await refetch()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save policy')
    }
  }

  async function handleTestDestination() {
    setError(null)
    setSuccess(null)
    try {
      await testDestination()
      setSuccess('Destination test succeeded.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Destination test failed')
    }
  }

  async function handleRun(dryRun: boolean) {
    setError(null)
    setSuccess(null)
    try {
      const result = await runArchiveJob({ variables: { dryRun } })
      const run = result.data?.runArchiveJob
      setSuccess(
        dryRun
          ? `Dry run finished: ${run?.status ?? 'unknown'} (${run?.rowsExported ?? 0} rows would export).`
          : `Archive job finished: ${run?.status ?? 'unknown'} (exported ${run?.rowsExported ?? 0}, deleted ${run?.rowsDeleted ?? 0}).`,
      )
      await Promise.all([refetchRuns(), refetch()])
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Archive job failed')
    }
  }

  async function handleDownload(path: string) {
    if (!token) return
    setError(null)
    try {
      const response = await fetch(`/api/v1/archive/download?path=${encodeURIComponent(path)}`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (!response.ok) {
        throw new Error(await response.text() || `Download failed (${response.status})`)
      }
      const blob = await response.blob()
      const filename = path.includes('/') ? path.slice(path.lastIndexOf('/') + 1) : path
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Download failed')
    }
  }

  async function handleRehydrate(path: string) {
    setError(null)
    setSuccess(null)
    try {
      const result = await rehydrateArchive({ variables: { path } })
      setSuccess(`Rehydrated ${result.data?.rehydrateTokenUsageArchive ?? 0} token usage rows.`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Rehydrate failed')
    }
  }

  const canArchive = mode !== 'DELETE_ONLY'
  const showCold = archiveRestoreEnabled && (caps?.archiveRestore ?? false)

  return (
    <div className="security-page">
      <header className="page-header">
        <h2>Archive & Retention</h2>
        <p className="page-subtitle">
          Configure hot-data retention, archive destinations, and cold restore
        </p>
      </header>

      {loading && !policy ? (
        <p className="empty">Loading…</p>
      ) : (
        <>
          {caps && (
            <section className="security-panel">
              <h3>Capabilities</h3>
              <p className="page-subtitle" style={{ marginBottom: '0.75rem' }}>
                Tier-gated features for this installation
              </p>
              <ul className="capability-list">
                <li className={caps.retentionPurge ? 'on' : 'off'}>Retention purge</li>
                <li className={caps.archiveLocal ? 'on' : 'off'}>Local archive</li>
                <li className={caps.archiveNetwork ? 'on' : 'off'}>Network archive</li>
                <li className={caps.archiveObjectStorage ? 'on' : 'off'}>S3 / object storage</li>
                <li className={caps.archiveRestore ? 'on' : 'off'}>Restore / download</li>
                <li className={caps.rollupAnalytics ? 'on' : 'off'}>Rollup analytics</li>
                <li className={caps.partitionMaintenance ? 'on' : 'off'}>Partition maintenance</li>
              </ul>
            </section>
          )}

          <section className="security-panel">
            <h3>Policy</h3>
            <form className="archive-form" onSubmit={handleSave}>
              <label className="checkbox-row">
                <input
                  type="checkbox"
                  checked={enabled}
                  onChange={(e) => setEnabled(e.target.checked)}
                  disabled={!caps?.retentionPurge}
                />
                Enabled
              </label>

              <label>
                Mode
                <select value={mode} onChange={(e) => setMode(e.target.value)}>
                  <option value="DELETE_ONLY">DELETE_ONLY</option>
                  <option value="ARCHIVE_THEN_DELETE" disabled={!caps?.archiveLocal && !caps?.archiveNetwork && !caps?.archiveObjectStorage}>
                    ARCHIVE_THEN_DELETE
                  </option>
                  <option value="ARCHIVE_ONLY" disabled={!caps?.archiveLocal && !caps?.archiveNetwork && !caps?.archiveObjectStorage}>
                    ARCHIVE_ONLY
                  </option>
                </select>
              </label>

              <label>
                Schedule (cron)
                <input value={scheduleCron} onChange={(e) => setScheduleCron(e.target.value)} required />
              </label>

              <div className="archive-days-grid">
                <label>
                  Task events days
                  <input
                    type="number"
                    min={1}
                    value={taskEventsDays}
                    onChange={(e) => setTaskEventsDays(Number(e.target.value))}
                    required
                  />
                </label>
                <label>
                  Token usage days
                  <input
                    type="number"
                    min={1}
                    value={tokenUsageDays}
                    onChange={(e) => setTokenUsageDays(Number(e.target.value))}
                    required
                  />
                </label>
                <label>
                  Task headers days
                  <input
                    type="number"
                    min={1}
                    value={taskHeadersDays}
                    onChange={(e) => setTaskHeadersDays(Number(e.target.value))}
                    required
                  />
                </label>
              </div>

              <fieldset className="archive-fieldset">
                <legend>Datasets</legend>
                {DATASET_OPTIONS.map((dataset) => (
                  <label key={dataset} className="checkbox-row">
                    <input
                      type="checkbox"
                      checked={datasets.includes(dataset)}
                      onChange={() => toggleDataset(dataset)}
                    />
                    {dataset}
                  </label>
                ))}
              </fieldset>

              <label>
                Destination
                <select
                  value={destinationType}
                  onChange={(e) => setDestinationType(e.target.value)}
                  disabled={!canArchive}
                >
                  {destinationOptions.map((opt) => (
                    <option key={opt.value} value={opt.value} disabled={!opt.enabled}>
                      {opt.label}{!opt.enabled ? ' (tier locked)' : ''}
                    </option>
                  ))}
                </select>
              </label>

              {destinationType === 'LOCAL_FS' && (
                <label>
                  Local path
                  <input
                    value={localPath}
                    onChange={(e) => setLocalPath(e.target.value)}
                    placeholder="~/.agent-retry/archive"
                    disabled={!canArchive || !caps?.archiveLocal}
                  />
                </label>
              )}

              {destinationType === 'NETWORK_FS' && (
                <label>
                  Network path
                  <input
                    value={networkPath}
                    onChange={(e) => setNetworkPath(e.target.value)}
                    placeholder="/mnt/archive"
                    disabled={!canArchive || !caps?.archiveNetwork}
                  />
                </label>
              )}

              {destinationType === 'S3' && (
                <>
                  <label>
                    S3 bucket
                    <input
                      value={s3Bucket}
                      onChange={(e) => setS3Bucket(e.target.value)}
                      disabled={!canArchive || !caps?.archiveObjectStorage}
                    />
                  </label>
                  <label>
                    S3 prefix
                    <input
                      value={s3Prefix}
                      onChange={(e) => setS3Prefix(e.target.value)}
                      disabled={!canArchive || !caps?.archiveObjectStorage}
                    />
                  </label>
                  <label>
                    S3 region
                    <input
                      value={s3Region}
                      onChange={(e) => setS3Region(e.target.value)}
                      disabled={!canArchive || !caps?.archiveObjectStorage}
                    />
                  </label>
                  <label>
                    S3 endpoint (optional)
                    <input
                      value={s3Endpoint}
                      onChange={(e) => setS3Endpoint(e.target.value)}
                      disabled={!canArchive || !caps?.archiveObjectStorage}
                    />
                  </label>
                  <label>
                    Access key env var
                    <input
                      value={s3AccessKeyEnv}
                      onChange={(e) => setS3AccessKeyEnv(e.target.value)}
                      placeholder="AWS_ACCESS_KEY_ID"
                      disabled={!canArchive || !caps?.archiveObjectStorage}
                    />
                  </label>
                  <label>
                    Secret key env var
                    <input
                      value={s3SecretKeyEnv}
                      onChange={(e) => setS3SecretKeyEnv(e.target.value)}
                      placeholder="AWS_SECRET_ACCESS_KEY"
                      disabled={!canArchive || !caps?.archiveObjectStorage}
                    />
                  </label>
                </>
              )}

              <label className="checkbox-row">
                <input
                  type="checkbox"
                  checked={legalHold}
                  onChange={(e) => setLegalHold(e.target.checked)}
                />
                Legal hold (skip deletes)
              </label>

              <div className="archive-actions">
                <button type="submit" disabled={saving || !caps?.retentionPurge}>
                  {saving ? 'Saving…' : 'Save policy'}
                </button>
                <button
                  type="button"
                  onClick={() => void handleTestDestination()}
                  disabled={testing || !canArchive}
                >
                  {testing ? 'Testing…' : 'Test destination'}
                </button>
                <button
                  type="button"
                  onClick={() => void handleRun(true)}
                  disabled={running || !caps?.retentionPurge}
                >
                  Dry run
                </button>
                <button
                  type="button"
                  onClick={() => void handleRun(false)}
                  disabled={running || !caps?.retentionPurge}
                >
                  {running ? 'Running…' : 'Run archive'}
                </button>
              </div>
            </form>
            {policy?.updatedAt && (
              <p className="page-subtitle" style={{ marginTop: '0.75rem' }}>
                Last updated {new Date(policy.updatedAt).toLocaleString()}
              </p>
            )}
          </section>

          {error && <p className="login-error">{error}</p>}
          {success && <p className="security-success">{success}</p>}

          <section className="security-panel">
            <h3>Recent runs</h3>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Started</th>
                  <th>Status</th>
                  <th>Dry run</th>
                  <th>Mode</th>
                  <th>Exported</th>
                  <th>Deleted</th>
                  <th>Bytes</th>
                  <th>Error</th>
                </tr>
              </thead>
              <tbody>
                {(runsData?.archiveRuns ?? []).length === 0 ? (
                  <tr>
                    <td colSpan={8} className="empty">No archive runs yet</td>
                  </tr>
                ) : (
                  (runsData?.archiveRuns ?? []).map((run: {
                    id: string
                    startedAt: string
                    status: string
                    dryRun: boolean
                    mode: string | null
                    rowsExported: number
                    rowsDeleted: number
                    bytesWritten: number
                    errorMessage: string | null
                  }) => (
                    <tr key={run.id}>
                      <td>{new Date(run.startedAt).toLocaleString()}</td>
                      <td>{run.status}</td>
                      <td>{run.dryRun ? 'Yes' : 'No'}</td>
                      <td>{run.mode ?? '—'}</td>
                      <td>{run.rowsExported}</td>
                      <td>{run.rowsDeleted}</td>
                      <td>{run.bytesWritten}</td>
                      <td>{run.errorMessage ?? '—'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </section>

          {showCold && (
            <section className="security-panel">
              <h3>Cold objects</h3>
              <div className="archive-cold-filters">
                <input
                  placeholder="Dataset filter"
                  value={coldDataset}
                  onChange={(e) => setColdDataset(e.target.value)}
                />
                <input
                  placeholder="Day prefix (YYYY-MM-DD)"
                  value={coldDayPrefix}
                  onChange={(e) => setColdDayPrefix(e.target.value)}
                />
                <button type="button" onClick={() => void refetchCold()}>
                  Refresh
                </button>
              </div>
              {coldLoading ? (
                <p className="empty">Loading cold objects…</p>
              ) : (
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Dataset</th>
                      <th>Day</th>
                      <th>Path</th>
                      <th>Bytes</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {(coldData?.archiveColdObjects ?? []).length === 0 ? (
                      <tr>
                        <td colSpan={5} className="empty">No cold objects found</td>
                      </tr>
                    ) : (
                      (coldData?.archiveColdObjects ?? []).map((obj: {
                        dataset: string
                        day: string
                        path: string
                        bytes: number
                      }) => (
                        <tr key={obj.path}>
                          <td>{obj.dataset}</td>
                          <td>{obj.day}</td>
                          <td><code>{obj.path}</code></td>
                          <td>{obj.bytes}</td>
                          <td>
                            <button type="button" onClick={() => void handleDownload(obj.path)}>
                              Download
                            </button>
                            {obj.dataset === 'TOKEN_USAGE' && (
                              <button
                                type="button"
                                disabled={rehydrating}
                                onClick={() => void handleRehydrate(obj.path)}
                              >
                                Rehydrate
                              </button>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              )}
            </section>
          )}

          {partitionMaintenanceEnabled && (caps?.partitionMaintenance ?? false) && (
            <section className="security-panel">
              <h3>Hot data partitions</h3>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Dataset</th>
                    <th>Period</th>
                    <th>Status</th>
                    <th>Notes</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {(partitionsData?.hotDataPartitions ?? []).length === 0 ? (
                    <tr>
                      <td colSpan={5} className="empty">No partition records</td>
                    </tr>
                  ) : (
                    (partitionsData?.hotDataPartitions ?? []).map((part: {
                      id: string
                      dataset: string
                      periodStart: string
                      periodEnd: string
                      status: string
                      notes: string | null
                      createdAt: string
                    }) => (
                      <tr key={part.id}>
                        <td>{part.dataset}</td>
                        <td>{part.periodStart} → {part.periodEnd}</td>
                        <td>{part.status}</td>
                        <td>{part.notes ?? '—'}</td>
                        <td>{part.createdAt}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </section>
          )}
        </>
      )}
    </div>
  )
}
