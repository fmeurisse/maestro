import { formatJSON } from '../utils/jsonFormatter'

interface ParametersDisplayProps {
  parameters: Record<string, any>
  mode?: 'table' | 'json'
}

/**
 * Read-only display of execution input parameters
 */
export function ParametersDisplay({ parameters, mode = 'table' }: ParametersDisplayProps) {
  const isEmpty = Object.keys(parameters).length === 0

  if (isEmpty) {
    return (
      <div className="parameters-display">
        <p className="empty-message">No parameters</p>
        <style>{`
          .parameters-display {
            padding: 1rem;
            background-color: #1a1a1a;
            border: 1px solid #333;
            border-radius: 4px;
          }

          .empty-message {
            margin: 0;
            color: #888;
            font-style: italic;
          }

          @media (prefers-color-scheme: light) {
            .parameters-display {
              background-color: #f9f9f9;
              border-color: #ddd;
            }
          }
        `}</style>
      </div>
    )
  }

  if (mode === 'json') {
    return (
      <div className="parameters-display json-mode">
        <pre className="json-content">{formatJSON(parameters)}</pre>
        <style>{`
          .parameters-display.json-mode {
            background-color: #1a1a1a;
            border: 1px solid #333;
            border-radius: 4px;
            padding: 1rem;
            overflow-x: auto;
          }

          .json-content {
            margin: 0;
            font-family: 'Courier New', monospace;
            font-size: 0.9rem;
            color: #ccc;
          }

          @media (prefers-color-scheme: light) {
            .parameters-display.json-mode {
              background-color: #f9f9f9;
              border-color: #ddd;
            }

            .json-content {
              color: #333;
            }
          }
        `}</style>
      </div>
    )
  }

  return (
    <div className="parameters-display table-mode">
      <table className="parameters-table">
        <thead>
          <tr>
            <th>Parameter</th>
            <th>Value</th>
          </tr>
        </thead>
        <tbody>
          {Object.entries(parameters).map(([key, value]) => (
            <tr key={key}>
              <td className="param-key">{key}</td>
              <td className="param-value">
                {typeof value === 'object' ? formatJSON(value) : String(value)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <style>{`
        .parameters-display.table-mode {
          overflow-x: auto;
        }

        .parameters-table {
          width: 100%;
          border-collapse: collapse;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
        }

        .parameters-table th,
        .parameters-table td {
          padding: 0.75rem 1rem;
          text-align: left;
          border-bottom: 1px solid #333;
        }

        .parameters-table th {
          background-color: #2a2a2a;
          font-weight: 600;
          color: #ccc;
        }

        .parameters-table tbody tr:last-child td {
          border-bottom: none;
        }

        .parameters-table tbody tr:hover {
          background-color: #2a2a2a;
        }

        .param-key {
          font-weight: 500;
          color: #888;
          min-width: 150px;
        }

        .param-value {
          font-family: 'Courier New', monospace;
          font-size: 0.9rem;
          color: #ccc;
          word-break: break-word;
        }

        @media (prefers-color-scheme: light) {
          .parameters-table {
            background-color: white;
            border-color: #ddd;
          }

          .parameters-table th {
            background-color: #f5f5f5;
            color: #333;
          }

          .parameters-table th,
          .parameters-table td {
            border-bottom-color: #ddd;
          }

          .parameters-table tbody tr:hover {
            background-color: #f9f9f9;
          }

          .param-value {
            color: #333;
          }
        }
      `}</style>
    </div>
  )
}
