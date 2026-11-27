/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMemo } from 'react'
import { rollingWeekRanges } from '../utils/metricDelta'

export const DASHBOARD_POLL_MS = 15_000

/** Fixed prior-week window (computed once per mount) for WoW comparison. */
export function useStablePreviousWeekRange() {
  return useMemo(() => rollingWeekRanges(Date.now()).previous, [])
}
