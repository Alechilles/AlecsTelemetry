import type { HostedCrashAlert } from './alert-router.js'

function truncate(value: string, maxLength: number): string {
  if (value.length <= maxLength) {
    return value
  }
  if (maxLength <= 3) {
    return value.slice(0, maxLength)
  }
  return `${value.slice(0, maxLength - 3)}...`
}

function firstStackFrame(alert: HostedCrashAlert): string {
  return alert.envelope.throwable.stack[0] ?? '<no stack frame>'
}

function firstThrowableLine(alert: HostedCrashAlert): string {
  const type = alert.envelope.throwable.type
  const message = alert.envelope.throwable.message
  return message === '<empty>' ? type : `${type}: ${message}`
}

export function formatCrashAlert(alert: HostedCrashAlert): string {
  const lines = [
    `**${alert.project.displayName}** reported a crash`,
    `Project: \`${alert.envelope.projectId}\``,
    `Plugin: \`${alert.envelope.pluginIdentifier}\` @ \`${alert.envelope.pluginVersion}\``,
    `Fingerprint: \`${alert.envelope.fingerprint}\``,
    `Occurrences: \`${alert.envelope.occurrenceCount}\``,
    `Source: \`${alert.envelope.source}\``,
    `Thread: \`${alert.envelope.threadName}\``,
    `Throwable: ${truncate(firstThrowableLine(alert), 300)}`,
    `Top frame: \`${truncate(firstStackFrame(alert), 250)}\``,
    `Hytale build: \`${alert.envelope.runtime.hytaleBuild}\``,
    `Server version: \`${alert.envelope.runtime.serverVersion}\``,
  ]

  if (alert.envelope.worldName) {
    lines.push(`World: \`${alert.envelope.worldName}\``)
  }
  if (alert.envelope.breadcrumbs.length > 0) {
    const breadcrumb = alert.envelope.breadcrumbs.at(-1)
    if (breadcrumb) {
      lines.push(`Latest breadcrumb: \`${truncate(`${breadcrumb.category}: ${breadcrumb.detail}`, 250)}\``)
    }
  }

  return truncate(lines.join('\n'), 1900)
}
