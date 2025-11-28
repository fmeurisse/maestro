import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { StatusBadge } from '../components/StatusBadge'
import { ParametersDisplay } from '../components/ParametersDisplay'
import { StepResultsTable } from '../components/StepResultsTable'
import { ErrorDetailsPanel } from '../components/ErrorDetailsPanel'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { formatDateTime, formatDuration } from '../utils/timeUtils'
import { workflowApi } from '../services/workflowApi'
import type { ExecutionDetail } from '../types/execution'

export function ExecutionDetailPage() {
  const { executionId } = useParams<{ executionId: string }>()
  const [execution, setExecution] = useState<ExecutionDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (executionId) {
      loadExecution()

      // Auto-refresh if execution is running or pending
      const interval = setInterval(() => {
        if (execution?.status === 'RUNNING' || execution?.status === 'PENDING') {
          loadExecution()
        }
      }, 3000)

      return () => clearInterval(interval)
    }
  }, [executionId, execution?.status])

  const loadExecution = async () => {
    if (!executionId) return

    try {
      setLoading(true)
      setError(null)

      const data = await workflowApi.getExecution(executionId)
      setExecution(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load execution')
    } finally {
      setLoading(false)
    }
  }

  if (loading && !execution) {
    return <LoadingSpinner message="Loading execution details..." />
  }

  if (error) {
    return (
      <div className="error-page">
        <h2>Error</h2>
        <p>{error}</p>
        <Link to="/executions" className="back-link">
          ← Back to Executions
        </Link>
        <style>{`
          .error-page {
            padding: 2rem;
          }

          .error-page h2 {
            color: #ef4444;
          }

          .back-link {
            display: inline-block;
            margin-top: 1rem;
            color: #646cff;
            text-decoration: none;
          }

          .back-link:hover {
            text-decoration: underline;
          }
        `}</style>
      </div>
    )
  }

  if (!execution) {
    return (
      <div className="empty-page">
        <p>Execution not found</p>
        <Link to="/executions" className="back-link">
          ← Back to Executions
        </Link>
      </div>
    )
  }

  const duration = execution.completedAt
    ? formatDuration(execution.startedAt, execution.completedAt)
    : 'Running...'

  return (
    <div className="execution-detail-page">
      <div className="page-header">
        <Link to="/executions" className="back-link">
          ← Back to Executions
        </Link>
      </div>

      <div className="execution-header">
        <div className="header-main">
          <h2>Execution Details</h2>
          <StatusBadge status={execution.status} size="large" />
        </div>
        <div className="execution-id">
          <span className="label">ID:</span>
          <code>{execution.executionId}</code>
        </div>
      </div>

      <div className="info-section">
        <h3>Workflow Information</h3>
        <div className="info-grid">
          <div className="info-item">
            <span className="label">Namespace:</span>
            <span className="value">{execution.revisionId.namespace}</span>
          </div>
          <div className="info-item">
            <span className="label">Workflow ID:</span>
            <span className="value">{execution.revisionId.id}</span>
          </div>
          <div className="info-item">
            <span className="label">Version:</span>
            <span className="value">v{execution.revisionId.version}</span>
          </div>
        </div>
      </div>

      <div className="info-section">
        <h3>Timing</h3>
        <div className="info-grid">
          <div className="info-item">
            <span className="label">Started:</span>
            <span className="value">{formatDateTime(execution.startedAt)}</span>
          </div>
          {execution.completedAt && (
            <div className="info-item">
              <span className="label">Completed:</span>
              <span className="value">{formatDateTime(execution.completedAt)}</span>
            </div>
          )}
          <div className="info-item">
            <span className="label">Duration:</span>
            <span className="value">{duration}</span>
          </div>
        </div>
      </div>

      <div className="info-section">
        <h3>Input Parameters</h3>
        <ParametersDisplay parameters={execution.inputParameters} mode="table" />
      </div>

      {execution.errorMessage && (
        <div className="info-section">
          <ErrorDetailsPanel errorMessage={execution.errorMessage} />
        </div>
      )}

      <div className="info-section">
        <h3>Step Results ({execution.steps.length} steps)</h3>
        <StepResultsTable steps={execution.steps} />
      </div>

      <style>{`
        .execution-detail-page {
          display: flex;
          flex-direction: column;
          gap: 2rem;
        }

        .page-header {
          margin-bottom: -1rem;
        }

        .back-link {
          display: inline-block;
          color: #646cff;
          text-decoration: none;
          font-size: 0.95rem;
          transition: all 0.2s;
        }

        .back-link:hover {
          text-decoration: underline;
          color: #4c54dd;
        }

        .execution-header {
          padding: 1.5rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 6px;
        }

        .header-main {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 1rem;
          flex-wrap: wrap;
          gap: 1rem;
        }

        .header-main h2 {
          margin: 0;
          font-size: 1.8rem;
        }

        .execution-id {
          display: flex;
          align-items: center;
          gap: 0.75rem;
        }

        .execution-id .label {
          color: #888;
          font-weight: 500;
        }

        .execution-id code {
          background-color: #2a2a2a;
          padding: 0.5rem 1rem;
          border-radius: 4px;
          font-size: 0.95rem;
          color: #ccc;
        }

        .info-section {
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 6px;
          padding: 1.5rem;
        }

        .info-section h3 {
          margin: 0 0 1rem 0;
          font-size: 1.2rem;
          color: #ccc;
        }

        .info-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
          gap: 1rem;
        }

        .info-item {
          display: flex;
          gap: 0.75rem;
          align-items: baseline;
        }

        .info-item .label {
          color: #888;
          font-weight: 500;
          min-width: 100px;
        }

        .info-item .value {
          color: #ccc;
          font-weight: 400;
        }

        @media (prefers-color-scheme: light) {
          .execution-header,
          .info-section {
            background-color: white;
            border-color: #ddd;
          }

          .execution-id code {
            background-color: #f5f5f5;
            color: #333;
          }

          .info-section h3,
          .info-item .value {
            color: #333;
          }

          .info-item .label {
            color: #666;
          }
        }

        @media (max-width: 768px) {
          .header-main {
            flex-direction: column;
            align-items: flex-start;
          }

          .info-grid {
            grid-template-columns: 1fr;
          }
        }
      `}</style>
    </div>
  )
}
