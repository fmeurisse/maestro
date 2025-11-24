import { WorkflowList } from '../../src/components/WorkflowList'
import type { WorkflowListItem } from '../../src/types/workflow'

describe('WorkflowList Component', () => {
  const mockWorkflows: WorkflowListItem[] = [
    {
      namespace: 'production',
      id: 'workflow-1',
      version: 1,
      active: true,
    },
    {
      namespace: 'production',
      id: 'workflow-1',
      version: 2,
      active: false,
    },
    {
      namespace: 'staging',
      id: 'workflow-2',
      version: 1,
      active: true,
    },
  ]

  it('renders workflows', () => {
    const onSelect = cy.stub()

    cy.mount(
      <WorkflowList
        workflows={mockWorkflows}
        onSelect={onSelect}
      />
    )

    cy.contains('Workflows (3)').should('exist')
    cy.contains('production/workflow-1').should('exist')
    cy.contains('staging/workflow-2').should('exist')
  })

  it('displays active badge for active workflows', () => {
    const onSelect = cy.stub()

    cy.mount(
      <WorkflowList
        workflows={mockWorkflows}
        onSelect={onSelect}
      />
    )

    // Should have 2 active badges (2 active workflows)
    cy.get('.badge.active').should('have.length', 2)
  })

  it('calls onSelect when a workflow is clicked', () => {
    const onSelect = cy.stub().as('onSelect')

    cy.mount(
      <WorkflowList
        workflows={mockWorkflows}
        onSelect={onSelect}
      />
    )

    cy.contains('production/workflow-1').click()

    cy.get('@onSelect').should('have.been.calledOnce')
  })

  it('filters workflows by search term', () => {
    const onSelect = cy.stub()

    cy.mount(
      <WorkflowList
        workflows={mockWorkflows}
        onSelect={onSelect}
      />
    )

    // Type in the filter input
    cy.get('.filter-input').type('staging')

    // Should only show staging workflow
    cy.contains('staging/workflow-2').should('exist')
    cy.contains('production/workflow-1').should('not.exist')
  })

  it('shows empty state when no workflows match filter', () => {
    const onSelect = cy.stub()

    cy.mount(
      <WorkflowList
        workflows={mockWorkflows}
        onSelect={onSelect}
      />
    )

    cy.get('.filter-input').type('nonexistent')

    cy.contains('No workflows match your filter').should('exist')
  })

  it('highlights selected workflow', () => {
    const onSelect = cy.stub()
    const selected = mockWorkflows[0]

    cy.mount(
      <WorkflowList
        workflows={mockWorkflows}
        onSelect={onSelect}
        selectedWorkflow={selected}
      />
    )

    cy.get('.workflow-item.selected').should('have.length', 1)
  })
})
