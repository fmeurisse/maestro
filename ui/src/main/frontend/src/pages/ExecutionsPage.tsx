import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ExecutionCard } from '../components/ExecutionCard'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { workflowApi } from '../services/workflowApi'
import type { ExecutionSummary, ExecutionStatus } from '../types/execution'

export function ExecutionsPage() {
  const navigate = useNavigate()
  const [executions, setExecutions] = useState<ExecutionSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<ExecutionStatus | 'ALL'>('ALL')
  const [currentPage, setCurrentPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const pageSize = 20

  // Note: This implementation assumes you'll provide a workflow namespace/id
  // In a real implementation, you might need a global "list all executions" endpoint
  // For now, this is a placeholder that demonstrates the UI structure
  const defaultNamespace = 'production'
  const defaultWorkflowId = 'my-workflow'

  useEffect(() => {
    loadExecutions()

    // Auto-refresh for running/pending executions
    const hasActiveExecutions = executions.some(
      e => e.status === 'RUNNING' || e.status === 'PENDING'
    )

    if (hasActiveExecutions) {
      const interval = setInterval(() => {
        loadExecutions()
      }, 5000)

      return () => clearInterval(interval)
    }
  }, [statusFilter, currentPage])

  const loadExecutions = async () => {
    try {
      setLoading(true)
      setError(null)

      // This is a placeholder implementation
      // In production, you'd either:
      // 1. Have a global executions endpoint
      // 2. Load executions from multiple known workflows
      // 3. Require workflow selection first

      const response = await workflowApi.getExecutionHistory(
        defaultNamespace,
        defaultWorkflowId,
        {
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          limit: pageSize,
          offset: currentPage * pageSize,
        }
      )

      setExecutions(response.executions)
      setHasMore(response.pagination.hasMore)
    } catch (err) {
      // If workflow doesn't exist, show empty state instead of error
      if (err instanceof Error && err.message.includes('404')) {
        setExecutions([])
        setHasMore(false)
      } else {
        setError(err instanceof Error ? err.message : 'Failed to load executions')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleCardClick = (executionId: string) => {
    navigate(`/executions/${executionId}`)
  }

  const handlePreviousPage = () => {
    if (currentPage > 0) {
      setCurrentPage(currentPage - 1)
    }
  }

  const handleNextPage = () => {
    if (hasMore) {
      setCurrentPage(currentPage + 1)
    }
  }

  return (
    <div className="executions-page">
      <div className="page-header">
        <h2>Workflow Executions</h2>
        <div className="header-actions">
          <select
            className="status-filter"
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value as ExecutionStatus | 'ALL')
              setCurrentPage(0)
            }}
          >
            <option value="ALL">All Statuses</option>
            <option value="PENDING">Pending</option>
            <option value="RUNNING">Running</option>
            <option value="COMPLETED">Completed</option>
            <option value="FAILED">Failed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>
      </div>

      {error && (
        <div className="error-banner">
          <strong>Error:</strong> {error}
        </div>
      )}

      {loading && executions.length === 0 ? (
        <LoadingSpinner message="Loading executions..." />
      ) : executions.length === 0 ? (
        <div className="empty-state">
          <p>No executions found.</p>
          <p className="hint">
            Launch a workflow execution to see it here.
          </p>
        </div>
      ) : (
        <>
          <div className="executions-grid">
            {executions.map((execution) => (
              <ExecutionCard
                key={execution.executionId}
                execution={execution}
                onClick={() => handleCardClick(execution.executionId)}
              />
            ))}
          </div>

          <div className="pagination">
            <button
              className="pagination-button"
              onClick={handlePreviousPage}
              disabled={currentPage === 0}
            >
              ← Previous
            </button>
            <span className="page-info">
              Page {currentPage + 1}
            </span>
            <button
              className="pagination-button"
              onClick={handleNextPage}
              disabled={!hasMore}
            >
              Next →
            </button>
          </div>
        </>
      )}

      <style>{`
        .executions-page {
          display: flex;
          flex-direction: column;
          gap: 1.5rem;
        }

        .page-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          flex-wrap: wrap;
          gap: 1rem;
        }

        .page-header h2 {
          margin: 0;
          font-size: 1.8rem;
        }

        .header-actions {
          display: flex;
          gap: 1rem;
          align-items: center;
        }

        .status-filter {
          padding: 0.5rem 1rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
          color: inherit;
          font-size: 0.9rem;
          cursor: pointer;
        }

        .status-filter:hover {
          border-color: #646cff;
        }

        .error-banner {
          padding: 1rem;
          background-color: #ff6b6b22;
          border: 1px solid #ff6b6b;
          border-radius: 4px;
          color: #ff6b6b;
        }

        .empty-state {
          padding: 4rem 2rem;
          text-align: center;
          color: #888;
        }

        .empty-state p {
          margin: 0.5rem 0;
          font-size: 1.1rem;
        }

        .empty-state .hint {
          font-size: 0.9rem;
          color: #666;
        }

        .executions-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
          gap: 1rem;
        }

        .pagination {
          display: flex;
          justify-content: center;
          align-items: center;
          gap: 1rem;
          padding: 1rem 0;
        }

        .pagination-button {
          padding: 0.5rem 1rem;
          background-color: #646cff;
          color: white;
          border: none;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.2s;
        }

        .pagination-button:hover:not(:disabled) {
          background-color: #4c54dd;
        }

        .pagination-button:disabled {
          background-color: #333;
          color: #666;
          cursor: not-allowed;
        }

        .page-info {
          color: #888;
          font-size: 0.9rem;
        }

        @media (prefers-color-scheme: light) {
          .status-filter {
            background-color: white;
            border-color: #ddd;
          }
        }

        @media (max-width: 768px) {
          .page-header {
            flex-direction: column;
            align-items: stretch;
          }

          .executions-grid {
            grid-template-columns: 1fr;
          }
        }
      `}</style>
    </div>
  )
}
