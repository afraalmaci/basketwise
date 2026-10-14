import { useEffect, useState } from 'react'
import { fetchChains, fetchStores, type Chain, type Store } from '../api/catalog'

export type Catalog = {
  chains: Chain[]
  stores: Store[]
  error: string | null
}

/** Chains and stores are small and static, so they are loaded once on startup. */
export function useCatalog(): Catalog {
  const [catalog, setCatalog] = useState<Catalog>({ chains: [], stores: [], error: null })

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([fetchChains(controller.signal), fetchStores(controller.signal)])
      .then(([chains, stores]) => setCatalog({ chains, stores, error: null }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        const message = error instanceof Error ? error.message : 'Unknown error'
        setCatalog((current) => ({ ...current, error: message }))
      })
    return () => controller.abort()
  }, [])

  return catalog
}
