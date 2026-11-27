/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMutation, useQuery } from '@apollo/client'
import { FormEvent, useState } from 'react'
import {
  COST_BUDGETS,
  CREATE_COST_BUDGET,
  DELETE_COST_BUDGET,
  UPDATE_COST_BUDGET,
} from '../graphql/client'

type CostBudgetRow = {
  id: string
  name: string
  scope: string
  scopeValue?: string | null
  period: string
  limitUsd: number
  alertThresholdPct: number
  active: boolean
  currentSpendUsd: number
}

const EMPTY_FORM = {
  name: '',
  scope: 'ORG',
  scopeValue: '',
  period: 'MONTHLY',
  limitUsd: '',
  alertThresholdPct: '80',
  active: true,
}

export default function CostBudgetsSection() {
  const { data, loading, refetch } = useQuery(COST_BUDGETS)
  const [createBudget] = useMutation(CREATE_COST_BUDGET, { onCompleted: () => refetch() })
  const [updateBudget] = useMutation(UPDATE_COST_BUDGET, { onCompleted: () => refetch() })
  const [deleteBudget] = useMutation(DELETE_COST_BUDGET, { onCompleted: () => refetch() })
  const [form, setForm] = useState(EMPTY_FORM)
  const [error, setError] = useState<string | null>(null)

  const budgets: CostBudgetRow[] = data?.costBudgets ?? []

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await createBudget({
        variables: {
          name: form.name,
          scope: form.scope,
          scopeValue: form.scope === 'ORG' ? null : form.scopeValue || null,
          period: form.period,
          limitUsd: Number(form.limitUsd),
          alertThresholdPct: Number(form.alertThresholdPct),
          active: form.active,
        },
      })
      setForm(EMPTY_FORM)
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Failed to create budget')
    }
  }

  return (
    <div className="card cost-budgets-section">
      <h3>Cost Budgets</h3>
      <p className="page-subtitle">SECADMIN: set spend limits and alert thresholds</p>

      <form className="cost-budget-form" onSubmit={handleSubmit}>
        <input
          placeholder="Budget name"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
          required
        />
        <select value={form.scope} onChange={(event) => setForm({ ...form, scope: event.target.value })}>
          <option value="ORG">Organization</option>
          <option value="USER">User</option>
          <option value="TOOL">Tool</option>
        </select>
        {form.scope !== 'ORG' && (
          <input
            placeholder={form.scope === 'USER' ? 'developer@email.com' : 'CURSOR'}
            value={form.scopeValue}
            onChange={(event) => setForm({ ...form, scopeValue: event.target.value })}
            required
          />
        )}
        <select value={form.period} onChange={(event) => setForm({ ...form, period: event.target.value })}>
          <option value="WEEKLY">Weekly</option>
          <option value="MONTHLY">Monthly</option>
        </select>
        <input
          type="number"
          min="0"
          step="0.01"
          placeholder="Limit USD"
          value={form.limitUsd}
          onChange={(event) => setForm({ ...form, limitUsd: event.target.value })}
          required
        />
        <input
          type="number"
          min="1"
          max="100"
          placeholder="Alert %"
          value={form.alertThresholdPct}
          onChange={(event) => setForm({ ...form, alertThresholdPct: event.target.value })}
        />
        <button type="submit">Create budget</button>
      </form>
      {error && <p className="empty">{error}</p>}

      {loading ? (
        <p className="empty">Loading budgets…</p>
      ) : budgets.length === 0 ? (
        <p className="empty">No budgets configured.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Scope</th>
                <th>Period</th>
                <th>Spend / Limit</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {budgets.map((budget) => (
                <tr key={budget.id}>
                  <td>{budget.name}</td>
                  <td>
                    {budget.scope}
                    {budget.scopeValue ? ` (${budget.scopeValue})` : ''}
                  </td>
                  <td>{budget.period}</td>
                  <td>
                    ${budget.currentSpendUsd.toFixed(2)} / ${budget.limitUsd.toFixed(2)}
                  </td>
                  <td>{budget.active ? 'Active' : 'Inactive'}</td>
                  <td className="cost-budget-actions">
                    <button
                      type="button"
                      onClick={() =>
                        updateBudget({
                          variables: { id: budget.id, active: !budget.active },
                        })
                      }
                    >
                      {budget.active ? 'Disable' : 'Enable'}
                    </button>
                    <button
                      type="button"
                      className="danger-btn"
                      onClick={() => deleteBudget({ variables: { id: budget.id } })}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
