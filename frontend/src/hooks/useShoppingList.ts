import { useEffect, useState } from 'react'
import type { Product, Unit } from '../api/catalog'

export type ListItem = {
  productId: number
  name: string
  unit: Unit
  /** Needed amount in the base unit (ml, g or pieces). */
  quantity: number
  /** How much one "+" adds: one piece, or the product's smallest pack. */
  step: number
}

export type ShoppingList = {
  items: ListItem[]
  add: (product: Product) => void
  remove: (productId: number) => void
  /** Adds (or with a negative count, removes) steps; removing the last step removes the item. */
  changeQuantity: (productId: number, steps: number) => void
  clear: () => void
}

export const STORAGE_KEY = 'basketwise.shopping-list.v1'

/** Limits the backend accepts: 25 L, 25 kg, 100 pieces. */
export const MAX_AMOUNT: Record<Unit, number> = { ml: 25_000, g: 25_000, pc: 100 }
export const MAX_ITEMS = 30

/**
 * The shopping list, kept in localStorage so a page reload does not lose it.
 *
 * Storage can be unavailable (private windows, blocked site data) or hold something
 * unexpected from an older version, so reading falls back to an empty list and writing
 * failures are ignored: the list still works for the current visit.
 */
export function useShoppingList(): ShoppingList {
  const [items, setItems] = useState<ListItem[]>(load)

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
    } catch {
      // Storage full or blocked: keep the list in memory only.
    }
  }, [items])

  return {
    items,
    add: (product) =>
      setItems((current) => {
        if (current.some((item) => item.productId === product.id) || current.length >= MAX_ITEMS) return current
        const quantity = smallestPack(product)
        return [...current, { productId: product.id, name: product.name, unit: product.unit, quantity, step: stepFor(product) }]
      }),
    remove: (productId) => setItems((current) => current.filter((item) => item.productId !== productId)),
    changeQuantity: (productId, steps) =>
      setItems((current) =>
        current.flatMap((item) => {
          if (item.productId !== productId) return [item]
          const quantity = Math.min(item.quantity + steps * item.step, MAX_AMOUNT[item.unit])
          return quantity > 0 ? [{ ...item, quantity }] : []
        }),
      ),
    clear: () => setItems([]),
  }
}

/** A new item starts at the smallest pack any chain sells: 1 L of milk, 6 eggs. */
export function smallestPack(product: Product): number {
  return product.offers.length === 0 ? 1 : Math.min(...product.offers.map((offer) => offer.quantityAmount))
}

/** Pieces go one by one (10 eggs is a fair wish); liquids and weights by the smallest pack. */
export function stepFor(product: Product): number {
  return product.unit === 'pc' ? 1 : smallestPack(product)
}

function load(): ListItem[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    return Array.isArray(parsed) ? parsed.filter(isListItem) : []
  } catch {
    return []
  }
}

function isListItem(value: unknown): value is ListItem {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Record<string, unknown>
  return (
    typeof item.productId === 'number' &&
    typeof item.name === 'string' &&
    (item.unit === 'ml' || item.unit === 'g' || item.unit === 'pc') &&
    typeof item.quantity === 'number' &&
    item.quantity > 0 &&
    typeof item.step === 'number' &&
    item.step > 0
  )
}
