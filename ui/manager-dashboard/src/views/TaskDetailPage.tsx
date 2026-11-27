/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useQuery } from '@apollo/client'
import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import MetricCard from '../components/analytics/MetricCard'
import MetricGrid from '../components/analytics/MetricGrid'
import ContextUsageSection from '../components/ContextUsageSection'
import ProjectContextCard from '../components/ProjectContextCard'
import TaskEventTimeline, { type TimelineFilter } from '../components/TaskEventTimeline'
import TaskSessionReplayPanel from '../components/TaskSessionReplayPanel'
import { TASK_DETAIL } from '../graphql/client'
import EvaluationsPanel from '../components/EvaluationsPanel'
import SessionFeedbackPanel from '../components/SessionFeedbackPanel'
import { usePlatformFeatures } from '../platformFeatures'
import { parseTaskMetadata } from '../utils/contextUsage'

function LoadingState({ label }: { label: string }) {
  return (
    <div className="loading-state">
      <div className="loading-spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

export default function TaskDetailPage() {
  const { id } = useParams()
  const { gitContextCaptureEnabled, evaluationsEnabled } = usePlatformFeatures()
  const { data, loading, error } = useQuery(TASK_DETAIL, {
    variables: { id },
    skip: !id,
  })
  const [timelineFilter, setTimelineFilter] = useState<TimelineFilter>('all')
  const [timelineOpen, setTimelineOpen] = useState(true)
  const timelineRef = useRef<HTMLDivElement>(null)

  if (loading) return <LoadingState label="Loading task…" />
  if (error) return <p className="empty">Failed to load task: {error.message}</p>
  if (!data?.task) return <p className="empty">Task not found.</p>

  const task = data.task
  const taskMetadata = parseTaskMetadata(task.metadata)

  function focusRetryTimeline() {
    setTimelineFilter('retries')
    setTimelineOpen(true)
    timelineRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <div className="analytics-surface">
      <Link to="/tasks" className="back-link">← Back to tasks</Link>
      <header className="page-header">
        <h2 className="page-title task-description">{task.description}</h2>
      </header>

      <ContextUsageSection
        metadata={taskMetadata}
        tokenUsage={task.tokenUsage}
        tokenTurns={task.tokenTurns}
        timeline={task.timeline}
      />

      {gitContextCaptureEnabled && <ProjectContextCard projectContext={task.projectContext} />}

      <MetricGrid className="section-gap">
        <button
          type="button"
          className="metric-card-button"
          onClick={focusRetryTimeline}
          title="Show only retries in the event timeline"
        >
          <MetricCard
            title="Retries"
            value={task.retryCount.toLocaleString()}
            subtitle="Click to filter timeline"
            variant={task.retryCount > 0 ? 'alert' : 'default'}
          />
        </button>
        <MetricCard
          title="Started"
          value={new Date(task.startedAt).toLocaleString()}
          subtitle="Task start time"
        />
        {task.completedAt && (
          <MetricCard
            title="Completed"
            value={new Date(task.completedAt).toLocaleString()}
            subtitle="Task completion time"
          />
        )}
        <MetricCard
          title="Est. Cost"
          value={
            task.tokenUsage.estimatedCostUsd > 0
              ? `$${task.tokenUsage.estimatedCostUsd.toFixed(4)}`
              : '—'
          }
        />
      </MetricGrid>

      <div className="card section-gap" ref={timelineRef} id="task-event-timeline">
        <h3>Event Timeline</h3>
        <TaskEventTimeline
          events={task.timeline}
          filter={timelineFilter}
          onFilterChange={setTimelineFilter}
          open={timelineOpen}
          onOpenChange={setTimelineOpen}
          defaultOpen
        />
      </div>

      <TaskSessionReplayPanel
        taskId={task.id}
        spans={task.spanTree}
        events={task.timeline}
      />

      {evaluationsEnabled && <EvaluationsPanel taskId={task.id} />}
      {evaluationsEnabled && <SessionFeedbackPanel taskId={task.id} />}
    </div>
  )
}
