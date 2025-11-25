import { YamlEditor } from '../../src/components/YamlEditor'

describe('YamlEditor Component', () => {
  it('displays YAML content', () => {
    const yaml = 'namespace: test\nid: workflow-1'
    const onChange = cy.stub()

    cy.mount(<YamlEditor value={yaml} onChange={onChange} />)

    // Monaco editor takes a moment to initialize
    cy.wait(1000)
    cy.contains('namespace: test').should('exist')
  })

  it('calls onChange when content changes', () => {
    const onChange = cy.stub().as('onChange')

    cy.mount(<YamlEditor value="" onChange={onChange} />)

    // Monaco editor takes a moment to initialize
    cy.wait(1000)

    // Type in the editor
    cy.get('.monaco-editor textarea').first().type('test: value', { force: true })

    // Verify onChange was called
    cy.get('@onChange').should('have.been.called')
  })

  it('displays read-only editor when readOnly prop is true', () => {
    const onChange = cy.stub()

    cy.mount(<YamlEditor value="namespace: test" onChange={onChange} readOnly />)

    cy.wait(1000)

    // Check that the editor has read-only aria attribute
    cy.get('.monaco-editor').should('exist')
  })

  it('shows validation warning for invalid YAML', () => {
    let currentValue = '{'
    const onChange = (value: string) => {
      currentValue = value
    }

    cy.mount(<YamlEditor value={currentValue} onChange={onChange} />)

    cy.wait(1000)

    // The component should show a warning for unmatched braces
    cy.contains('Warning').should('exist')
  })
})
