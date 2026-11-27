/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import type { ReactNode } from 'react'

export type ProjectContextData = {
  projectRoot?: string | null
  repoName?: string | null
  gitBranch?: string | null
  gitCommit?: string | null
  gitCommitShort?: string | null
  remoteUrl?: string | null
  remoteHost?: string | null
  isDirty?: boolean | null
  resolvedFrom?: string | null
  isSubmodule?: boolean | null
  isWorktree?: boolean | null
}

type ProjectContextCardProps = {
  projectContext?: ProjectContextData | null
}

function Field({ label, value }: { label: string; value?: ReactNode }) {
  if (value == null || value === '') return null
  return (
    <div className="project-context-field">
      <span className="project-context-label">{label}</span>
      <span className="project-context-value">{value}</span>
    </div>
  )
}

export default function ProjectContextCard({ projectContext }: ProjectContextCardProps) {
  if (!projectContext) return null

  const hasContent = Boolean(
    projectContext.repoName
      || projectContext.gitBranch
      || projectContext.gitCommitShort
      || projectContext.projectRoot
      || projectContext.remoteHost,
  )
  if (!hasContent) return null

  return (
    <div className="card section-gap">
      <h3>Project</h3>
      <div className="project-context-grid">
        <Field label="Repository" value={projectContext.repoName} />
        <Field label="Branch" value={projectContext.gitBranch} />
        <Field
          label="Commit"
          value={projectContext.gitCommitShort ?? projectContext.gitCommit}
        />
        <Field label="Remote" value={projectContext.remoteHost ?? projectContext.remoteUrl} />
        <Field label="Project root" value={projectContext.projectRoot} />
        <Field label="Dirty" value={projectContext.isDirty == null ? null : projectContext.isDirty ? 'Yes' : 'No'} />
        <Field label="Submodule" value={projectContext.isSubmodule == null ? null : projectContext.isSubmodule ? 'Yes' : 'No'} />
        <Field label="Worktree" value={projectContext.isWorktree == null ? null : projectContext.isWorktree ? 'Yes' : 'No'} />
      </div>
    </div>
  )
}
