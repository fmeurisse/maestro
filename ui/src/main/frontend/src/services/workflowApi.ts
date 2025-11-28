import type { WorkflowRevisionID, WorkflowListItem } from '../types/workflow'
import type {
  ExecutionRequest,
  ExecutionResponse,
  ExecutionDetail,
  ExecutionHistoryResponse,
  ExecutionStatus,
} from '../types/execution'

const API_BASE = '/api/workflows'
const EXECUTION_API_BASE = '/api/executions'

/**
 * Workflow Management API Client
 * Provides methods to interact with the Maestro workflow management backend.
 */
export class WorkflowApi {
  /**
   * Creates a new workflow with version 1
   */
  async createWorkflow(yaml: string): Promise<WorkflowRevisionID> {
    const response = await fetch(API_BASE, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/yaml',
      },
      body: yaml,
    })

    if (!response.ok) {
      throw new Error(`Failed to create workflow: ${response.statusText}`)
    }

    const responseYaml = await response.text()
    return this.parseWorkflowRevisionID(responseYaml)
  }

  /**
   * Creates a new revision of an existing workflow
   */
  async createRevision(
    namespace: string,
    id: string,
    yaml: string
  ): Promise<WorkflowRevisionID> {
    const response = await fetch(`${API_BASE}/${namespace}/${id}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/yaml',
      },
      body: yaml,
    })

    if (!response.ok) {
      throw new Error(`Failed to create revision: ${response.statusText}`)
    }

    const responseYaml = await response.text()
    return this.parseWorkflowRevisionID(responseYaml)
  }

  /**
   * Lists all revisions of a workflow
   */
  async listRevisions(
    namespace: string,
    id: string,
    activeOnly = false
  ): Promise<WorkflowListItem[]> {
    const url = activeOnly
      ? `${API_BASE}/${namespace}/${id}?active=true`
      : `${API_BASE}/${namespace}/${id}`

    const response = await fetch(url, {
      headers: {
        Accept: 'application/yaml',
      },
    })

    if (response.status === 404) {
      return []
    }

    if (!response.ok) {
      throw new Error(`Failed to list revisions: ${response.statusText}`)
    }

    const responseYaml = await response.text()
    return this.parseRevisionList(responseYaml)
  }

  /**
   * Gets a specific workflow revision with its YAML source
   */
  async getRevision(
    namespace: string,
    id: string,
    version: number
  ): Promise<string> {
    const response = await fetch(`${API_BASE}/${namespace}/${id}/${version}`, {
      headers: {
        Accept: 'application/yaml',
      },
    })

    if (!response.ok) {
      throw new Error(`Failed to get revision: ${response.statusText}`)
    }

    return await response.text()
  }

  /**
   * Activates a workflow revision
   */
  async activateRevision(
    namespace: string,
    id: string,
    version: number
  ): Promise<WorkflowRevisionID> {
    const response = await fetch(
      `${API_BASE}/${namespace}/${id}/${version}/activate`,
      {
        method: 'POST',
        headers: {
          Accept: 'application/yaml',
        },
      }
    )

    if (!response.ok) {
      throw new Error(`Failed to activate revision: ${response.statusText}`)
    }

    const responseYaml = await response.text()
    return this.parseWorkflowRevisionID(responseYaml)
  }

  /**
   * Deactivates a workflow revision
   */
  async deactivateRevision(
    namespace: string,
    id: string,
    version: number
  ): Promise<WorkflowRevisionID> {
    const response = await fetch(
      `${API_BASE}/${namespace}/${id}/${version}/deactivate`,
      {
        method: 'POST',
        headers: {
          Accept: 'application/yaml',
        },
      }
    )

    if (!response.ok) {
      throw new Error(`Failed to deactivate revision: ${response.statusText}`)
    }

    const responseYaml = await response.text()
    return this.parseWorkflowRevisionID(responseYaml)
  }

  /**
   * Updates an inactive workflow revision
   */
  async updateRevision(
    namespace: string,
    id: string,
    version: number,
    yaml: string
  ): Promise<WorkflowRevisionID> {
    const response = await fetch(`${API_BASE}/${namespace}/${id}/${version}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/yaml',
        Accept: 'application/yaml',
      },
      body: yaml,
    })

    if (!response.ok) {
      throw new Error(`Failed to update revision: ${response.statusText}`)
    }

    const responseYaml = await response.text()
    return this.parseWorkflowRevisionID(responseYaml)
  }

  /**
   * Deletes a specific workflow revision
   */
  async deleteRevision(
    namespace: string,
    id: string,
    version: number
  ): Promise<void> {
    const response = await fetch(`${API_BASE}/${namespace}/${id}/${version}`, {
      method: 'DELETE',
    })

    if (!response.ok) {
      throw new Error(`Failed to delete revision: ${response.statusText}`)
    }
  }

  /**
   * Deletes all revisions of a workflow
   */
  async deleteWorkflow(namespace: string, id: string): Promise<void> {
    const response = await fetch(`${API_BASE}/${namespace}/${id}`, {
      method: 'DELETE',
    })

    if (!response.ok) {
      throw new Error(`Failed to delete workflow: ${response.statusText}`)
    }
  }

  /**
   * Lists all workflows in a namespace
   */
  async listWorkflows(namespace: string): Promise<Array<{ namespace: string; id: string }>> {
    const response = await fetch(`${API_BASE}/${namespace}`, {
      headers: {
        Accept: 'application/json',
      },
    })

    if (!response.ok) {
      throw new Error(`Failed to list workflows: ${response.statusText}`)
    }

    return await response.json()
  }

  /**
   * Launches a workflow execution
   */
  async executeWorkflow(request: ExecutionRequest): Promise<ExecutionResponse> {
    const response = await fetch(EXECUTION_API_BASE, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Failed to execute workflow: ${response.statusText} - ${errorText}`)
    }

    return await response.json()
  }

  /**
   * Gets execution details
   */
  async getExecution(executionId: string): Promise<ExecutionDetail> {
    const response = await fetch(`${EXECUTION_API_BASE}/${executionId}`, {
      headers: {
        Accept: 'application/json',
      },
    })

    if (!response.ok) {
      throw new Error(`Failed to get execution: ${response.statusText}`)
    }

    return await response.json()
  }

  /**
   * Gets execution history for a specific workflow
   */
  async getExecutionHistory(
    namespace: string,
    id: string,
    filters?: {
      version?: number
      status?: ExecutionStatus
      limit?: number
      offset?: number
    }
  ): Promise<ExecutionHistoryResponse> {
    const params = new URLSearchParams()
    if (filters?.version !== undefined) params.append('version', filters.version.toString())
    if (filters?.status) params.append('status', filters.status)
    if (filters?.limit !== undefined) params.append('limit', filters.limit.toString())
    if (filters?.offset !== undefined) params.append('offset', filters.offset.toString())

    const queryString = params.toString()
    const url = queryString
      ? `${API_BASE}/${namespace}/${id}/executions?${queryString}`
      : `${API_BASE}/${namespace}/${id}/executions`

    const response = await fetch(url, {
      headers: {
        Accept: 'application/json',
      },
    })

    if (!response.ok) {
      throw new Error(`Failed to get execution history: ${response.statusText}`)
    }

    return await response.json()
  }

  /**
   * Parses a YAML response into a WorkflowRevisionID
   */
  private parseWorkflowRevisionID(yaml: string): WorkflowRevisionID {
    const lines = yaml.split('\n')
    const result: Partial<WorkflowRevisionID> = {}

    for (const line of lines) {
      const [key, ...valueParts] = line.split(':')
      const value = valueParts.join(':').trim()

      if (key === 'namespace') result.namespace = value
      else if (key === 'id') result.id = value
      else if (key === 'version') result.version = parseInt(value, 10)
      else if (key === 'active') result.active = value === 'true'
    }

    if (!result.namespace || !result.id || result.version === undefined) {
      throw new Error('Invalid workflow revision ID response')
    }

    return result as WorkflowRevisionID
  }

  /**
   * Parses a YAML list response into an array of WorkflowListItems
   */
  private parseRevisionList(yaml: string): WorkflowListItem[] {
    const lines = yaml.split('\n')
    const revisions: WorkflowListItem[] = []
    let current: Partial<WorkflowListItem> = {}

    for (const line of lines) {
      const trimmed = line.trim()

      if (trimmed.startsWith('-')) {
        if (current.namespace && current.id && current.version !== undefined) {
          revisions.push(current as WorkflowListItem)
        }
        current = {}
        const [key, ...valueParts] = trimmed.substring(1).trim().split(':')
        const value = valueParts.join(':').trim()
        if (key === 'namespace') current.namespace = value
      } else if (trimmed) {
        const [key, ...valueParts] = trimmed.split(':')
        const value = valueParts.join(':').trim()

        if (key === 'namespace') current.namespace = value
        else if (key === 'id') current.id = value
        else if (key === 'version') current.version = parseInt(value, 10)
        else if (key === 'active') current.active = value === 'true'
      }
    }

    if (current.namespace && current.id && current.version !== undefined) {
      revisions.push(current as WorkflowListItem)
    }

    return revisions
  }
}

// Export singleton instance
export const workflowApi = new WorkflowApi()
