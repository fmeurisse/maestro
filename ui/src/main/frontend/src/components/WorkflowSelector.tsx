import { useState } from 'react'
import type { WorkflowRevisionID } from '../types/workflow'

interface WorkflowSelectorProps {
  onSelect: (workflow: WorkflowRevisionID) => void
  selectedWorkflow?: WorkflowRevisionID
}

/**
 * Simple workflow selector with manual input
 * Note: In production, this would load workflows from an API
 */
export function WorkflowSelector({ onSelect, selectedWorkflow }: WorkflowSelectorProps) {
  const [namespace, setNamespace] = useState(selectedWorkflow?.namespace || 'production')
  const [id, setId] = useState(selectedWorkflow?.id || '')
  const [version, setVersion] = useState(selectedWorkflow?.version?.toString() || '1')

  const handleSelect = () => {
    const versionNum = parseInt(version, 10)
    if (namespace && id && !isNaN(versionNum)) {
      onSelect({
        namespace,
        id,
        version: versionNum,
        active: true,
      })
    }
  }

  const isValid = namespace && id && version && !isNaN(parseInt(version, 10))

  return (
    <div className="workflow-selector">
      <div className="selector-form">
        <div className="form-group">
          <label htmlFor="namespace">Namespace</label>
          <input
            id="namespace"
            type="text"
            value={namespace}
            onChange={(e) => setNamespace(e.target.value)}
            placeholder="e.g., production"
          />
        </div>

        <div className="form-group">
          <label htmlFor="workflow-id">Workflow ID</label>
          <input
            id="workflow-id"
            type="text"
            value={id}
            onChange={(e) => setId(e.target.value)}
            placeholder="e.g., my-workflow"
          />
        </div>

        <div className="form-group">
          <label htmlFor="version">Version</label>
          <input
            id="version"
            type="number"
            min="1"
            value={version}
            onChange={(e) => setVersion(e.target.value)}
            placeholder="1"
          />
        </div>

        <button
          className="select-button"
          onClick={handleSelect}
          disabled={!isValid}
        >
          Select Workflow
        </button>
      </div>

      {selectedWorkflow && (
        <div className="selected-workflow">
          <span className="label">Selected:</span>
          <code>
            {selectedWorkflow.namespace}/{selectedWorkflow.id} v{selectedWorkflow.version}
          </code>
        </div>
      )}

      <style>{`
        .workflow-selector {
          display: flex;
          flex-direction: column;
          gap: 1rem;
        }

        .selector-form {
          display: grid;
          grid-template-columns: 2fr 2fr 1fr auto;
          gap: 1rem;
          align-items: end;
        }

        .form-group {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .form-group label {
          font-size: 0.9rem;
          font-weight: 500;
          color: #888;
        }

        .form-group input {
          padding: 0.5rem 0.75rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
          color: inherit;
          font-size: 0.95rem;
        }

        .form-group input:focus {
          outline: none;
          border-color: #646cff;
        }

        .select-button {
          padding: 0.5rem 1.5rem;
          background-color: #646cff;
          color: white;
          border: none;
          border-radius: 4px;
          cursor: pointer;
          font-weight: 500;
          transition: all 0.2s;
        }

        .select-button:hover:not(:disabled) {
          background-color: #4c54dd;
        }

        .select-button:disabled {
          background-color: #333;
          color: #666;
          cursor: not-allowed;
        }

        .selected-workflow {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          padding: 1rem;
          background-color: #2a2a2a;
          border: 1px solid #646cff;
          border-radius: 4px;
        }

        .selected-workflow .label {
          color: #888;
          font-weight: 500;
        }

        .selected-workflow code {
          background-color: #1a1a1a;
          padding: 0.4rem 0.75rem;
          border-radius: 3px;
          font-size: 0.9rem;
        }

        @media (prefers-color-scheme: light) {
          .form-group input {
            background-color: white;
            border-color: #ddd;
          }

          .selected-workflow {
            background-color: #f9f9f9;
            border-color: #646cff;
          }

          .selected-workflow code {
            background-color: white;
          }
        }

        @media (max-width: 768px) {
          .selector-form {
            grid-template-columns: 1fr;
          }
        }
      `}</style>
    </div>
  )
}
