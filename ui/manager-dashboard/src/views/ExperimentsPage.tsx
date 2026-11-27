/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMutation, useQuery } from '@apollo/client'
import { useState, type FormEvent } from 'react'
import {
  CREATE_EVAL_DATASET,
  CREATE_EVAL_EXPERIMENT,
  EVAL_DATASETS,
  EVAL_DEFINITIONS,
  EVAL_EXPERIMENTS,
  POPULATE_DATASET_FROM_FAILED,
  RUN_EVAL_EXPERIMENT,
} from '../graphql/client'
import { usePlatformFeatures } from '../platformFeatures'

export default function ExperimentsPage() {
  const { evalExperimentsEnabled, featuresLoading } = usePlatformFeatures()
  const [datasetName, setDatasetName] = useState('')
  const [expName, setExpName] = useState('')
  const [datasetId, setDatasetId] = useState('')
  const [baselineId, setBaselineId] = useState('')
  const [variantId, setVariantId] = useState('')

  const { data: datasetsData, refetch: refetchDatasets } = useQuery(EVAL_DATASETS, {
    skip: !evalExperimentsEnabled,
  })
  const { data: experimentsData, refetch: refetchExperiments } = useQuery(EVAL_EXPERIMENTS, {
    skip: !evalExperimentsEnabled,
  })
  const { data: definitionsData } = useQuery(EVAL_DEFINITIONS, {
    skip: !evalExperimentsEnabled,
  })

  const [createDataset] = useMutation(CREATE_EVAL_DATASET, { onCompleted: () => refetchDatasets() })
  const [populateFailed] = useMutation(POPULATE_DATASET_FROM_FAILED, { onCompleted: () => refetchDatasets() })
  const [createExperiment] = useMutation(CREATE_EVAL_EXPERIMENT, { onCompleted: () => refetchExperiments() })
  const [runExperiment, { loading: running }] = useMutation(RUN_EVAL_EXPERIMENT, {
    onCompleted: () => refetchExperiments(),
  })

  if (featuresLoading) {
    return (
      <div className="loading-state">
        <div className="loading-spinner" aria-hidden="true" />
        <span>Loading…</span>
      </div>
    )
  }

  if (!evalExperimentsEnabled) {
    return <p className="empty">Eval experiments require Tier 3+ with evaluations enabled.</p>
  }

  const datasets = datasetsData?.evalDatasets ?? []
  const experiments = experimentsData?.evalExperiments ?? []
  const definitions = definitionsData?.evalDefinitions ?? []

  async function handleCreateDataset(e: FormEvent) {
    e.preventDefault()
    if (!datasetName.trim()) return
    await createDataset({ variables: { name: datasetName.trim() } })
    setDatasetName('')
  }

  async function handleCreateExperiment(e: FormEvent) {
    e.preventDefault()
    if (!expName.trim() || !datasetId || !baselineId || !variantId) return
    await createExperiment({
      variables: {
        name: expName.trim(),
        datasetId,
        baselineDefinitionId: baselineId,
        variantDefinitionId: variantId,
      },
    })
    setExpName('')
  }

  return (
    <div className="analytics-surface">
      <header className="page-header">
        <h2 className="page-title">Eval Experiments</h2>
        <p className="page-subtitle">Compare evaluator definitions on curated datasets.</p>
      </header>

      <div className="card section-gap">
        <h3>Datasets</h3>
        <form className="inline-form" onSubmit={handleCreateDataset}>
          <input
            type="text"
            placeholder="Dataset name"
            value={datasetName}
            onChange={(e) => setDatasetName(e.target.value)}
          />
          <button type="submit" className="btn-primary">Create dataset</button>
        </form>
        {datasets.length === 0 ? (
          <p className="empty">No datasets yet.</p>
        ) : (
          <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Source</th>
                <th>Tasks</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {datasets.map((d: { id: string; name: string; source: string; taskCount: number }) => (
                <tr key={d.id}>
                  <td>{d.name}</td>
                  <td>{d.source}</td>
                  <td>{d.taskCount}</td>
                  <td>
                    <button
                      type="button"
                      className="btn-secondary btn-sm"
                      onClick={() => populateFailed({ variables: { datasetId: d.id } })}
                    >
                      Add failed evals
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </div>

      <div className="card section-gap">
        <h3>New experiment</h3>
        <form className="stack-form" onSubmit={handleCreateExperiment}>
          <input type="text" placeholder="Experiment name" value={expName} onChange={(e) => setExpName(e.target.value)} />
          <select value={datasetId} onChange={(e) => setDatasetId(e.target.value)}>
            <option value="">Select dataset</option>
            {datasets.map((d: { id: string; name: string }) => (
              <option key={d.id} value={d.id}>{d.name}</option>
            ))}
          </select>
          <select value={baselineId} onChange={(e) => setBaselineId(e.target.value)}>
            <option value="">Baseline definition</option>
            {definitions.map((d: { id: string; name: string }) => (
              <option key={d.id} value={d.id}>{d.name}</option>
            ))}
          </select>
          <select value={variantId} onChange={(e) => setVariantId(e.target.value)}>
            <option value="">Variant definition</option>
            {definitions.map((d: { id: string; name: string }) => (
              <option key={d.id} value={d.id}>{d.name}</option>
            ))}
          </select>
          <button type="submit" className="btn-primary">Create experiment</button>
        </form>
      </div>

      <div className="card section-gap">
        <h3>Experiments</h3>
        {experiments.length === 0 ? (
          <p className="empty">No experiments yet.</p>
        ) : (
          <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Status</th>
                <th>Baseline pass</th>
                <th>Variant pass</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {experiments.map((exp: {
                id: string
                name: string
                status: string
                comparison?: { baselinePassRate: number; variantPassRate: number } | null
              }) => (
                <tr key={exp.id}>
                  <td>{exp.name}</td>
                  <td>{exp.status}</td>
                  <td>{exp.comparison ? `${(exp.comparison.baselinePassRate * 100).toFixed(0)}%` : '—'}</td>
                  <td>{exp.comparison ? `${(exp.comparison.variantPassRate * 100).toFixed(0)}%` : '—'}</td>
                  <td>
                    <button
                      type="button"
                      className="btn-primary btn-sm"
                      disabled={running || exp.status === 'RUNNING'}
                      onClick={() => runExperiment({ variables: { experimentId: exp.id } })}
                    >
                      {exp.status === 'RUNNING' ? 'Running…' : 'Run'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </div>
    </div>
  )
}
