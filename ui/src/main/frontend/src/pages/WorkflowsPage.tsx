import {useEffect, useState} from 'react'
import {YamlEditor} from '../components/YamlEditor'
import {WorkflowRevisions} from '../components/WorkflowRevisions'
import {RevisionActions} from '../components/RevisionActions'
import {WorkflowList} from '../components/WorkflowList'
import {workflowApi} from '../services/workflowApi'
import type {WorkflowListItem} from '../types/workflow'

export function WorkflowsPage() {
    const [namespace, setNamespace] = useState('production')
    const [workflows, setWorkflows] = useState<Array<{ namespace: string; id: string }>>([])
    const [selectedWorkflowId, setSelectedWorkflowId] = useState<{ namespace: string; id: string } | null>(null)
    const [selectedWorkflow, setSelectedWorkflow] =
        useState<WorkflowListItem | null>(null)
    const [yamlContent, setYamlContent] = useState('')
    const [isCreating, setIsCreating] = useState(false)
    const [isLoading, setIsLoading] = useState(false)
    const [isLoadingWorkflows, setIsLoadingWorkflows] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [successMessage, setSuccessMessage] = useState<string | null>(null)

    // Template YAML for new workflows
    const templateYaml = `namespace: ${namespace}
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
        loadWorkflows()
    }, [namespace])

    useEffect(() => {
        if (selectedWorkflow) {
            loadWorkflowYaml()
        }
    }, [selectedWorkflow])

    const loadWorkflows = async () => {
        setIsLoadingWorkflows(true)
        try {
            const data = await workflowApi.listWorkflows(namespace)
            setWorkflows(data)
        } catch (err) {
            // Silently fail - empty list is OK
            setWorkflows([])
        } finally {
            setIsLoadingWorkflows(false)
        }
    }

    const handleSelectWorkflowFromList = (selectedNamespace: string, id: string) => {
        setSelectedWorkflowId({ namespace: selectedNamespace, id })
        setSelectedWorkflow(null)
        setIsCreating(false)
    }

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
        <div className="workflows-page">
            <div className="workflows-header">
                <h2>Workflow Management</h2>
                <div className="header-actions">
                    <form onSubmit={(e) => { e.preventDefault(); loadWorkflows(); }} className="namespace-form">
                        <input
                            type="text"
                            value={namespace}
                            onChange={(e) => setNamespace(e.target.value)}
                            placeholder="Namespace"
                            className="namespace-input"
                        />
                        <button type="submit" className="button-secondary">Load</button>
                    </form>
                    <button onClick={handleStartCreate} disabled={isCreating || isLoading}>
                        + Create New Workflow
                    </button>
                </div>
            </div>

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

            <div className="workflows-content">
                <div className="workflows-sidebar">
                    <WorkflowList
                        workflows={workflows.map(w => ({ ...w, version: 0, active: false }))}
                        onSelect={(wf) => handleSelectWorkflowFromList(wf.namespace, wf.id)}
                        selectedWorkflow={selectedWorkflowId ? { ...selectedWorkflowId, version: 0, active: false } : undefined}
                    />
                </div>

                <div className="workflows-main">
                {isCreating ? (
                    <div className="create-workflow">
                        <div className="create-header">
                            <h3>Create New Workflow</h3>
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
                ) : selectedWorkflowId || selectedWorkflow ? (
                    <div className="workflow-details">
                        <div className="sidebar">
                            <WorkflowRevisions
                                namespace={selectedWorkflowId?.namespace || selectedWorkflow!.namespace}
                                id={selectedWorkflowId?.id || selectedWorkflow!.id}
                                onSelectRevision={setSelectedWorkflow}
                                selectedRevision={selectedWorkflow || undefined}
                            />
                        </div>

                        <div className="main-content">
                            <div className="editor-section">
                                <div className="editor-header">
                                    <h3>
                                        {selectedWorkflow.namespace}/{selectedWorkflow.id} v
                                        {selectedWorkflow.version}
                                    </h3>
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
            </div>

            <style>{`
        .workflows-page {
          display: flex;
          flex-direction: column;
          gap: 1.5rem;
          height: 100%;
        }

        .workflows-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          flex-wrap: wrap;
          gap: 1rem;
        }

        .workflows-header h2 {
          margin: 0;
          font-size: 1.8rem;
        }

        .header-actions {
          display: flex;
          gap: 1rem;
          align-items: center;
        }

        .namespace-form {
          display: flex;
          gap: 0.5rem;
        }

        .namespace-input {
          padding: 0.5rem 0.75rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
          color: inherit;
          font-size: 0.9rem;
        }

        .namespace-input:focus {
          outline: none;
          border-color: #646cff;
        }

        .button-secondary {
          padding: 0.5rem 1rem;
          background-color: #333;
          border: none;
          border-radius: 4px;
          color: white;
          cursor: pointer;
          transition: all 0.2s;
        }

        .button-secondary:hover {
          background-color: #444;
        }

        .workflows-content {
          display: grid;
          grid-template-columns: 300px 1fr;
          gap: 1.5rem;
          flex: 1;
          min-height: 0;
        }

        .workflows-sidebar {
          min-height: 0;
        }

        .workflows-main {
          min-height: 0;
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

        .workflows-content {
          flex: 1;
          min-height: 0;
        }

        .create-workflow,
        .workflow-details {
          height: 100%;
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

        .create-header h3,
        .editor-header h3 {
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
          grid-template-columns: 280px 1fr;
          gap: 1.5rem;
          height: 100%;
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

        @media (prefers-color-scheme: light) {
          .namespace-input {
            background-color: white;
            border-color: #ddd;
          }
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

        @media (max-width: 1024px) {
          .workflows-content {
            grid-template-columns: 1fr;
          }

          .workflows-sidebar {
            max-height: 250px;
          }

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
