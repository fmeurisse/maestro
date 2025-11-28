/**
 * JSON formatting utilities for displaying execution data
 */

/**
 * Format an object as pretty-printed JSON
 */
export function formatJSON(obj: any): string {
  try {
    return JSON.stringify(obj, null, 2)
  } catch (error) {
    return String(obj)
  }
}

/**
 * Truncate long JSON strings for display
 */
export function truncateJSON(obj: any, maxLength: number = 100): string {
  const json = formatJSON(obj)
  if (json.length <= maxLength) {
    return json
  }
  return json.substring(0, maxLength) + '...'
}

/**
 * Check if a value is a JSON object
 */
export function isJSONObject(value: any): boolean {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

/**
 * Check if a value is a JSON array
 */
export function isJSONArray(value: any): boolean {
  return Array.isArray(value)
}
