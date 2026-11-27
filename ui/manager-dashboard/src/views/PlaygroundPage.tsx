/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMutation, useQuery } from '@apollo/client'
import { useState, type FormEvent } from 'react'
import { EVAL_DEFINITIONS, RUN_PLAYGROUND_EVAL } from '../graphql/client'
import { usePlatformFeatures } from '../platformFeatures'

function evalLabelClass(label?: string | null): string {
  if (label === 'PASS') return 'eval-badge pass'
  if (label === 'FAIL') return 'eval-badge fail'
  if (label === 'WARN') return 'eval-badge warn'
  return 'eval-badge muted'
}

function formatScore(score: unknown): string {
  const n = typeof score === 'number' ? score : Number(score)
  return Number.isFinite(n) ? n.toFixed(1) : '—'
}

export default function PlaygroundPage() {
  const { evaluationsEnabled, featuresLoading } = usePlatformFeatures()
  const [taskId, setTaskId] = useState('')
  const [definitionId, setDefinitionId] = useState('')
  const [prompt, setPrompt] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<{
    label: string
    score?: number | null
    explanation?: string | null
  } | null>(null)

  const { data: definitionsData, loading: defsLoading } = useQuery(EVAL_DEFINITIONS, {
    skip: !evaluationsEnabled,
  })
  const [runEval, { loading: running }] = useMutation(RUN_PLAYGROUND_EVAL)

  if (featuresLoading) {
    return (
      <div className="loading-state">
        <div className="loading-spinner" aria-hidden="true" />
        <span>Loading…</span>
      </div>
    )
  }

  if (!evaluationsEnabled) {
    return <p className="empty">Evaluations are not enabled for this installation.</p>
  }

  const definitions = definitionsData?.evalDefinitions ?? []

  async function handleRun(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setResult(null)
    if (!taskId.trim() || !definitionId) return
    try {
      const { data } = await runEval({
        variables: {
          taskId: taskId.trim(),
          definitionId,
          promptTemplate: prompt.trim() || null,
        },
      })
      setResult(data?.runPlaygroundEval ?? null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Evaluation failed')
    }
  }

  return (
    <div className="analytics-surface">
      <header className="page-header">
        <h2 className="page-title">Eval Playground</h2>
        <p className="page-subtitle">Test evaluator prompts against a single task without persisting a definition.</p>
      </header>

      <div className="card section-gap">
        <form className="stack-form" onSubmit={handleRun}>
          <label>
            Task ID
            <input type="text" value={taskId} onChange={(e) => setTaskId(e.target.value)} placeholder="UUID" />
          </label>
          <label>
            Evaluator
            <select value={definitionId} onChange={(e) => setDefinitionId(e.target.value)} disabled={defsLoading}>
              <option value="">Select definition</option>
              {definitions.map((d: { id: string; name: string; kind: string }) => (
                <option key={d.id} value={d.id}>{d.name} ({d.kind})</option>
              ))}
            </select>
          </label>
          <label>
            Prompt override (optional)
            <textarea
              rows={8}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Leave blank to use the definition template"
            />
          </label>
          <button type="submit" className="btn-primary" disabled={running || defsLoading}>
            {running ? 'Running…' : 'Run evaluation'}
          </button>
        </form>
        {error && <p className="login-error">{error}</p>}
      </div>

      {result && (
        <div className="card section-gap">
          <h3>Result</h3>
          <p>
            <span className={evalLabelClass(result.label)}>{result.label}</span>
            {result.score != null && <span> Score: {formatScore(result.score)}</span>}
          </p>
          {result.explanation && <p className="eval-explanation">{result.explanation}</p>}
        </div>
      )}
    </div>
  )
}
