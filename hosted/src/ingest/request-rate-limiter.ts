export class RequestRateLimiter {
  private readonly timestampsByProject = new Map<string, number[]>()

  allow(projectId: string, limitPerMinute: number, nowMs: number): boolean {
    const safeLimit = Math.max(1, limitPerMinute)
    const windowStart = nowMs - 60_000
    const projectKey = projectId.toLowerCase()
    const timestamps = this.timestampsByProject.get(projectKey) ?? []
    const recent = timestamps.filter((timestamp) => timestamp >= windowStart)
    if (recent.length >= safeLimit) {
      this.timestampsByProject.set(projectKey, recent)
      return false
    }
    recent.push(nowMs)
    this.timestampsByProject.set(projectKey, recent)
    return true
  }
}
