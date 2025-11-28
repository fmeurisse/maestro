import { formatJSON } from '../utils/jsonFormatter'

interface ParameterEditorProps {
  parameters: Record<string, any>
  onChange: (parameters: Record<string, any>) => void
  mode: 'form' | 'json'
  onModeChange: (mode: 'form' | 'json') => void
  schema?: {
    name: string
    type: string
    description?: string
    required?: boolean
  }[]
}

/**
 * Parameter editor supporting both form and JSON editing modes
 */
export function ParameterEditor({
  parameters,
  onChange,
  mode,
  onModeChange,
  schema,
}: ParameterEditorProps) {
  const handleFormChange = (key: string, value: any) => {
    onChange({ ...parameters, [key]: value })
  }

  const handleJsonChange = (jsonString: string) => {
    try {
      const parsed = JSON.parse(jsonString)
      onChange(parsed)
    } catch (err) {
      // Invalid JSON, keep current state
    }
  }

  const handleAddParameter = () => {
    const key = prompt('Parameter name:')
    if (key && key.trim()) {
      onChange({ ...parameters, [key.trim()]: '' })
    }
  }

  const handleRemoveParameter = (key: string) => {
    const newParams = { ...parameters }
    delete newParams[key]
    onChange(newParams)
  }

  return (
    <div className="parameter-editor">
      <div className="editor-header">
        <h4>Parameters</h4>
        <div className="mode-toggle">
          <button
            className={mode === 'form' ? 'active' : ''}
            onClick={() => onModeChange('form')}
          >
            Form
          </button>
          <button
            className={mode === 'json' ? 'active' : ''}
            onClick={() => onModeChange('json')}
          >
            JSON
          </button>
        </div>
      </div>

      {mode === 'form' ? (
        <div className="form-mode">
          {Object.keys(parameters).length === 0 && (
            <p className="empty-message">No parameters yet. Add some below.</p>
          )}

          {Object.entries(parameters).map(([key, value]) => (
            <div key={key} className="parameter-row">
              <div className="parameter-input">
                <label>{key}</label>
                <input
                  type="text"
                  value={typeof value === 'object' ? JSON.stringify(value) : String(value)}
                  onChange={(e) => {
                    let newValue: any = e.target.value
                    // Try to parse as JSON for objects
                    if (e.target.value.startsWith('{') || e.target.value.startsWith('[')) {
                      try {
                        newValue = JSON.parse(e.target.value)
                      } catch {
                        // Keep as string
                      }
                    }
                    handleFormChange(key, newValue)
                  }}
                  placeholder="Enter value..."
                />
              </div>
              <button
                className="remove-button"
                onClick={() => handleRemoveParameter(key)}
                title="Remove parameter"
              >
                ✕
              </button>
            </div>
          ))}

          <button className="add-button" onClick={handleAddParameter}>
            + Add Parameter
          </button>
        </div>
      ) : (
        <div className="json-mode">
          <textarea
            className="json-editor"
            value={formatJSON(parameters)}
            onChange={(e) => handleJsonChange(e.target.value)}
            placeholder="{}"
            rows={10}
          />
        </div>
      )}

      <style>{`
        .parameter-editor {
          display: flex;
          flex-direction: column;
          gap: 1rem;
        }

        .editor-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
        }

        .editor-header h4 {
          margin: 0;
          font-size: 1.1rem;
          color: #ccc;
        }

        .mode-toggle {
          display: flex;
          gap: 0;
          border: 1px solid #333;
          border-radius: 4px;
          overflow: hidden;
        }

        .mode-toggle button {
          padding: 0.4rem 1rem;
          background-color: #1a1a1a;
          border: none;
          color: #888;
          cursor: pointer;
          font-size: 0.9rem;
          transition: all 0.2s;
        }

        .mode-toggle button.active {
          background-color: #646cff;
          color: white;
        }

        .mode-toggle button:not(.active):hover {
          background-color: #2a2a2a;
        }

        .form-mode {
          display: flex;
          flex-direction: column;
          gap: 0.75rem;
        }

        .empty-message {
          margin: 0;
          padding: 2rem;
          text-align: center;
          color: #666;
          font-style: italic;
        }

        .parameter-row {
          display: flex;
          gap: 0.5rem;
          align-items: end;
        }

        .parameter-input {
          flex: 1;
          display: flex;
          flex-direction: column;
          gap: 0.4rem;
        }

        .parameter-input label {
          font-size: 0.9rem;
          font-weight: 500;
          color: #888;
        }

        .parameter-input input {
          padding: 0.5rem 0.75rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
          color: inherit;
          font-size: 0.95rem;
          font-family: 'Courier New', monospace;
        }

        .parameter-input input:focus {
          outline: none;
          border-color: #646cff;
        }

        .remove-button {
          padding: 0.5rem 0.75rem;
          background-color: #ef4444;
          border: none;
          border-radius: 4px;
          color: white;
          cursor: pointer;
          font-size: 1rem;
          transition: all 0.2s;
        }

        .remove-button:hover {
          background-color: #dc2626;
        }

        .add-button {
          padding: 0.5rem 1rem;
          background-color: #10b981;
          border: none;
          border-radius: 4px;
          color: white;
          cursor: pointer;
          font-weight: 500;
          transition: all 0.2s;
        }

        .add-button:hover {
          background-color: #059669;
        }

        .json-mode {
          display: flex;
          flex-direction: column;
        }

        .json-editor {
          padding: 1rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
          color: #ccc;
          font-family: 'Courier New', monospace;
          font-size: 0.9rem;
          resize: vertical;
          min-height: 200px;
        }

        .json-editor:focus {
          outline: none;
          border-color: #646cff;
        }

        @media (prefers-color-scheme: light) {
          .editor-header h4 {
            color: #333;
          }

          .mode-toggle {
            border-color: #ddd;
          }

          .mode-toggle button {
            background-color: white;
            color: #666;
          }

          .mode-toggle button:not(.active):hover {
            background-color: #f5f5f5;
          }

          .parameter-input input,
          .json-editor {
            background-color: white;
            border-color: #ddd;
            color: #333;
          }
        }
      `}</style>
    </div>
  )
}
