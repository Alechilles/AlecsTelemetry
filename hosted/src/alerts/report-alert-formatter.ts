import type { HostedManualReportAlert } from './alert-router.js'

function truncate(value: string, maxLength: number): string {
  if (value.length <= maxLength) {
    return value
  }
  if (maxLength <= 3) {
    return value.slice(0, maxLength)
  }
  return `${value.slice(0, maxLength - 3)}...`
}

function stringFormValue(alert: HostedManualReportAlert, key: string): string | null {
  const value = alert.envelope.formValues[key]
  if (typeof value === 'string' && value.length > 0) {
    return value
  }
  return null
}

export function formatReportAlert(alert: HostedManualReportAlert): string {
  const severity = stringFormValue(alert, 'severity')
  const attachmentKinds = alert.envelope.attachmentManifests.map((attachment) => attachment.kind)
  const lines = [
    `**${alert.project.displayName}** received a player ${alert.envelope.reportKind} report`,
    `Report ID: \`${alert.envelope.reportId}\``,
    `Project: \`${alert.envelope.projectId}\``,
    `Plugin: \`${alert.envelope.pluginIdentifier}\` @ \`${alert.envelope.pluginVersion}\``,
    `Title: ${truncate(alert.envelope.title, 250)}`,
    `Players online: \`${alert.envelope.context.onlinePlayerCount}\``,
    `Mode: \`${alert.envelope.context.runtimeMode}\``,
  ]

  if (severity) {
    lines.push(`Severity: \`${truncate(severity, 80)}\``)
  }
  if (attachmentKinds.length > 0) {
    lines.push(`Attachments: \`${truncate(attachmentKinds.join(', '), 250)}\``)
  } else {
    lines.push('Attachments: `none`')
  }

  return truncate(lines.join('\n'), 1900)
}
