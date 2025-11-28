/**
 * Runtime Configuration
 *
 * This configuration is loaded at runtime from window.__RUNTIME_CONFIG__
 * which is injected by the backend via /config.js
 *
 * This allows the API URL to be configured via environment variables
 * at deployment time rather than at build time.
 */

export interface RuntimeConfig {
  apiUrl: string
}

declare global {
  interface Window {
    __RUNTIME_CONFIG__?: RuntimeConfig
  }
}

/**
 * Gets the runtime configuration
 * Falls back to relative URLs if config is not available (e.g., during development)
 */
export function getRuntimeConfig(): RuntimeConfig {
  // If runtime config is available (production), use it
  if (window.__RUNTIME_CONFIG__) {
    return window.__RUNTIME_CONFIG__
  }

  // Fallback for development mode (Vite dev server)
  // Uses relative URLs which work when UI and API are on same origin
  return {
    apiUrl: '',
  }
}

/**
 * Gets the base API URL for the workflow API
 */
export function getApiBaseUrl(): string {
  const config = getRuntimeConfig()
  return config.apiUrl
}
