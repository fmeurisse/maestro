import { useState } from 'react'
import type { WorkflowRevisionID } from '../types/workflow'
import { workflowApi } from '../services/workflowApi'

interface RevisionActionsProps {
  revision: WorkflowRevisionID
  onActionComplete: () => void
}

/**
 * Component providing actions for workflow revisions
 * Supports activate, deactivate, and delete operations
 */
export function RevisionActions({
  revision,
  onActionComplete,
}: RevisionActionsProps) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleActivate = async () => {
    setLoading(true)
    setError(null)

    try {
      await workflowApi.activateRevision(
        revision.namespace,
        revision.id,
        revision.version
      )
      onActionComplete()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to activate')
    } finally {
      setLoading(false)
    }
  }

  const handleDeactivate = async () => {
    setLoading(true)
    setError(null)

    try {
      await workflowApi.deactivateRevision(
        revision.namespace,
        revision.id,
        revision.version
      )
      onActionComplete()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to deactivate')
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async () => {
    if (
      !confirm(
        `Are you sure you want to delete revision ${revision.version}?\nThis action cannot be undone.`
      )
    ) {
      return
    }

    setLoading(true)
    setError(null)

    try {
      await workflowApi.deleteRevision(
        revision.namespace,
        revision.id,
        revision.version
      )
      onActionComplete()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="revision-actions">
      <div className="actions-header">
        <h4>
          Actions for {revision.namespace}/{revision.id} v{revision.version}
        </h4>
        {revision.active && <span className="badge active">Active</span>}
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="actions-buttons">
        {revision.active ? (
          <button
            onClick={handleDeactivate}
            disabled={loading}
            className="button-deactivate"
          >
            {loading ? 'Deactivating...' : 'Deactivate'}
          </button>
        ) : (
          <>
            <button
              onClick={handleActivate}
              disabled={loading}
              className="button-activate"
            >
              {loading ? 'Activating...' : 'Activate'}
            </button>
            <button
              onClick={handleDelete}
              disabled={loading}
              className="button-delete"
            >
              {loading ? 'Deleting...' : 'Delete'}
            </button>
          </>
        )}
      </div>

      <style>{`
        .revision-actions {
          padding: 1rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
        }

        .actions-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 1rem;
        }

        .actions-header h4 {
          margin: 0;
          font-size: 1rem;
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

        .error-message {
          padding: 0.75rem;
          margin-bottom: 1rem;
          background-color: #ff6b6b22;
          border: 1px solid #ff6b6b;
          border-radius: 4px;
          color: #ff6b6b;
          font-size: 0.9rem;
        }

        .actions-buttons {
          display: flex;
          gap: 0.5rem;
        }

        .actions-buttons button {
          flex: 1;
        }

        .button-activate {
          background-color: #10b981;
          color: white;
        }

        .button-activate:hover:not(:disabled) {
          background-color: #0d9668;
        }

        .button-deactivate {
          background-color: #f59e0b;
          color: white;
        }

        .button-deactivate:hover:not(:disabled) {
          background-color: #d97706;
        }

        .button-delete {
          background-color: #ef4444;
          color: white;
        }

        .button-delete:hover:not(:disabled) {
          background-color: #dc2626;
        }

        button:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }

        @media (prefers-color-scheme: light) {
          .revision-actions {
            background-color: white;
            border-color: #ddd;
          }

          .error-message {
            background-color: #fee;
          }
        }
      `}</style>
    </div>
  )
}
