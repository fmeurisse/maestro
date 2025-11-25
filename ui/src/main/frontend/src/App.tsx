import {useEffect, useState} from 'react'
import {YamlEditor} from './components/YamlEditor'
import {WorkflowRevisions} from './components/WorkflowRevisions'
import {RevisionActions} from './components/RevisionActions'
import {workflowApi} from './services/workflowApi'
import type {WorkflowListItem} from './types/workflow'

function App() {
    const [selectedWorkflow, setSelectedWorkflow] =
        useState<WorkflowListItem | null>(null)
    const [yamlContent, setYamlContent] = useState('')
    const [isCreating, setIsCreating] = useState(false)
    const [isLoading, setIsLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [successMessage, setSuccessMessage] = useState<string | null>(null)

    // Template YAML for new workflows
    const templateYaml = `namespace: production
id: my-workflow
name: My Workflow
description: Description of the workflow
steps:
  - type: LogTask
    message: "Starting workflow"
  - type: WorkTask
    name: "process-data"
    parameters: {}`

    useEffect(() => {
        if (selectedWorkflow) {
            loadWorkflowYaml()
        }
    }, [selectedWorkflow])

    const loadWorkflowYaml = async () => {
        if (!selectedWorkflow) return

        setIsLoading(true)
        setError(null)

        try {
            const yaml = await workflowApi.getRevision(
                selectedWorkflow.namespace,
                selectedWorkflow.id,
                selectedWorkflow.version
            )
            setYamlContent(yaml)
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load workflow')
        } finally {
            setIsLoading(false)
        }
    }

    const handleCreateWorkflow = async () => {
        setIsLoading(true)
        setError(null)
        setSuccessMessage(null)

        try {
            const result = await workflowApi.createWorkflow(yamlContent)
            setSuccessMessage(
                `Workflow created: ${result.namespace}/${result.id} v${result.version}`
            )
            setIsCreating(false)
            setSelectedWorkflow(result)
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to create workflow')
        } finally {
            setIsLoading(false)
        }
    }

    const handleCreateRevision = async () => {
        if (!selectedWorkflow) return

        setIsLoading(true)
        setError(null)
        setSuccessMessage(null)

        try {
            const result = await workflowApi.createRevision(
                selectedWorkflow.namespace,
                selectedWorkflow.id,
                yamlContent
            )
            setSuccessMessage(
                `Revision created: ${result.namespace}/${result.id} v${result.version}`
            )
            setSelectedWorkflow(result)
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to create revision')
        } finally {
            setIsLoading(false)
        }
    }

    const handleStartCreate = () => {
        setIsCreating(true)
        setSelectedWorkflow(null)
        setYamlContent(templateYaml)
        setError(null)
        setSuccessMessage(null)
    }

    const handleCancelCreate = () => {
        setIsCreating(false)
        setYamlContent('')
    }

    const handleActionComplete = () => {
        setSuccessMessage('Action completed successfully')
        if (selectedWorkflow) {
            // Reload the workflow to get updated state
            setTimeout(() => loadWorkflowYaml(), 500)
        }
    }

    return (
        <div className="app">
            <header className="app-header">
                <h1>🎭 Maestro - Workflow Management</h1>
                <button onClick={handleStartCreate} disabled={isCreating || isLoading}>
                    + Create New Workflow
                </button>
            </header>

            {error && (
                <div className="message error">
                    <strong>Error:</strong> {error}
                </div>
            )}

            {successMessage && (
                <div className="message success">
                    <strong>Success:</strong> {successMessage}
                </div>
            )}

            <div className="app-content">
                {isCreating ? (
                    <div className="create-workflow">
                        <div className="create-header">
                            <h2>Create New Workflow</h2>
                            <div className="button-group">
                                <button onClick={handleCancelCreate} disabled={isLoading}>
                                    Cancel
                                </button>
                                <button
                                    onClick={handleCreateWorkflow}
                                    disabled={isLoading || !yamlContent.trim()}
                                    className="button-primary"
                                >
                                    {isLoading ? 'Creating...' : 'Create Workflow'}
                                </button>
                            </div>
                        </div>
                        <YamlEditor value={yamlContent} onChange={setYamlContent}/>
                    </div>
                ) : selectedWorkflow ? (
                    <div className="workflow-details">
                        <div className="sidebar">
                            <WorkflowRevisions
                                namespace={selectedWorkflow.namespace}
                                id={selectedWorkflow.id}
                                onSelectRevision={setSelectedWorkflow}
                                selectedRevision={selectedWorkflow}
                            />
                        </div>

                        <div className="main-content">
                            <div className="editor-section">
                                <div className="editor-header">
                                    <h2>
                                        {selectedWorkflow.namespace}/{selectedWorkflow.id} v
                                        {selectedWorkflow.version}
                                    </h2>
                                    <button
                                        onClick={handleCreateRevision}
                                        disabled={isLoading}
                                        className="button-primary"
                                    >
                                        {isLoading ? 'Creating...' : 'Create New Revision'}
                                    </button>
                                </div>

                                {isLoading ? (
                                    <div className="loading">Loading...</div>
                                ) : (
                                    <YamlEditor
                                        value={yamlContent}
                                        onChange={setYamlContent}
                                        readOnly={selectedWorkflow.active}
                                    />
                                )}
                            </div>

                            <RevisionActions
                                revision={selectedWorkflow}
                                onActionComplete={handleActionComplete}
                            />
                        </div>
                    </div>
                ) : (
                    <div className="empty-state">
                        <p>Select a workflow from the list or create a new one to get started.</p>
                    </div>
                )}
            </div>

            <style>{`
        .app {
          min-height: 100vh;
          display: flex;
          flex-direction: column;
        }

        .app-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 1rem 2rem;
          background-color: #1a1a1a;
          border-bottom: 1px solid #333;
        }

        .app-header h1 {
          margin: 0;
          font-size: 1.5rem;
        }

        .message {
          padding: 1rem 2rem;
          margin: 0;
        }

        .message.error {
          background-color: #ff6b6b22;
          border-bottom: 2px solid #ff6b6b;
          color: #ff6b6b;
        }

        .message.success {
          background-color: #10b98122;
          border-bottom: 2px solid #10b981;
          color: #10b981;
        }

        .app-content {
          flex: 1;
          padding: 2rem;
        }

        .create-workflow,
        .workflow-details {
          height: calc(100vh - 200px);
          display: flex;
          flex-direction: column;
        }

        .create-header,
        .editor-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 1rem;
        }

        .create-header h2,
        .editor-header h2 {
          margin: 0;
        }

        .button-group {
          display: flex;
          gap: 0.5rem;
        }

        .button-primary {
          background-color: #646cff;
          color: white;
        }

        .button-primary:hover:not(:disabled) {
          background-color: #4c54dd;
        }

        .workflow-details {
          display: grid;
          grid-template-columns: 300px 1fr;
          gap: 2rem;
          height: calc(100vh - 200px);
        }

        .sidebar {
          display: flex;
          flex-direction: column;
          min-height: 0;
        }

        .main-content {
          display: flex;
          flex-direction: column;
          gap: 1rem;
          min-height: 0;
        }

        .editor-section {
          flex: 1;
          display: flex;
          flex-direction: column;
          min-height: 0;
        }

        .empty-state {
          display: flex;
          align-items: center;
          justify-content: center;
          height: 400px;
          color: #888;
          font-size: 1.1rem;
        }

        .loading {
          display: flex;
          align-items: center;
          justify-content: center;
          height: 400px;
          color: #888;
        }

        @media (prefers-color-scheme: light) {
          .app-header {
            background-color: white;
            border-bottom-color: #ddd;
          }
        }

        @media (max-width: 1024px) {
          .workflow-details {
            grid-template-columns: 1fr;
          }

          .sidebar {
            max-height: 300px;
          }
        }
      `}</style>
        </div>
    )
}

export default App
