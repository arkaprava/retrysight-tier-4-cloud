/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useCallback, useEffect, useMemo, useState } from 'react'

export type ChartWindowAlign = 'start' | 'end' | 'center'

const DEFAULT_MIN_VISIBLE = 3
const ZOOM_RATIO = 0.25

export type UseChartZoomOptions = {
  minVisible?: number
  align?: ChartWindowAlign
}

export function useChartZoom<T>(
  data: T[],
  { minVisible = DEFAULT_MIN_VISIBLE, align = 'center' }: UseChartZoomOptions = {},
) {
  const dataLength = data.length
  const [window, setWindow] = useState({ start: 0, end: dataLength })

  useEffect(() => {
    setWindow({ start: 0, end: dataLength })
  }, [dataLength])

  const visibleCount = window.end - window.start

  const zoomedData = useMemo(
    () => data.slice(window.start, window.end),
    [data, window.start, window.end],
  )

  const canZoomIn = visibleCount > minVisible
  const canZoomOut = window.start > 0 || window.end < dataLength
  const canReset = window.start !== 0 || window.end !== dataLength

  const zoomIn = useCallback(() => {
    setWindow((prev) => {
      const count = prev.end - prev.start
      if (count <= minVisible) return prev

      const remove = Math.max(1, Math.floor(count * ZOOM_RATIO))
      const trimTotal =
        align === 'center' ? Math.min(count - minVisible, remove * 2) : Math.min(count - minVisible, remove)
      const newCount = count - trimTotal

      if (align === 'end') {
        return { start: prev.end - newCount, end: prev.end }
      }
      if (align === 'start') {
        return { start: prev.start, end: prev.start + newCount }
      }

      const trimEach = Math.floor(trimTotal / 2)
      return {
        start: prev.start + trimEach,
        end: prev.end - (trimTotal - trimEach),
      }
    })
  }, [align, minVisible])

  const zoomOut = useCallback(() => {
    setWindow((prev) => {
      if (prev.start === 0 && prev.end === dataLength) return prev

      const count = prev.end - prev.start
      const expand = Math.max(1, Math.floor(count * ZOOM_RATIO))

      if (align === 'end') {
        return { start: Math.max(0, prev.start - expand), end: prev.end }
      }
      if (align === 'start') {
        return { start: prev.start, end: Math.min(dataLength, prev.end + expand) }
      }

      return {
        start: Math.max(0, prev.start - expand),
        end: Math.min(dataLength, prev.end + expand),
      }
    })
  }, [align, dataLength])

  const reset = useCallback(() => {
    setWindow({ start: 0, end: dataLength })
  }, [dataLength])

  return {
    zoomedData,
    zoomIn,
    zoomOut,
    reset,
    canZoomIn,
    canZoomOut,
    canReset,
    visibleCount,
    totalCount: dataLength,
  }
}
