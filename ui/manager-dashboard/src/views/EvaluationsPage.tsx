/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { AnnotationQueuePage, EvaluationsAdminSection } from '../components/EvaluationsPanel'
import { useAuth } from '../auth/AuthContext'

export default function EvaluationsPage() {
  const { isSecadmin } = useAuth()

  return (
    <div className="analytics-surface">
      <AnnotationQueuePage />
      {isSecadmin && (
        <div className="section-gap">
          <EvaluationsAdminSection />
        </div>
      )}
    </div>
  )
}
