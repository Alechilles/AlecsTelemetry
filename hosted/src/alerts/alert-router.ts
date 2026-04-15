import type { TelemetryCrashEnvelope } from '../contract/crash-envelope.js'
import type { HostedProjectConfig } from '../projects/project-registry.js'

export interface HostedCrashAlert {
  readonly project: HostedProjectConfig
  readonly envelope: TelemetryCrashEnvelope
}

export interface CrashAlertRouteResult {
  readonly dispatched: boolean
  readonly detail: string
}

export interface CrashAlertRouter {
  routeCrashAlert(alert: HostedCrashAlert): Promise<CrashAlertRouteResult>
}

export class NoopCrashAlertRouter implements CrashAlertRouter {
  async routeCrashAlert(): Promise<CrashAlertRouteResult> {
    return {
      dispatched: false,
      detail: 'No alert router configured.',
    }
  }
}
