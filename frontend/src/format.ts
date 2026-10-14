import type { Unit } from './api/catalog'

const euro = new Intl.NumberFormat('nl-NL', { style: 'currency', currency: 'EUR' })

export function formatPrice(cents: number): string {
  return euro.format(cents / 100)
}

const UNIT_LABEL: Record<Unit, string> = { ml: 'L', g: 'kg', pc: 'st' }

/** 115 cents per liter -> "€ 1,15 / L" */
export function formatUnitPrice(cents: number, unit: Unit): string {
  return `${formatPrice(cents)} / ${UNIT_LABEL[unit]}`
}
