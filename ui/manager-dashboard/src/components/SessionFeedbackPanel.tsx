/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMutation, useQuery } from '@apollo/client'
import { useState } from 'react'
import { SESSION_FEEDBACK, SUBMIT_SESSION_FEEDBACK } from '../graphql/client'
import { usePlatformFeatures } from '../platformFeatures'

type Props = {
  taskId: string
}

export default function SessionFeedbackPanel({ taskId }: Props) {
  const { evaluationsEnabled } = usePlatformFeatures()
  const [comment, setComment] = useState('')

  const { data, refetch } = useQuery(SESSION_FEEDBACK, {
    variables: { taskId },
    skip: !evaluationsEnabled,
  })
  const [submitFeedback, { loading }] = useMutation(SUBMIT_SESSION_FEEDBACK, {
    onCompleted: () => {
      refetch()
      setComment('')
    },
  })

  if (!evaluationsEnabled) {
    return null
  }

  const feedback = data?.sessionFeedback ?? []

  async function handleRating(rating: number) {
    await submitFeedback({
      variables: { taskId, rating, comment: comment.trim() || null },
    })
  }

  return (
    <div className="card section-gap">
      <h3>Session Feedback</h3>
      <div className="feedback-actions">
        <button type="button" className="btn-secondary" disabled={loading} onClick={() => handleRating(1)}>
          👍 Helpful
        </button>
        <button type="button" className="btn-secondary" disabled={loading} onClick={() => handleRating(-1)}>
          👎 Not helpful
        </button>
      </div>
      <input
        type="text"
        placeholder="Optional comment"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        className="feedback-comment"
      />
      {feedback.length > 0 && (
        <ul className="feedback-list">
          {feedback.map((item: {
            id: string
            rating: number
            comment?: string | null
            createdBy: string
            createdAt: string
          }) => (
            <li key={item.id}>
              <span>{item.rating > 0 ? '👍' : item.rating < 0 ? '👎' : '—'}</span>
              {item.comment && <span> {item.comment}</span>}
              <span className="muted"> — {item.createdBy}, {new Date(item.createdAt).toLocaleString()}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
