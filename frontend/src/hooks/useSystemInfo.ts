import { useEffect, useState } from 'react'
import { fetchSystemInfo, type SystemInfo } from '../api/system'

export type SystemStatus =
  | { state: 'loading' }
  | { state: 'ready'; info: SystemInfo }
  | { state: 'error'; message: string }

export function useSystemInfo(): SystemStatus {
  const [status, setStatus] = useState<SystemStatus>({ state: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    fetchSystemInfo(controller.signal)
      .then((info) => setStatus({ state: 'ready', info }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        const message = error instanceof Error ? error.message : 'Unknown error'
        setStatus({ state: 'error', message })
      })
    return () => controller.abort()
  }, [])

  return status
}
