/**
 * Time formatting utilities for execution timestamps and durations
 */

/**
 * Format ISO timestamp to relative time ("2 minutes ago")
 */
export function formatRelativeTime(isoTimestamp: string): string {
  const now = new Date().getTime()
  const time = new Date(isoTimestamp).getTime()
  const diff = now - time

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) {
    return seconds === 1 ? '1 second ago' : `${seconds} seconds ago`
  } else if (minutes < 60) {
    return minutes === 1 ? '1 minute ago' : `${minutes} minutes ago`
  } else if (hours < 24) {
    return hours === 1 ? '1 hour ago' : `${hours} hours ago`
  } else {
    return days === 1 ? '1 day ago' : `${days} days ago`
  }
}

/**
 * Format duration between two timestamps ("2m 34s" or "1h 5m")
 */
export function formatDuration(startISO: string, endISO: string): string {
  const start = new Date(startISO).getTime()
  const end = new Date(endISO).getTime()
  const diff = end - start

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) {
    const remainingMinutes = minutes % 60
    return remainingMinutes > 0 ? `${hours}h ${remainingMinutes}m` : `${hours}h`
  } else if (minutes > 0) {
    const remainingSeconds = seconds % 60
    return remainingSeconds > 0 ? `${minutes}m ${remainingSeconds}s` : `${minutes}m`
  } else {
    return `${seconds}s`
  }
}

/**
 * Format ISO timestamp to local date/time
 */
export function formatDateTime(isoTimestamp: string): string {
  const date = new Date(isoTimestamp)
  return date.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}
