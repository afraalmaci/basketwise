import { useEffect, useState } from 'react'
import { searchProducts, type Product } from '../api/catalog'

export const MIN_QUERY_LENGTH = 2
const DEBOUNCE_MS = 250

export type SearchState =
  | { state: 'idle' }
  | { state: 'loading' }
  | { state: 'done'; results: Product[] }
  | { state: 'error'; message: string }

type Response =
  | { query: string; ok: true; results: Product[] }
  | { query: string; ok: false; message: string }

/**
 * Debounced search: waits until the user pauses typing, and aborts the previous
 * request when the query changes, so a slow old response can never overwrite a newer one.
 *
 * Only responses are stored in state. "idle" and "loading" are derived during render
 * by comparing the current query with the query the last response belongs to.
 */
export function useProductSearch(query: string): SearchState {
  const [response, setResponse] = useState<Response | null>(null)
  const trimmed = query.trim()
  const searchable = trimmed.length >= MIN_QUERY_LENGTH

  useEffect(() => {
    if (!searchable) return
    const controller = new AbortController()
    const timer = setTimeout(() => {
      searchProducts(trimmed, controller.signal)
        .then((results) => setResponse({ query: trimmed, ok: true, results }))
        .catch((error: unknown) => {
          if (controller.signal.aborted) return
          const message = error instanceof Error ? error.message : 'Unknown error'
          setResponse({ query: trimmed, ok: false, message })
        })
    }, DEBOUNCE_MS)

    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [trimmed, searchable])

  if (!searchable) return { state: 'idle' }
  if (response?.query !== trimmed) return { state: 'loading' }
  return response.ok
    ? { state: 'done', results: response.results }
    : { state: 'error', message: response.message }
}
