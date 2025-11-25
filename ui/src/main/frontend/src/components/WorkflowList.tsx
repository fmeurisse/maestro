import { useState } from 'react'
import type { WorkflowListItem } from '../types/workflow'

interface WorkflowListProps {
  workflows: WorkflowListItem[]
  onSelect: (workflow: WorkflowListItem) => void
  selectedWorkflow?: WorkflowListItem
}

/**
 * Component to display a list of workflow revisions
 * Shows namespace, id, version, and active status for each workflow
 */
export function WorkflowList({
  workflows,
  onSelect,
  selectedWorkflow,
}: WorkflowListProps) {
  const [filter, setFilter] = useState('')

  const filteredWorkflows = workflows.filter((wf) => {
    const searchTerm = filter.toLowerCase()
    return (
      wf.namespace.toLowerCase().includes(searchTerm) ||
      wf.id.toLowerCase().includes(searchTerm) ||
      wf.version.toString().includes(searchTerm)
    )
  })

  return (
    <div className="workflow-list">
      <div className="workflow-list-header">
        <h2>Workflows ({workflows.length})</h2>
        <input
          type="text"
          placeholder="Filter by namespace, id, or version..."
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          className="filter-input"
        />
      </div>

      <div className="workflow-list-items">
        {filteredWorkflows.length === 0 ? (
          <div className="empty-state">
            {filter ? 'No workflows match your filter' : 'No workflows found'}
          </div>
        ) : (
          filteredWorkflows.map((workflow) => {
            const isSelected =
              selectedWorkflow &&
              selectedWorkflow.namespace === workflow.namespace &&
              selectedWorkflow.id === workflow.id &&
              selectedWorkflow.version === workflow.version

            return (
              <div
                key={`${workflow.namespace}-${workflow.id}-${workflow.version}`}
                className={`workflow-item ${isSelected ? 'selected' : ''}`}
                onClick={() => onSelect(workflow)}
              >
                <div className="workflow-item-header">
                  <span className="workflow-id">
                    {workflow.namespace}/{workflow.id}
                  </span>
                  <span className="workflow-version">v{workflow.version}</span>
                  {workflow.active && (
                    <span className="badge active">Active</span>
                  )}
                </div>
              </div>
            )
          })
        )}
      </div>

      <style>{`
        .workflow-list {
          display: flex;
          flex-direction: column;
          height: 100%;
        }

        .workflow-list-header {
          margin-bottom: 1rem;
        }

        .workflow-list-header h2 {
          margin-bottom: 0.5rem;
        }

        .filter-input {
          width: 100%;
          padding: 0.5rem;
          border: 1px solid #444;
          border-radius: 4px;
          background-color: #1a1a1a;
          color: inherit;
          font-size: 0.9rem;
        }

        .filter-input:focus {
          outline: none;
          border-color: #646cff;
        }

        .workflow-list-items {
          flex: 1;
          overflow-y: auto;
        }

        .workflow-item {
          padding: 0.75rem 1rem;
          margin-bottom: 0.5rem;
          background-color: #1a1a1a;
          border: 1px solid #333;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.2s;
        }

        .workflow-item:hover {
          border-color: #646cff;
          background-color: #222;
        }

        .workflow-item.selected {
          border-color: #646cff;
          background-color: #2a2a4a;
        }

        .workflow-item-header {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .workflow-id {
          flex: 1;
          font-weight: 500;
        }

        .workflow-version {
          color: #888;
          font-size: 0.9em;
        }

        .badge {
          padding: 0.25rem 0.5rem;
          border-radius: 3px;
          font-size: 0.75rem;
          font-weight: 600;
        }

        .badge.active {
          background-color: #10b981;
          color: white;
        }

        .empty-state {
          padding: 2rem;
          text-align: center;
          color: #888;
        }

        @media (prefers-color-scheme: light) {
          .filter-input {
            background-color: white;
            border-color: #ccc;
          }

          .workflow-item {
            background-color: white;
            border-color: #ddd;
          }

          .workflow-item:hover {
            background-color: #f5f5f5;
          }

          .workflow-item.selected {
            background-color: #e8e8ff;
          }
        }
      `}</style>
    </div>
  )
}
