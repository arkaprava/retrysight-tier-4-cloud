/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@apollo/client'
import { AGENTS_QUERY } from '../graphql/client'
import {
  defaultDateRange,
  downloadBlob,
  fetchReportOptions,
  generateReport,
  toInstantEnd,
  toInstantStart,
  type ReportDatasetOption,
  type ReportFormatOption,
  type ReportOptions,
} from '../reportApi'

import { formatSourceTool, useSourceTools } from '../sourceTools'

type WizardStep = 1 | 2 | 3 | 4

type AgentRow = {
  id: string
  hostname: string
  developerEmail: string
}

function LoadingState({ label }: { label: string }) {
  return (
    <div className="loading-state">
      <div className="loading-spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

function phaseBadge(phase: number) {
  return <span className="report-phase-badge">Phase {phase}</span>
}

export default function ReportsPage() {
  const [options, setOptions] = useState<ReportOptions | null>(null)
  const [optionsError, setOptionsError] = useState<string | null>(null)
  const [loadingOptions, setLoadingOptions] = useState(true)

  const [step, setStep] = useState<WizardStep>(1)
  const [selectedDatasetId, setSelectedDatasetId] = useState<string | null>(null)
  const [selectedFormatId, setSelectedFormatId] = useState<string | null>(null)
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [sourceTool, setSourceTool] = useState('')
  const [agentId, setAgentId] = useState('')
  const [taskId, setTaskId] = useState('')
  const [generating, setGenerating] = useState(false)
  const [generateError, setGenerateError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const { data: agentsData } = useQuery(AGENTS_QUERY)
  const agents: AgentRow[] = agentsData?.agents ?? []
  const { enabledSourceTools } = useSourceTools()
  const sourceToolOptions = enabledSourceTools.length > 0
    ? enabledSourceTools
    : [{ id: 'CURSOR', label: 'Cursor' }]

  useEffect(() => {
    let cancelled = false

    async function loadOptions() {
      setLoadingOptions(true)
      setOptionsError(null)
      try {
        const loaded = await fetchReportOptions()
        if (!cancelled) {
          setOptions(loaded)
          const defaults = defaultDateRange(loaded.maxDateRangeDays)
          setFromDate(defaults.from)
          setToDate(defaults.to)
        }
      } catch (error) {
        if (!cancelled) {
          setOptionsError(error instanceof Error ? error.message : 'Failed to load report options')
        }
      } finally {
        if (!cancelled) {
          setLoadingOptions(false)
        }
      }
    }

    void loadOptions()
    return () => {
      cancelled = true
    }
  }, [])

  const selectedDataset = useMemo(
    () => options?.datasets.find((dataset) => dataset.id === selectedDatasetId) ?? null,
    [options, selectedDatasetId],
  )

  const availableFormats = useMemo(() => {
    if (!options || !selectedDataset) return []
    return options.formats.filter((format) => {
      if (!selectedDataset.formats.includes(format.id)) return false
      if (!format.available) return false
      return format.phase <= selectedDataset.phase || selectedDataset.phase >= format.phase
    })
  }, [options, selectedDataset])

  const enabledPhaseSummary = useMemo(() => {
    if (!options) return ''
    return options.phases
      .filter((phase) => phase.enabled)
      .map((phase) => phase.label)
      .join(', ')
  }, [options])

  const disabledPhaseMessage = useMemo(() => {
    if (!options) return null
    const disabled = options.phases.filter((phase) => !phase.enabled)
    if (disabled.length === 0) return null
    return `Administrator has disabled: ${disabled.map((phase) => phase.label).join(', ')}. Scheduled and advanced features are hidden.`
  }, [options])

  function selectDataset(dataset: ReportDatasetOption) {
    if (!dataset.available) return
    setSelectedDatasetId(dataset.id)
    setSelectedFormatId(null)
    setGenerateError(null)
    setSuccessMessage(null)
  }

  function selectFormat(format: ReportFormatOption) {
    if (!format.available) return
    setSelectedFormatId(format.id)
    setGenerateError(null)
    setSuccessMessage(null)
  }

  function canProceedFromStep(current: WizardStep): boolean {
    switch (current) {
      case 1:
        return selectedDataset !== null
      case 2:
        return selectedFormatId !== null
      case 3:
        if (!selectedDataset) return false
        if (selectedDataset.requiredFilters.includes('taskId') && !taskId.trim()) return false
        if (selectedDataset.requiredFilters.includes('from') && !fromDate) return false
        if (selectedDataset.requiredFilters.includes('to') && !toDate) return false
        return true
      default:
        return true
    }
  }

  async function handleGenerate() {
    if (!selectedDataset || !selectedFormatId) return

    setGenerating(true)
    setGenerateError(null)
    setSuccessMessage(null)

    try {
      const filters: { sourceTool?: string; agentId?: string; taskId?: string } = {}
      if (sourceTool) filters.sourceTool = sourceTool
      if (agentId) filters.agentId = agentId
      if (taskId.trim()) filters.taskId = taskId.trim()

      const request = {
        dataset: selectedDataset.id,
        format: selectedFormatId,
        phase: selectedDataset.phase,
        ...(fromDate ? { from: toInstantStart(fromDate) } : {}),
        ...(toDate ? { to: toInstantEnd(toDate) } : {}),
        ...(Object.keys(filters).length > 0 ? { filters } : {}),
      }

      const { blob, filename } = await generateReport(request)
      downloadBlob(blob, filename)
      setSuccessMessage(`Downloaded ${filename}`)
    } catch (error) {
      setGenerateError(error instanceof Error ? error.message : 'Report generation failed')
    } finally {
      setGenerating(false)
    }
  }

  if (loadingOptions) return <LoadingState label="Loading report options…" />
  if (optionsError || !options) {
    return <p className="empty">Failed to load reports: {optionsError ?? 'Unknown error'}</p>
  }

  const stepLabels = ['Report type', 'Format', 'Scope', 'Generate']

  return (
    <>
      <header className="page-header">
        <h2 className="page-title">Reports</h2>
        <p className="page-subtitle">
          Export task data and dashboard metrics. Enabled: {enabledPhaseSummary}.
        </p>
      </header>

      {disabledPhaseMessage && (
        <div className="report-config-notice" role="status">
          {disabledPhaseMessage}
        </div>
      )}

      <div className="report-wizard">
        <ol className="report-steps" aria-label="Report wizard steps">
          {stepLabels.map((label, index) => {
            const stepNumber = (index + 1) as WizardStep
            const isActive = step === stepNumber
            const isComplete = step > stepNumber
            return (
              <li
                key={label}
                className={`report-step${isActive ? ' active' : ''}${isComplete ? ' complete' : ''}`}
                aria-current={isActive ? 'step' : undefined}
              >
                <span className="report-step-number">{stepNumber}</span>
                <span className="report-step-label">{label}</span>
              </li>
            )
          })}
        </ol>

        <div className="card report-panel">
          {step === 1 && (
            <section className="report-section">
              <h3>Choose report type</h3>
              <p className="report-section-hint">Select a dataset. Options reflect administrator configuration.</p>
              <div className="report-dataset-grid">
                {options.datasets.map((dataset) => (
                  <button
                    key={dataset.id}
                    type="button"
                    className={`report-dataset-card${
                      selectedDatasetId === dataset.id ? ' selected' : ''
                    }${dataset.available ? '' : ' unavailable'}`}
                    onClick={() => selectDataset(dataset)}
                    disabled={!dataset.available}
                    aria-pressed={selectedDatasetId === dataset.id}
                  >
                    <div className="report-dataset-card-header">
                      <strong>{dataset.label}</strong>
                      {phaseBadge(dataset.phase)}
                    </div>
                    <p>{dataset.description}</p>
                    {!dataset.available && (
                      <span className="report-coming-soon">
                        Coming soon
                        {dataset.requiresPhase ? ` (Phase ${dataset.requiresPhase})` : ''}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </section>
          )}

          {step === 2 && selectedDataset && (
            <section className="report-section">
              <h3>Choose format</h3>
              <p className="report-section-hint">
                Formats available for <strong>{selectedDataset.label}</strong>.
              </p>
              <div className="report-format-list">
                {availableFormats.length === 0 ? (
                  <p className="empty">No formats available for this dataset with current configuration.</p>
                ) : (
                  availableFormats.map((format) => (
                    <button
                      key={format.id}
                      type="button"
                      className={`report-format-card${
                        selectedFormatId === format.id ? ' selected' : ''
                      }`}
                      onClick={() => selectFormat(format)}
                      aria-pressed={selectedFormatId === format.id}
                    >
                      <div className="report-format-card-header">
                        <strong>{format.label}</strong>
                        {phaseBadge(format.phase)}
                      </div>
                      <p>{format.description}</p>
                    </button>
                  ))
                )}
                {options.formats
                  .filter(
                    (format) =>
                      selectedDataset.formats.includes(format.id) && !format.available,
                  )
                  .map((format) => (
                    <div key={format.id} className="report-format-card unavailable" aria-disabled="true">
                      <div className="report-format-card-header">
                        <strong>{format.label}</strong>
                        {phaseBadge(format.phase)}
                      </div>
                      <p>{format.description}</p>
                      <span className="report-coming-soon">
                        Requires Phase {format.requiresPhase ?? format.phase}
                      </span>
                    </div>
                  ))}
              </div>
            </section>
          )}

          {step === 3 && selectedDataset && (
            <section className="report-section">
              <h3>Define scope</h3>
              <p className="report-section-hint">
                Maximum date range: {options.maxDateRangeDays} days.
              </p>
              <div className="report-filters">
                {(selectedDataset.requiredFilters.includes('from')
                  || selectedDataset.optionalFilters.includes('from')) && (
                  <label>
                    From date
                    {selectedDataset.requiredFilters.includes('from') && (
                      <span className="report-required">*</span>
                    )}
                    <input
                      type="date"
                      value={fromDate}
                      onChange={(event) => setFromDate(event.target.value)}
                      required={selectedDataset.requiredFilters.includes('from')}
                    />
                  </label>
                )}
                {(selectedDataset.requiredFilters.includes('to')
                  || selectedDataset.optionalFilters.includes('to')) && (
                  <label>
                    To date
                    {selectedDataset.requiredFilters.includes('to') && (
                      <span className="report-required">*</span>
                    )}
                    <input
                      type="date"
                      value={toDate}
                      onChange={(event) => setToDate(event.target.value)}
                      required={selectedDataset.requiredFilters.includes('to')}
                    />
                  </label>
                )}
                {(selectedDataset.requiredFilters.includes('taskId')
                  || selectedDataset.optionalFilters.includes('taskId')) && (
                  <label>
                    Task ID
                    {selectedDataset.requiredFilters.includes('taskId') && (
                      <span className="report-required">*</span>
                    )}
                    <input
                      type="text"
                      value={taskId}
                      onChange={(event) => setTaskId(event.target.value)}
                      placeholder="UUID of the task"
                      required={selectedDataset.requiredFilters.includes('taskId')}
                    />
                  </label>
                )}
                {(selectedDataset.requiredFilters.includes('sourceTool')
                  || selectedDataset.optionalFilters.includes('sourceTool')) && (
                  <label>
                    Source tool
                    <select value={sourceTool} onChange={(event) => setSourceTool(event.target.value)}>
                      <option value="">All tools</option>
                      {sourceToolOptions.map((tool) => (
                        <option key={tool.id} value={tool.id}>
                          {formatSourceTool(tool.id, enabledSourceTools)}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                {(selectedDataset.requiredFilters.includes('agentId')
                  || selectedDataset.optionalFilters.includes('agentId')) && (
                  <label>
                    Collector
                    <select value={agentId} onChange={(event) => setAgentId(event.target.value)}>
                      <option value="">All collectors</option>
                      {agents.map((agent) => (
                        <option key={agent.id} value={agent.id}>
                          {agent.hostname} ({agent.developerEmail})
                        </option>
                      ))}
                    </select>
                  </label>
                )}
              </div>
            </section>
          )}

          {step === 4 && selectedDataset && selectedFormatId && (
            <section className="report-section">
              <h3>Generate report</h3>
              <div className="report-summary">
                <div><span>Type</span><strong>{selectedDataset.label}</strong></div>
                <div><span>Format</span><strong>{selectedFormatId}</strong></div>
                <div><span>Phase</span><strong>Phase {selectedDataset.phase}</strong></div>
                {fromDate && <div><span>From</span><strong>{fromDate}</strong></div>}
                {toDate && <div><span>To</span><strong>{toDate}</strong></div>}
                {taskId && <div><span>Task</span><strong>{taskId}</strong></div>}
                {sourceTool && <div><span>Tool</span><strong>{sourceTool}</strong></div>}
                {agentId && (
                  <div>
                    <span>Collector</span>
                    <strong>{agents.find((agent) => agent.id === agentId)?.hostname ?? agentId}</strong>
                  </div>
                )}
              </div>

              {generateError && <p className="report-error">{generateError}</p>}
              {successMessage && <p className="report-success">{successMessage}</p>}

              <button
                type="button"
                className="report-generate-btn"
                onClick={() => void handleGenerate()}
                disabled={generating}
              >
                {generating ? 'Generating…' : 'Download report'}
              </button>
            </section>
          )}

          <div className="report-wizard-nav">
            <button
              type="button"
              className="report-nav-btn"
              onClick={() => setStep((current) => Math.max(1, current - 1) as WizardStep)}
              disabled={step === 1}
            >
              Back
            </button>
            {step < 4 ? (
              <button
                type="button"
                className="report-nav-btn primary"
                onClick={() => setStep((current) => Math.min(4, current + 1) as WizardStep)}
                disabled={!canProceedFromStep(step)}
              >
                Next
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </>
  )
}
