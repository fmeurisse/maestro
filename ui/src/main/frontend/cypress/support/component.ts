// Cypress Component Testing support file
import './commands'

// Prevent TypeScript errors on cy object
import { mount } from 'cypress/react18'

declare global {
  namespace Cypress {
    interface Chainable {
      mount: typeof mount
    }
  }
}

Cypress.Commands.add('mount', mount)
