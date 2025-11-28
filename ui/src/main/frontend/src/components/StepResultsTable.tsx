import { useState } from 'react'
import { StatusBadge } from './StatusBadge'
import { formatDuration } from '../utils/timeUtils'
import { formatJSON } from '../utils/jsonFormatter'
import type { ExecutionStepResult } from '../types/execution'

interface StepResultsTableProps {
  steps: ExecutionStepResult[]
}

/**
 * Expandable table displaying execution step results
 */
export function StepResultsTable({ steps }: StepResultsTableProps) {
  const [expandedSteps, setExpandedSteps] = useState<Set<number>>(new Set())

  const toggleStep = (stepIndex: number) => {
    const newExpanded = new Set(expandedSteps)
    if (newExpanded.has(stepIndex)) {
      newExpanded.delete(stepIndex)
    } else {
      newExpanded.add(stepIndex)
    }
    setExpandedSteps(newExpanded)
  }

  if (steps.length === 0) {
    return (
      <div className="step-results-empty">
        <p>No steps to display</p>
        <style>{`
          .step-results-empty {
            padding: 2rem;
            text-align: center;
            color: #888;
            background-color: #1a1a1a;
            border: 1px solid #333;
            border-radius: 4px;
          }

          .step-results-empty p {
            margin: 0;
          }

          @media (prefers-color-scheme: light) {
            .step-results-empty {
              background-color: #f9f9f9;
              border-color: #ddd;
            }
          }
        `}</style>
      </div>
    )
  }

  return (
    <div className="step-results-table">
      <table>
        <thead>
          <tr>
            <th className="col-expand"></th>
            <th className="col-step">Step</th>
            <th className="col-id">Step ID</th>
            <th className="col-type">Type</th>
            <th className="col-status">Status</th>
            <th className="col-duration">Duration</th>
          </tr>
        </thead>
        <tbody>
          {steps.map((step, index) => {
            const isExpanded = expandedSteps.has(step.stepIndex)
            const duration = formatDuration(step.startedAt, step.completedAt)
            const hasDetails = step.inputData || step.outputData || step.errorMessage

            return (
              <>
                <tr key={step.stepIndex} className={isExpanded ? 'expanded' : ''}>
                  <td className="col-expand">
                    {hasDetails && (
                      <button className="expand-button" onClick={() => toggleStep(step.stepIndex)}>
                        {isExpanded ? '▼' : '▶'}
                      </button>
                    )}
                  </td>
                  <td className="col-step">{step.stepIndex + 1}</td>
                  <td className="col-id">
                    <code>{step.stepId}</code>
                  </td>
                  <td className="col-type">{step.stepType}</td>
                  <td className="col-status">
                    <StatusBadge status={step.status} size="small" />
                  </td>
                  <td className="col-duration">{duration}</td>
                </tr>

                {isExpanded && (
                  <tr key={`${step.stepIndex}-details`} className="step-details-row">
                    <td colSpan={6}>
                      <div className="step-details">
                        {step.inputData && (
                          <div className="details-section">
                            <h5>Input Data</h5>
                            <pre className="data-content">{formatJSON(step.inputData)}</pre>
                          </div>
                        )}

                        {step.outputData && (
                          <div className="details-section">
                            <h5>Output Data</h5>
                            <pre className="data-content">{formatJSON(step.outputData)}</pre>
                          </div>
                        )}

                        {step.errorMessage && (
                          <div className="details-section error-section">
                            <h5>Error</h5>
                            <p className="error-message">{step.errorMessage}</p>
                            {step.errorDetails && (
                              <>
                                <p className="error-type">Type: {step.errorDetails.errorType}</p>
                                {step.errorDetails.stackTrace && (
                                  <details className="stack-trace-details">
                                    <summary>Stack Trace</summary>
                                    <pre className="stack-trace">{step.errorDetails.stackTrace}</pre>
                                  </details>
                                )}
                              </>
                            )}
                          </div>
                        )}
                      </div>
                    </td>
                  </tr>
                )}
              </>
            )
          })}
        </tbody>
      </table>

      <style>{`
        .step-results-table {
          overflow-x: auto;
        }

        .step-results-table table {
          width: 100%;
          border-collapse: collapse;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
        }

        .step-results-table th,
        .step-results-table td {
          padding: 0.75rem;
          text-align: left;
          border-bottom: 1px solid #333;
        }

        .step-results-table thead th {
          background-color: #2a2a2a;
          font-weight: 600;
          color: #ccc;
          position: sticky;
          top: 0;
        }

        .step-results-table tbody tr {
          transition: background-color 0.2s;
        }

        .step-results-table tbody tr:hover {
          background-color: #2a2a2a;
        }

        .step-results-table tbody tr.expanded {
          background-color: #2a2a2a;
        }

        .col-expand {
          width: 40px;
          text-align: center;
        }

        .col-step {
          width: 60px;
          font-weight: 500;
        }

        .col-id code {
          background-color: #2a2a2a;
          padding: 0.2rem 0.5rem;
          border-radius: 3px;
          font-size: 0.85rem;
        }

        .col-type {
          color: #888;
        }

        .col-duration {
          color: #888;
          font-size: 0.9rem;
        }

        .expand-button {
          background-color: transparent;
          border: none;
          color: #646cff;
          cursor: pointer;
          font-size: 0.8rem;
          padding: 0.25rem;
          transition: transform 0.2s;
        }

        .expand-button:hover {
          transform: scale(1.2);
        }

        .step-details-row {
          background-color: #222 !important;
        }

        .step-details-row:hover {
          background-color: #222 !important;
        }

        .step-details {
          padding: 1rem;
          display: flex;
          flex-direction: column;
          gap: 1rem;
        }

        .details-section {
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
          padding: 1rem;
        }

        .details-section h5 {
          margin: 0 0 0.5rem 0;
          color: #ccc;
          font-size: 0.9rem;
          font-weight: 600;
        }

        .data-content {
          margin: 0;
          font-family: 'Courier New', monospace;
          font-size: 0.85rem;
          color: #ccc;
          overflow-x: auto;
          white-space: pre-wrap;
          word-break: break-word;
        }

        .error-section {
          border-color: #ef4444;
        }

        .error-message {
          margin: 0 0 0.5rem 0;
          color: #ff6b6b;
          font-weight: 500;
        }

        .error-type {
          margin: 0;
          color: #888;
          font-size: 0.85rem;
          font-family: 'Courier New', monospace;
        }

        .stack-trace-details {
          margin-top: 0.5rem;
        }

        .stack-trace-details summary {
          cursor: pointer;
          color: #888;
          font-size: 0.9rem;
          padding: 0.25rem 0;
        }

        .stack-trace-details summary:hover {
          color: #ccc;
        }

        .stack-trace {
          margin: 0.5rem 0 0 0;
          padding: 0.75rem;
          background-color: #2a1a1a;
          border: 1px solid #ef4444;
          border-radius: 4px;
          font-family: 'Courier New', monospace;
          font-size: 0.8rem;
          color: #ff6b6b;
          overflow-x: auto;
          white-space: pre-wrap;
          word-break: break-word;
        }

        @media (prefers-color-scheme: light) {
          .step-results-table table {
            background-color: white;
            border-color: #ddd;
          }

          .step-results-table th,
          .step-results-table td {
            border-bottom-color: #ddd;
          }

          .step-results-table thead th {
            background-color: #f5f5f5;
            color: #333;
          }

          .step-results-table tbody tr:hover,
          .step-results-table tbody tr.expanded {
            background-color: #f9f9f9;
          }

          .col-id code {
            background-color: #f5f5f5;
          }

          .step-details-row {
            background-color: #fafafa !important;
          }

          .details-section {
            background-color: white;
            border-color: #ddd;
          }

          .details-section h5 {
            color: #333;
          }

          .data-content {
            color: #333;
          }

          .stack-trace-details summary {
            color: #666;
          }

          .stack-trace-details summary:hover {
            color: #333;
          }

          .stack-trace {
            background-color: #fff5f5;
            border-color: #ef4444;
            color: #d32f2f;
          }
        }

        @media (max-width: 768px) {
          .col-id,
          .col-duration {
            display: none;
          }
        }
      `}</style>
    </div>
  )
}
