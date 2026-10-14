export type SystemInfo = {
  app: string
  dataMode: 'demo' | 'real'
  databaseUp: boolean
}

export async function fetchSystemInfo(signal?: AbortSignal): Promise<SystemInfo> {
  const response = await fetch('/api/system/info', { signal })
  if (!response.ok) {
    throw new Error(`API responded with ${response.status}`)
  }
  return response.json() as Promise<SystemInfo>
}
