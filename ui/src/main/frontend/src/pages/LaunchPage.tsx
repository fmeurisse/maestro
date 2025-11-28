import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { WorkflowSelector } from '../components/WorkflowSelector'
import { ParameterEditor } from '../components/ParameterEditor'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { workflowApi } from '../services/workflowApi'
import type { WorkflowRevisionID } from '../types/workflow'

export function LaunchPage() {
  const navigate = useNavigate()
  const [selectedWorkflow, setSelectedWorkflow] = useState<WorkflowRevisionID | null>(null)
  const [parameters, setParameters] = useState<Record<string, any>>({})
  const [editMode, setEditMode] = useState<'form' | 'json'>('form')
  const [isLaunching, setIsLaunching] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const handleWorkflowSelect = (workflow: WorkflowRevisionID) => {
    setSelectedWorkflow(workflow)
    setError(null)
    setSuccess(null)
  }

  const handleLaunch = async () => {
    if (!selectedWorkflow) {
      setError('Please select a workflow')
      return
    }

    setIsLaunching(true)
    setError(null)
    setSuccess(null)

    try {
      const response = await workflowApi.executeWorkflow({
        namespace: selectedWorkflow.namespace,
        id: selectedWorkflow.id,
        version: selectedWorkflow.version,
        parameters,
      })

      setSuccess(`Execution launched successfully! ID: ${response.executionId}`)

      // Navigate to execution detail after 2 seconds
      setTimeout(() => {
        navigate(`/executions/${response.executionId}`)
      }, 2000)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to launch execution')
    } finally {
      setIsLaunching(false)
    }
  }

  const canLaunch = selectedWorkflow && !isLaunching

  return (
    <div className="launch-page">
      <div className="page-header">
        <h2>Launch Workflow Execution</h2>
      </div>

      {error && (
        <div className="message error">
          <strong>Error:</strong> {error}
        </div>
      )}

      {success && (
        <div className="message success">
          <strong>Success:</strong> {success}
        </div>
      )}

      <div className="launch-content">
        <div className="section">
          <h3>Step 1: Select Workflow</h3>
          <WorkflowSelector
            onSelect={handleWorkflowSelect}
            selectedWorkflow={selectedWorkflow || undefined}
          />
        </div>

        {selectedWorkflow && (
          <div className="section">
            <h3>Step 2: Configure Parameters</h3>
            <ParameterEditor
              parameters={parameters}
              onChange={setParameters}
              mode={editMode}
              onModeChange={setEditMode}
            />
          </div>
        )}

        {selectedWorkflow && (
          <div className="launch-actions">
            <button
              className="launch-button"
              onClick={handleLaunch}
              disabled={!canLaunch}
            >
              {isLaunching ? 'Launching...' : '🚀 Launch Execution'}
            </button>
          </div>
        )}

        {isLaunching && (
          <LoadingSpinner message="Launching execution..." />
        )}
      </div>

      <style>{`
        .launch-page {
          display: flex;
          flex-direction: column;
          gap: 1.5rem;
          max-width: 1200px;
        }

        .page-header h2 {
          margin: 0;
          font-size: 1.8rem;
        }

        .message {
          padding: 1rem;
          border-radius: 4px;
        }

        .message.error {
          background-color: #ff6b6b22;
          border: 1px solid #ff6b6b;
          color: #ff6b6b;
        }

        .message.success {
          background-color: #10b98122;
          border: 1px solid #10b981;
          color: #10b981;
        }

        .launch-content {
          display: flex;
          flex-direction: column;
          gap: 2rem;
        }

        .section {
          padding: 1.5rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 6px;
        }

        .section h3 {
          margin: 0 0 1rem 0;
          font-size: 1.2rem;
          color: #ccc;
        }

        .launch-actions {
          display: flex;
          justify-content: center;
          padding: 2rem 0;
        }

        .launch-button {
          padding: 1rem 3rem;
          background-color: #646cff;
          border: none;
          border-radius: 6px;
          color: white;
          font-size: 1.1rem;
          font-weight: 600;
          cursor: pointer;
          transition: all 0.2s;
          box-shadow: 0 4px 12px rgba(100, 108, 255, 0.3);
        }

        .launch-button:hover:not(:disabled) {
          background-color: #4c54dd;
          transform: translateY(-2px);
          box-shadow: 0 6px 16px rgba(100, 108, 255, 0.4);
        }

        .launch-button:disabled {
          background-color: #333;
          color: #666;
          cursor: not-allowed;
          box-shadow: none;
          transform: none;
        }

        @media (prefers-color-scheme: light) {
          .section {
            background-color: white;
            border-color: #ddd;
          }

          .section h3 {
            color: #333;
          }
        }

        @media (max-width: 768px) {
          .launch-button {
            width: 100%;
            padding: 1rem 2rem;
          }
        }
      `}</style>
    </div>
  )
}
