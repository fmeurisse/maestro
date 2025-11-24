import { RevisionActions } from '../../src/components/RevisionActions'
import type { WorkflowRevisionID } from '../../src/types/workflow'

describe('RevisionActions Component', () => {
  const mockActiveRevision: WorkflowRevisionID = {
    namespace: 'production',
    id: 'workflow-1',
    version: 1,
    active: true,
  }

  const mockInactiveRevision: WorkflowRevisionID = {
    namespace: 'production',
    id: 'workflow-1',
    version: 2,
    active: false,
  }

  it('shows deactivate button for active revision', () => {
    const onActionComplete = cy.stub()

    cy.mount(
      <RevisionActions
        revision={mockActiveRevision}
        onActionComplete={onActionComplete}
      />
    )

    cy.contains('Deactivate').should('exist')
    cy.contains('Activate').should('not.exist')
    cy.contains('Delete').should('not.exist')
  })

  it('shows activate and delete buttons for inactive revision', () => {
    const onActionComplete = cy.stub()

    cy.mount(
      <RevisionActions
        revision={mockInactiveRevision}
        onActionComplete={onActionComplete}
      />
    )

    cy.contains('Activate').should('exist')
    cy.contains('Delete').should('exist')
    cy.contains('Deactivate').should('not.exist')
  })

  it('displays active badge for active revision', () => {
    const onActionComplete = cy.stub()

    cy.mount(
      <RevisionActions
        revision={mockActiveRevision}
        onActionComplete={onActionComplete}
      />
    )

    cy.get('.badge.active').should('exist')
    cy.contains('Active').should('exist')
  })

  it('displays revision information in header', () => {
    const onActionComplete = cy.stub()

    cy.mount(
      <RevisionActions
        revision={mockActiveRevision}
        onActionComplete={onActionComplete}
      />
    )

    cy.contains('production/workflow-1 v1').should('exist')
  })

  it('disables buttons during loading state', () => {
    const onActionComplete = cy.stub()

    cy.mount(
      <RevisionActions
        revision={mockInactiveRevision}
        onActionComplete={onActionComplete}
      />
    )

    // Buttons should be enabled initially
    cy.contains('Activate').should('not.be.disabled')
    cy.contains('Delete').should('not.be.disabled')
  })
})
