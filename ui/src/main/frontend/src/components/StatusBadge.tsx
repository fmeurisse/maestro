import type { ExecutionStatus, StepStatus } from '../types/execution'

interface StatusBadgeProps {
  status: ExecutionStatus | StepStatus
  size?: 'small' | 'medium' | 'large'
}

/**
 * Reusable status badge component for executions and steps
 */
export function StatusBadge({ status, size = 'medium' }: StatusBadgeProps) {
  const getStatusColor = () => {
    switch (status) {
      case 'COMPLETED':
        return '#10b981' // Green
      case 'FAILED':
        return '#ef4444' // Red
      case 'RUNNING':
        return '#3b82f6' // Blue
      case 'PENDING':
        return '#6b7280' // Gray
      case 'CANCELLED':
        return '#f59e0b' // Orange
      case 'SKIPPED':
        return '#9ca3af' // Light gray
      default:
        return '#6b7280'
    }
  }

  const getSizeStyles = () => {
    switch (size) {
      case 'small':
        return { padding: '0.15rem 0.4rem', fontSize: '0.7rem' }
      case 'large':
        return { padding: '0.4rem 0.75rem', fontSize: '0.9rem' }
      default:
        return { padding: '0.25rem 0.5rem', fontSize: '0.75rem' }
    }
  }

  const isPulse = status === 'RUNNING'

  return (
    <>
      <span className={`status-badge ${isPulse ? 'pulse' : ''}`} style={{ backgroundColor: getStatusColor(), ...getSizeStyles() }}>
        {status}
      </span>
      <style>{`
        .status-badge {
          display: inline-block;
          border-radius: 3px;
          font-weight: 600;
          text-transform: uppercase;
          letter-spacing: 0.5px;
          color: white;
        }

        .status-badge.pulse {
          animation: pulse 2s ease-in-out infinite;
        }

        @keyframes pulse {
          0%, 100% {
            opacity: 1;
          }
          50% {
            opacity: 0.6;
          }
        }
      `}</style>
    </>
  )
}
