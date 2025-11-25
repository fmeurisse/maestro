import { useEffect, useState } from 'react'
import type { WorkflowListItem } from '../types/workflow'
import { workflowApi } from '../services/workflowApi'

interface WorkflowRevisionsProps {
  namespace: string
  id: string
  onSelectRevision: (revision: WorkflowListItem) => void
  selectedRevision?: WorkflowListItem
}

/**
 * Component to display all revisions of a specific workflow
 * Shows version history with active/inactive status
 */
export function WorkflowRevisions({
  namespace,
  id,
  onSelectRevision,
  selectedRevision,
}: WorkflowRevisionsProps) {
  const [revisions, setRevisions] = useState<WorkflowListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showActiveOnly, setShowActiveOnly] = useState(false)

  useEffect(() => {
    loadRevisions()
  }, [namespace, id, showActiveOnly])

  const loadRevisions = async () => {
    setLoading(true)
    setError(null)

    try {
      const data = await workflowApi.listRevisions(
        namespace,
        id,
        showActiveOnly
      )
      setRevisions(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load revisions')
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return <div className="loading">Loading revisions...</div>
  }

  if (error) {
    return (
      <div className="error">
        <p>Error: {error}</p>
        <button onClick={loadRevisions}>Retry</button>
      </div>
    )
  }

  return (
    <div className="workflow-revisions">
      <div className="revisions-header">
        <h3>
          Revisions for {namespace}/{id}
        </h3>
        <label className="filter-checkbox">
          <input
            type="checkbox"
            checked={showActiveOnly}
            onChange={(e) => setShowActiveOnly(e.target.checked)}
          />
          <span>Show active only</span>
        </label>
      </div>

      <div className="revisions-list">
        {revisions.length === 0 ? (
          <div className="empty-state">
            {showActiveOnly
              ? 'No active revisions'
              : 'No revisions found for this workflow'}
          </div>
        ) : (
          revisions
            .sort((a, b) => b.version - a.version)
            .map((revision) => {
              const isSelected =
                selectedRevision &&
                selectedRevision.namespace === revision.namespace &&
                selectedRevision.id === revision.id &&
                selectedRevision.version === revision.version

              return (
                <div
                  key={revision.version}
                  className={`revision-item ${isSelected ? 'selected' : ''}`}
                  onClick={() => onSelectRevision(revision)}
                >
                  <div className="revision-header">
                    <span className="revision-version">
                      Version {revision.version}
                    </span>
                    {revision.active && (
                      <span className="badge active">Active</span>
                    )}
                  </div>
                </div>
              )
            })
        )}
      </div>

      <style>{`
        .workflow-revisions {
          display: flex;
          flex-direction: column;
          height: 100%;
        }

        .revisions-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 1rem;
          padding-bottom: 0.5rem;
          border-bottom: 1px solid #333;
        }

        .revisions-header h3 {
          margin: 0;
          font-size: 1.1rem;
        }

        .filter-checkbox {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          cursor: pointer;
          font-size: 0.9rem;
        }

        .filter-checkbox input[type="checkbox"] {
          cursor: pointer;
        }

        .revisions-list {
          flex: 1;
          overflow-y: auto;
        }

        .revision-item {
          padding: 0.75rem 1rem;
          margin-bottom: 0.5rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.2s;
        }

        .revision-item:hover {
          border-color: #646cff;
          background-color: #222;
        }

        .revision-item.selected {
          border-color: #646cff;
          background-color: #2a2a4a;
        }

        .revision-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
        }

        .revision-version {
          font-weight: 500;
        }

        .badge {
          padding: 0.25rem 0.5rem;
          border-radius: 3px;
          font-size: 0.75rem;
          font-weight: 600;
        }

        .badge.active {
          background-color: #10b981;
          color: white;
        }

        .loading,
        .error,
        .empty-state {
          padding: 2rem;
          text-align: center;
          color: #888;
        }

        .error {
          color: #ff6b6b;
        }

        .error button {
          margin-top: 1rem;
        }

        @media (prefers-color-scheme: light) {
          .revisions-header {
            border-bottom-color: #ddd;
          }

          .revision-item {
            background-color: white;
            border-color: #ddd;
          }

          .revision-item:hover {
            background-color: #f5f5f5;
          }

          .revision-item.selected {
            background-color: #e8e8ff;
          }
        }
      `}</style>
    </div>
  )
}
