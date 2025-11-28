export type ExecutionStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type StepStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'

export interface ExecutionRequest {
  namespace: string
  id: string
  version: number
  parameters: Record<string, any>
}

export interface ExecutionResponse {
  executionId: string
  status: ExecutionStatus
  revisionId: {
    namespace: string
    id: string
    version: number
  }
  inputParameters: Record<string, any>
  startedAt: string
  _links: {
    self: { href: string }
    workflow: { href: string }
  }
}

export interface ExecutionStepResult {
  resultId: string
  executionId: string
  stepIndex: number
  stepId: string
  stepType: string
  status: StepStatus
  inputData?: Record<string, any>
  outputData?: Record<string, any>
  errorMessage?: string
  errorDetails?: {
    errorType: string
    stackTrace: string
    stepInputs?: Record<string, any>
  }
  startedAt: string
  completedAt: string
}

export interface ExecutionDetail {
  executionId: string
  status: ExecutionStatus
  revisionId: {
    namespace: string
    id: string
    version: number
  }
  inputParameters: Record<string, any>
  startedAt: string
  completedAt?: string
  errorMessage?: string
  steps: ExecutionStepResult[]
  _links: {
    self: { href: string }
    workflow: { href: string }
  }
}

export interface ExecutionSummary {
  executionId: string
  status: ExecutionStatus
  errorMessage?: string
  revisionVersion: number
  startedAt: string
  completedAt?: string
  stepCount: number
  completedSteps: number
  failedSteps: number
}

export interface ExecutionHistoryResponse {
  executions: ExecutionSummary[]
  pagination: {
    total: number
    limit: number
    offset: number
    hasMore: boolean
  }
  _links: {
    self: { href: string }
    workflow: { href: string }
    next?: { href: string }
  }
}
