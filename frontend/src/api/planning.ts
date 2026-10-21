import type { Unit } from './catalog'
import { postJson } from './http'
import type { LatLng, Leg, TravelMode } from './routing'

export type OptimizeItem = {
  productId: number
  /** In the product's base unit: 2000 for 2 L of milk, 500 for 500 g, 6 for 6 eggs. */
  quantity: number
}

export type OptimizeRequest = {
  origin: LatLng
  mode: TravelMode
  items: OptimizeItem[]
  valueOfTimeEurPerHour: number
}

/** One pack size bought, labeled the way the chain labels it. */
export type BoughtPack = {
  displayName: string
  quantityText: string
  amount: number
  priceCents: number
  count: number
}

export type Purchase = {
  productId: number
  name: string
  unit: Unit
  neededAmount: number
  /** More than needed when a bigger pack was cheaper. */
  boughtAmount: number
  costCents: number
  packs: BoughtPack[]
}

export type PlanStop = {
  storeId: number
  chainCode: string
  storeName: string
  latitude: number
  longitude: number
  subtotalCents: number
  items: Purchase[]
}

/** One way to do the shopping: where to go, in which order, and what to buy at each stop. */
export type Plan = {
  storeCount: number
  basketCents: number
  travelSeconds: number
  travelDistanceMeters: number
  /** Travel time priced at the chosen value of time. */
  timeCostCents: number
  /** Basket + time cost; the plan with the lowest score is the recommendation. */
  scoreCents: number
  /** Stores in visiting order. */
  stops: PlanStop[]
  /** Origin → first stop, ..., last stop → origin. */
  legs: Leg[]
  /** True when travel times are straight-line estimates. */
  estimated: boolean
  /** [latitude, longitude] pairs. */
  geometry: [number, number][]
}

/** One chain's column in the comparison. */
export type ChainColumn = {
  chainCode: string
  /** Its branch with the shortest travel time; null when nothing on the list is sold anywhere. */
  nearestStoreId: number | null
  nearestStoreName: string | null
  /** One way, from the start location. */
  nearestSeconds: number | null
  /** The items it sells, bought there. */
  totalCents: number
  itemsCovered: number
}

export type ChainPrice = {
  chainCode: string
  costCents: number
  boughtAmount: number
  /** Lowest price for this item across chains (ties: all of them). */
  cheapest: boolean
  packs: BoughtPack[]
}

export type ComparisonRow = {
  productId: number
  name: string
  unit: Unit
  neededAmount: number
  /** One per chain that sells it; a chain that does not is missing. */
  prices: ChainPrice[]
}

/** Every item on the list priced at every chain, in list order. */
export type Comparison = {
  chains: ChainColumn[]
  items: ComparisonRow[]
}

export type OptimizeResponse = {
  mode: TravelMode
  valueOfTimeEurPerHour: number
  estimated: boolean
  combinationsChecked: number
  /** Items no chain sells; the plans leave them out. */
  unavailable: { productId: number; name: string }[]
  /** The best plan for each number of stores, best first, without plans beaten on both price and time. */
  plans: Plan[]
  comparison: Comparison
}

export const optimizeBasket = (request: OptimizeRequest, signal?: AbortSignal) =>
  postJson<OptimizeResponse>('/api/optimize', request, signal)
