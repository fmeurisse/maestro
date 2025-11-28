import { StatusBadge } from './StatusBadge'
import { formatRelativeTime, formatDuration } from '../utils/timeUtils'
import type { ExecutionSummary } from '../types/execution'

interface ExecutionCardProps {
  execution: ExecutionSummary
  onClick: () => void
}

/**
 * Card displaying execution summary
 */
export function ExecutionCard({ execution, onClick }: ExecutionCardProps) {
  const truncatedId = execution.executionId.substring(0, 12)
  const duration = execution.completedAt
    ? formatDuration(execution.startedAt, execution.completedAt)
    : 'Running...'
  const progress = execution.stepCount > 0
    ? `${execution.completedSteps}/${execution.stepCount} steps`
    : 'No steps'

  return (
    <div className="execution-card" onClick={onClick}>
      <div className="card-header">
        <div className="execution-id">
          <span className="id-label">ID:</span>
          <code>{truncatedId}</code>
        </div>
        <StatusBadge status={execution.status} size="small" />
      </div>

      <div className="card-body">
        <div className="workflow-info">
          <span className="label">Workflow:</span>
          <span className="value">v{execution.revisionVersion}</span>
        </div>

        <div className="time-info">
          <span className="label">Started:</span>
          <span className="value">{formatRelativeTime(execution.startedAt)}</span>
        </div>

        {execution.completedAt && (
          <div className="duration-info">
            <span className="label">Duration:</span>
            <span className="value">{duration}</span>
          </div>
        )}

        <div className="progress-info">
          <span className="label">Progress:</span>
          <span className="value">{progress}</span>
          {execution.failedSteps > 0 && (
            <span className="failed-steps">({execution.failedSteps} failed)</span>
          )}
        </div>

        {execution.errorMessage && (
          <div className="error-preview">
            <span className="label">Error:</span>
            <span className="error-text">{execution.errorMessage}</span>
          </div>
        )}
      </div>

      <style>{`
        .execution-card {
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 6px;
          padding: 1rem;
          cursor: pointer;
          transition: all 0.2s;
        }

        .execution-card:hover {
          border-color: #646cff;
          box-shadow: 0 2px 8px rgba(100, 108, 255, 0.2);
        }

        .card-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 0.75rem;
          padding-bottom: 0.75rem;
          border-bottom: 1px solid #333;
        }

        .execution-id {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          font-size: 0.9rem;
        }

        .id-label {
          color: #888;
          font-weight: 500;
        }

        .execution-id code {
          background-color: #2a2a2a;
          padding: 0.2rem 0.5rem;
          border-radius: 3px;
          font-size: 0.85rem;
        }

        .card-body {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
          font-size: 0.9rem;
        }

        .workflow-info,
        .time-info,
        .duration-info,
        .progress-info,
        .error-preview {
          display: flex;
          gap: 0.5rem;
        }

        .label {
          color: #888;
          font-weight: 500;
          min-width: 70px;
        }

        .value {
          color: #ccc;
        }

        .failed-steps {
          color: #ef4444;
          margin-left: 0.25rem;
        }

        .error-preview {
          padding-top: 0.5rem;
          border-top: 1px solid #333;
        }

        .error-text {
          color: #ff6b6b;
          font-size: 0.85rem;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        @media (prefers-color-scheme: light) {
          .execution-card {
            background-color: white;
            border-color: #ddd;
          }

          .execution-card:hover {
            background-color: #f9f9f9;
          }

          .execution-id code {
            background-color: #f5f5f5;
          }

          .card-header,
          .error-preview {
            border-color: #ddd;
          }
        }
      `}</style>
    </div>
  )
}
