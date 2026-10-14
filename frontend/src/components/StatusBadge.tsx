import type { SystemStatus } from '../hooks/useSystemInfo'

type Props = { status: SystemStatus }

export function StatusBadge({ status }: Props) {
  if (status.state === 'loading') {
    return <span className="badge badge--muted">Connecting to API…</span>
  }
  if (status.state === 'error') {
    return <span className="badge badge--error">API unavailable</span>
  }
  const { dataMode, databaseUp } = status.info
  if (!databaseUp) {
    return <span className="badge badge--error">Database unavailable</span>
  }
  return (
    <span className="badge badge--ok">
      {dataMode === 'demo' ? 'Demo data' : 'Live data'}
    </span>
  )
}
