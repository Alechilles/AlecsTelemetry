export class DuplicateAlertSuppressor {
  private readonly lastAlertAtByFingerprint = new Map<string, number>()

  shouldDispatch(projectId: string, fingerprint: string, windowSeconds: number, nowMs: number): boolean {
    if (windowSeconds <= 0) {
      return true
    }
    const key = `${projectId.toLowerCase()}|${fingerprint.toLowerCase()}`
    const previous = this.lastAlertAtByFingerprint.get(key)
    const windowMs = windowSeconds * 1000
    if (previous !== undefined && nowMs - previous < windowMs) {
      return false
    }
    this.lastAlertAtByFingerprint.set(key, nowMs)
    return true
  }
}
