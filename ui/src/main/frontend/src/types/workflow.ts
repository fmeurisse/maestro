export interface WorkflowRevisionID {
  namespace: string
  id: string
  version: number
  active?: boolean
}

export interface WorkflowRevision extends WorkflowRevisionID {
  name: string
  description: string
  yamlSource: string
  createdAt: string
  updatedAt: string
}

export interface CreateWorkflowRequest {
  yaml: string
}

export interface UpdateRevisionRequest {
  yaml: string
}

export type WorkflowListItem = WorkflowRevisionID
