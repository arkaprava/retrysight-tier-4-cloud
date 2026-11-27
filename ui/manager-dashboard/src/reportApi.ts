/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export type ReportPhaseOption = {
  phase: number
  label: string
  description: string
  enabled: boolean
}

export type ReportDatasetOption = {
  id: string
  label: string
  description: string
  phase: number
  available: boolean
  requiresPhase: number | null
  formats: string[]
  requiredFilters: string[]
  optionalFilters: string[]
}

export type ReportFormatOption = {
  id: string
  label: string
  description: string
  phase: number
  available: boolean
  requiresPhase: number | null
}

export type ReportOptions = {
  enabledPhases: number[]
  maxDateRangeDays: number
  phases: ReportPhaseOption[]
  datasets: ReportDatasetOption[]
  formats: ReportFormatOption[]
}

export type ReportFilters = {
  sourceTool?: string
  agentId?: string
  taskId?: string
}

export type ReportGenerateRequest = {
  dataset: string
  format: string
  from?: string
  to?: string
  filters?: ReportFilters
  phase?: number
}

function authHeaders(): HeadersInit {
  const token = localStorage.getItem('authToken')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function fetchReportOptions(): Promise<ReportOptions> {
  const response = await fetch('/api/v1/reports/options', {
    headers: authHeaders(),
  })

  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Failed to load report options (${response.status})`)
  }

  return response.json()
}

export async function generateReport(request: ReportGenerateRequest): Promise<{
  blob: Blob
  filename: string
}> {
  const response = await fetch('/api/v1/reports/generate', {
    method: 'POST',
    headers: {
      ...authHeaders(),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Report generation failed (${response.status})`)
  }

  const disposition = response.headers.get('Content-Disposition') ?? ''
  const filenameMatch = disposition.match(/filename="([^"]+)"/)
  const filename = filenameMatch?.[1] ?? 'report'

  return {
    blob: await response.blob(),
    filename,
  }
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

export function defaultDateRange(maxDays: number): { from: string; to: string } {
  const to = new Date()
  const from = new Date()
  const rangeDays = Math.min(7, maxDays)
  from.setDate(from.getDate() - rangeDays)
  return {
    from: from.toISOString().slice(0, 10),
    to: to.toISOString().slice(0, 10),
  }
}

export function toInstantStart(date: string): string {
  return new Date(`${date}T00:00:00.000Z`).toISOString()
}

export function toInstantEnd(date: string): string {
  return new Date(`${date}T23:59:59.999Z`).toISOString()
}
