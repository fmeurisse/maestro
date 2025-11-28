import { useState } from 'react'
import { formatJSON } from '../utils/jsonFormatter'

interface ErrorDetailsPanelProps {
  errorMessage: string
  errorDetails?: {
    errorType: string
    stackTrace: string
    stepInputs?: Record<string, any>
  }
}

/**
 * Collapsible error details panel with stack trace
 */
export function ErrorDetailsPanel({ errorMessage, errorDetails }: ErrorDetailsPanelProps) {
  const [isExpanded, setIsExpanded] = useState(false)

  return (
    <div className="error-details-panel">
      <div className="error-header">
        <div className="error-icon">⚠️</div>
        <div className="error-content">
          <h4>Error</h4>
          <p className="error-message">{errorMessage}</p>
          {errorDetails?.errorType && (
            <p className="error-type">Type: {errorDetails.errorType}</p>
          )}
        </div>
      </div>

      {errorDetails && (
        <>
          <button className="toggle-button" onClick={() => setIsExpanded(!isExpanded)}>
            {isExpanded ? '▼' : '▶'} {isExpanded ? 'Hide Details' : 'Show Details'}
          </button>

          {isExpanded && (
            <div className="error-details">
              {errorDetails.stackTrace && (
                <div className="stack-trace-section">
                  <h5>Stack Trace</h5>
                  <pre className="stack-trace">{errorDetails.stackTrace}</pre>
                </div>
              )}

              {errorDetails.stepInputs && (
                <div className="step-inputs-section">
                  <h5>Step Inputs (at time of error)</h5>
                  <pre className="step-inputs">{formatJSON(errorDetails.stepInputs)}</pre>
                </div>
              )}
            </div>
          )}
        </>
      )}

      <style>{`
        .error-details-panel {
          background-color: #2a1a1a;
          border: 2px solid #ef4444;
          border-radius: 6px;
          padding: 1.5rem;
        }

        .error-header {
          display: flex;
          gap: 1rem;
        }

        .error-icon {
          font-size: 2rem;
          flex-shrink: 0;
        }

        .error-content {
          flex: 1;
        }

        .error-content h4 {
          margin: 0 0 0.5rem 0;
          color: #ef4444;
          font-size: 1.2rem;
        }

        .error-message {
          margin: 0 0 0.5rem 0;
          color: #ff6b6b;
          font-size: 1rem;
          font-weight: 500;
        }

        .error-type {
          margin: 0;
          color: #888;
          font-size: 0.9rem;
          font-family: 'Courier New', monospace;
        }

        .toggle-button {
          margin-top: 1rem;
          padding: 0.5rem 1rem;
          background-color: #333;
          border: 1px solid #444;
          border-radius: 4px;
          color: #ccc;
          cursor: pointer;
          font-size: 0.9rem;
          transition: all 0.2s;
        }

        .toggle-button:hover {
          background-color: #444;
          border-color: #555;
        }

        .error-details {
          margin-top: 1rem;
          padding-top: 1rem;
          border-top: 1px solid #444;
        }

        .stack-trace-section,
        .step-inputs-section {
          margin-bottom: 1rem;
        }

        .stack-trace-section h5,
        .step-inputs-section h5 {
          margin: 0 0 0.5rem 0;
          color: #ccc;
          font-size: 0.95rem;
          font-weight: 600;
        }

        .stack-trace,
        .step-inputs {
          margin: 0;
          padding: 1rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
          font-family: 'Courier New', monospace;
          font-size: 0.85rem;
          color: #ff6b6b;
          overflow-x: auto;
          white-space: pre-wrap;
          word-break: break-word;
        }

        .step-inputs {
          color: #ccc;
        }

        @media (prefers-color-scheme: light) {
          .error-details-panel {
            background-color: #fff5f5;
            border-color: #ef4444;
          }

          .error-type {
            color: #666;
          }

          .toggle-button {
            background-color: #f5f5f5;
            border-color: #ddd;
            color: #333;
          }

          .toggle-button:hover {
            background-color: #e8e8e8;
          }

          .error-details {
            border-top-color: #ddd;
          }

          .stack-trace-section h5,
          .step-inputs-section h5 {
            color: #333;
          }

          .stack-trace,
          .step-inputs {
            background-color: #fafafa;
            border-color: #ddd;
          }

          .stack-trace {
            color: #d32f2f;
          }

          .step-inputs {
            color: #333;
          }
        }
      `}</style>
    </div>
  )
}
