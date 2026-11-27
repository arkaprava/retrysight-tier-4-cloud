/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

type ModelBadgeListProps = {
  models: string[]
  latestModel?: string | null
  className?: string
}

export default function ModelBadgeList({ models, latestModel, className }: ModelBadgeListProps) {
  const visible = models.length > 0
    ? models
    : latestModel
      ? [latestModel]
      : []

  if (visible.length === 0) {
    return <span className={`muted ${className ?? ''}`}>No model recorded</span>
  }

  return (
    <div className={`model-badge-list ${className ?? ''}`}>
      {visible.map((model) => (
        <span
          key={model}
          className={`context-badge model${latestModel === model ? ' latest-model' : ''}`}
          title={latestModel === model ? `${model} (latest turn)` : model}
        >
          {model}
        </span>
      ))}
    </div>
  )
}
