import { useEffect, useLayoutEffect, useRef, useState } from 'react'

export type RequestState<T> =
  | { state: 'idle' }
  | { state: 'loading'; previous: T | null }
  | { state: 'done'; data: T }
  | { state: 'error'; message: string }

type Response<T> = { key: string; ok: true; data: T } | { key: string; ok: false; message: string }

/**
 * Runs `run` whenever `key` changes, aborting the previous request. A `null` key means
 * "nothing to ask" (idle).
 *
 * `key` must describe every input of `run` (e.g. the origin and mode): a request is sent
 * only when the key changes, not every time the caller passes a new `run` function.
 *
 * Like useProductSearch, only responses are stored; "loading" is derived by comparing the
 * current key with the key of the last response. While loading, the previous data is kept
 * so the UI does not flash empty between requests.
 */
export function useRequest<T>(key: string | null, run: (signal: AbortSignal) => Promise<T>): RequestState<T> {
  const [response, setResponse] = useState<Response<T> | null>(null)

  // "Latest ref" pattern: the effect below always calls the newest `run` without listing it
  // as a dependency. Layout effects run before regular effects, so the ref is fresh in time.
  const runRef = useRef(run)
  useLayoutEffect(() => {
    runRef.current = run
  })

  useEffect(() => {
    if (key === null) return
    const controller = new AbortController()
    runRef.current(controller.signal)
      .then((data) => setResponse({ key, ok: true, data }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        const message = error instanceof Error ? error.message : 'Unknown error'
        setResponse({ key, ok: false, message })
      })
    return () => controller.abort()
  }, [key])

  if (key === null) return { state: 'idle' }
  if (response?.key !== key) {
    return { state: 'loading', previous: response?.ok ? response.data : null }
  }
  return response.ok ? { state: 'done', data: response.data } : { state: 'error', message: response.message }
}
