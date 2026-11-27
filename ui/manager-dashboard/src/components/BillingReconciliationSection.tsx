/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMutation, useQuery } from '@apollo/client'
import { FormEvent, useState } from 'react'
import MetricCard from './analytics/MetricCard'
import MetricGrid from './analytics/MetricGrid'
import { BILLING_RECONCILIATION, COST_FEATURES, FETCH_CURSOR_BILLING, IMPORT_BILLING_ACTUAL } from '../graphql/client'
import { toInstantEnd, toInstantStart } from '../reportApi'

type BillingReconciliationSectionProps = {
  fromDate: string
  toDate: string
}

export default function BillingReconciliationSection({ fromDate, toDate }: BillingReconciliationSectionProps) {
  const { data: featuresData } = useQuery(COST_FEATURES)
  const { data, loading, refetch } = useQuery(BILLING_RECONCILIATION, {
    variables: {
      from: fromDate ? toInstantStart(fromDate) : undefined,
      to: toDate ? toInstantEnd(toDate) : undefined,
    },
    skip: !featuresData?.costFeatures?.billingReconciliationAvailable,
  })
  const [importActual] = useMutation(IMPORT_BILLING_ACTUAL, { onCompleted: () => refetch() })
  const [fetchCursor] = useMutation(FETCH_CURSOR_BILLING, { onCompleted: () => refetch() })
  const [actualCostUsd, setActualCostUsd] = useState('')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)

  if (!featuresData?.costFeatures?.billingReconciliationAvailable) {
    return null
  }

  const reconciliation = data?.billingReconciliation

  async function handleImport(event: FormEvent) {
    event.preventDefault()
    if (!fromDate || !toDate) {
      setError('Select a date range before importing actuals.')
      return
    }
    setError(null)
    try {
      await importActual({
        variables: {
          periodStart: toInstantStart(fromDate),
          periodEnd: toInstantEnd(toDate),
          actualCostUsd: Number(actualCostUsd),
          notes: notes || null,
        },
      })
      setActualCostUsd('')
      setNotes('')
    } catch (importError) {
      setError(importError instanceof Error ? importError.message : 'Import failed')
    }
  }

  return (
    <div className="card cost-billing-section analytics-surface">
      <h3>Billing Reconciliation</h3>
      <p className="page-subtitle">Compare estimated token costs against imported billing actuals</p>

      {loading ? (
        <p className="empty">Loading reconciliation…</p>
      ) : reconciliation ? (
        <MetricGrid className="cost-reconciliation-stats">
          <MetricCard title="Estimated" value={`$${reconciliation.estimatedCostUsd.toFixed(2)}`} />
          <MetricCard title="Actual" value={`$${reconciliation.actualCostUsd.toFixed(2)}`} />
          <MetricCard title="Variance" value={`$${reconciliation.varianceUsd.toFixed(2)}`} variant="alert" />
          <MetricCard title="Variance %" value={`${reconciliation.variancePct.toFixed(1)}%`} variant="alert" />
        </MetricGrid>
      ) : (
        <p className="empty">No reconciliation data for this range.</p>
      )}

      <form className="cost-billing-form" onSubmit={handleImport}>
        <input
          type="number"
          min="0"
          step="0.01"
          placeholder="Actual cost USD"
          value={actualCostUsd}
          onChange={(event) => setActualCostUsd(event.target.value)}
          required
        />
        <input
          placeholder="Notes (optional)"
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
        />
        <button type="submit">Import billing actual</button>
      </form>
      {featuresData?.costFeatures?.billingApiEnabled && (
        <button
          type="button"
          onClick={async () => {
            if (!fromDate || !toDate) {
              setError('Select a date range before fetching Cursor billing.')
              return
            }
            setError(null)
            try {
              await fetchCursor({
                variables: {
                  periodStart: toInstantStart(fromDate),
                  periodEnd: toInstantEnd(toDate),
                },
              })
            } catch (fetchError) {
              setError(fetchError instanceof Error ? fetchError.message : 'Cursor fetch failed')
            }
          }}
        >
          Fetch from Cursor API
        </button>
      )}
      {error && <p className="empty">{error}</p>}
    </div>
  )
}
