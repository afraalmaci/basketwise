export type Chain = {
  code: string
  name: string
  color: string
  storeCount: number
}

export type Store = {
  id: number
  chainCode: string
  chainName: string
  name: string
  latitude: number
  longitude: number
}

export type Unit = 'ml' | 'g' | 'pc'

export type Offer = {
  chainCode: string
  displayName: string
  quantityText: string
  quantityAmount: number
  priceCents: number
  /** Per liter (ml), per kilogram (g) or per piece (pc). */
  unitPriceCents: number
}

export type Product = {
  id: number
  slug: string
  name: string
  category: string
  unit: Unit
  offers: Offer[]
}

async function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(url, { signal })
  if (!response.ok) {
    throw new Error(`${url} responded with ${response.status}`)
  }
  return response.json() as Promise<T>
}

export const fetchChains = (signal?: AbortSignal) => getJson<Chain[]>('/api/chains', signal)

export const fetchStores = (signal?: AbortSignal) => getJson<Store[]>('/api/stores', signal)

export const searchProducts = (query: string, signal?: AbortSignal) =>
  getJson<Product[]>(`/api/products/search?q=${encodeURIComponent(query)}`, signal)
