/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMutation, useQuery } from '@apollo/client'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ANNOTATION_QUEUE,
  COMPLETE_ANNOTATION,
  CREATE_EVAL_SCORECARD,
  ENQUEUE_ANNOTATION,
  EVAL_DEFINITIONS,
  EVAL_RUNS,
  EVAL_SCORECARDS,
  RUN_TASK_EVALUATIONS,
  UPDATE_EVAL_DEFINITION,
} from '../graphql/client'
import { usePlatformFeatures } from '../platformFeatures'

type EvalRun = {
  id: string
  label?: string | null
  score?: number | null
  explanation?: string | null
  status: string
  triggeredBy: string
  createdAt: string
  definition?: { name: string; slug: string; kind: string } | null
}

type EvalScorecard = {
  id: string
  score: number
  notes?: string | null
  createdBy: string
  createdAt: string
}

type Props = {
  taskId: string
}

function labelClass(label?: string | null): string {
  if (label === 'PASS') return 'eval-badge pass'
  if (label === 'FAIL') return 'eval-badge fail'
  if (label === 'WARN') return 'eval-badge warn'
  return 'eval-badge muted'
}

export default function EvaluationsPanel({ taskId }: Props) {
  const { evaluationsEnabled } = usePlatformFeatures()
  const [score, setScore] = useState('80')
  const [notes, setNotes] = useState('')

  const { data, loading, refetch } = useQuery(EVAL_RUNS, {
    variables: { taskId },
    skip: !evaluationsEnabled,
  })
  const { data: scorecardData, refetch: refetchScorecards } = useQuery(EVAL_SCORECARDS, {
    variables: { taskId },
    skip: !evaluationsEnabled,
  })

  const [runEvals, { loading: running }] = useMutation(RUN_TASK_EVALUATIONS, {
    onCompleted: () => refetch(),
  })
  const [createScorecard, { loading: savingScore }] = useMutation(CREATE_EVAL_SCORECARD, {
    onCompleted: () => {
      refetchScorecards()
      setNotes('')
    },
  })
  const [enqueue] = useMutation(ENQUEUE_ANNOTATION)

  if (!evaluationsEnabled) {
    return null
  }

  const runs: EvalRun[] = data?.evalRuns ?? []
  const scorecards: EvalScorecard[] = scorecardData?.evalScorecards ?? []

  async function handleRunEvals() {
    await runEvals({ variables: { taskId } })
  }

  async function handleScorecard(e: React.FormEvent) {
    e.preventDefault()
    const parsed = Number(score)
    if (Number.isNaN(parsed)) return
    await createScorecard({ variables: { taskId, score: parsed, notes: notes || null } })
  }

  async function handleEnqueueReview() {
    await enqueue({ variables: { taskId, reason: 'MANUAL' } })
  }

  return (
    <div className="card section-gap evaluations-panel">
      <div className="evaluations-header">
        <h3>Evaluations</h3>
        <div className="evaluations-actions">
          <button type="button" className="btn-secondary" onClick={handleEnqueueReview}>
            Queue for review
          </button>
          <button type="button" className="btn-primary" onClick={handleRunEvals} disabled={running}>
            {running ? 'Running…' : 'Run evaluators'}
          </button>
        </div>
      </div>

      {loading ? (
        <p className="empty">Loading evaluation results…</p>
      ) : runs.length === 0 ? (
        <p className="empty">No automated evaluations yet. Run evaluators or wait for session completion.</p>
      ) : (
        <ul className="eval-runs-list">
          {runs.map((run) => (
            <li key={run.id} className="eval-run-row">
              <div className="eval-run-title">
                <span className={labelClass(run.label)}>{run.label ?? run.status}</span>
                <strong>{run.definition?.name ?? 'Evaluator'}</strong>
                {run.score != null && <span className="eval-run-score">{run.score.toFixed(0)}</span>}
              </div>
              {run.explanation && <p className="eval-run-explanation">{run.explanation}</p>}
              <span className="eval-run-meta">
                {run.triggeredBy.toLowerCase()} · {new Date(run.createdAt).toLocaleString()}
              </span>
            </li>
          ))}
        </ul>
      )}

      <div className="eval-scorecard-section">
        <h4>Human scorecard</h4>
        <form className="eval-scorecard-form" onSubmit={handleScorecard}>
          <label>
            Score (0–100)
            <input
              type="number"
              min={0}
              max={100}
              value={score}
              onChange={(e) => setScore(e.target.value)}
            />
          </label>
          <label>
            Notes
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
          </label>
          <button type="submit" className="btn-secondary" disabled={savingScore}>
            {savingScore ? 'Saving…' : 'Save scorecard'}
          </button>
        </form>
        {scorecards.length > 0 && (
          <ul className="eval-scorecards-list">
            {scorecards.map((s) => (
              <li key={s.id}>
                <strong>{s.score}</strong> by {s.createdBy}
                {s.notes && <> — {s.notes}</>}
                <span className="muted"> · {new Date(s.createdAt).toLocaleString()}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

export function EvaluationsAdminSection() {
  const { evaluationsEnabled } = usePlatformFeatures()
  const { data, refetch } = useQuery(EVAL_DEFINITIONS, { skip: !evaluationsEnabled })
  const [updateDef] = useMutation(UPDATE_EVAL_DEFINITION, { onCompleted: () => refetch() })

  if (!evaluationsEnabled) {
    return <p className="empty">Evaluations are disabled for this installation tier.</p>
  }

  const defs = data?.evalDefinitions ?? []

  return (
    <div className="card">
      <h3>Eval definitions</h3>
      <p className="report-section-hint">
        Managed evaluators run automatically on session completion. Enable the LLM judge only when Ollama is available.
      </p>
      <ul className="eval-definitions-list">
        {defs.map((def: { id: string; name: string; slug: string; kind: string; enabled: boolean }) => (
          <li key={def.id} className="eval-definition-row">
            <div>
              <strong>{def.name}</strong>
              <span className="muted"> · {def.slug} · {def.kind}</span>
            </div>
            <button
              type="button"
              className={def.enabled ? 'btn-secondary' : 'btn-primary'}
              onClick={() => updateDef({ variables: { id: def.id, enabled: !def.enabled } })}
            >
              {def.enabled ? 'Disable' : 'Enable'}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

export function AnnotationQueuePage() {
  const { evaluationsEnabled } = usePlatformFeatures()
  const { data, loading, refetch } = useQuery(ANNOTATION_QUEUE, {
    variables: { status: 'OPEN' },
    skip: !evaluationsEnabled,
    pollInterval: 20000,
  })
  const [complete] = useMutation(COMPLETE_ANNOTATION, { onCompleted: () => refetch() })

  if (!evaluationsEnabled) {
    return <p className="empty">Annotation queue requires evaluations (Tier 2+).</p>
  }

  const items = data?.annotationQueue ?? []

  return (
    <>
      <header className="page-header">
        <h2 className="page-title">Annotation queue</h2>
        <p className="page-subtitle">Sessions flagged for human review</p>
      </header>
      {loading ? (
        <p className="empty">Loading queue…</p>
      ) : items.length === 0 ? (
        <p className="empty">No open review items.</p>
      ) : (
        <div className="card">
          <ul className="annotation-queue-list">
            {items.map((item: {
              id: string
              taskId: string
              reason: string
              createdAt: string
              createdBy?: string | null
            }) => (
              <li key={item.id} className="annotation-queue-row">
                <div>
                  <Link to={`/tasks/${item.taskId}`}>Review task</Link>
                  <span className="muted"> · {item.reason} · {new Date(item.createdAt).toLocaleString()}</span>
                </div>
                <div className="annotation-queue-actions">
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => complete({ variables: { id: item.id, status: 'SKIPPED', notes: null } })}
                  >
                    Skip
                  </button>
                  <button
                    type="button"
                    className="btn-primary"
                    onClick={() => complete({ variables: { id: item.id, status: 'DONE', notes: null } })}
                  >
                    Done
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </>
  )
}
