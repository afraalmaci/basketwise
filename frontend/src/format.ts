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

/** 412 -> "7 min", 3900 -> "1 h 05 min". Rounded to whole minutes, at least 1. */
export function formatDuration(seconds: number): string {
  const minutes = Math.max(1, Math.round(seconds / 60))
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  return `${hours} h ${String(minutes % 60).padStart(2, '0')} min`
}

const kilometers = new Intl.NumberFormat('nl-NL', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

/** 850 -> "850 m", 2430 -> "2,4 km" (Dutch decimal comma, like the prices). */
export function formatDistance(meters: number): string {
  if (meters < 1000) return `${Math.round(meters / 10) * 10} m`
  return `${kilometers.format(meters / 1000)} km`
}

const decimal = new Intl.NumberFormat('nl-NL', { maximumFractionDigits: 2 })

/** 750 ml -> "750 ml", 1500 ml -> "1,5 L", 2000 g -> "2 kg", 6 pc -> "6 st". */
export function formatQuantity(amount: number, unit: Unit): string {
  if (unit === 'pc') return `${amount} st`
  const [small, large] = unit === 'ml' ? ['ml', 'L'] : ['g', 'kg']
  return amount < 1000 ? `${amount} ${small}` : `${decimal.format(amount / 1000)} ${large}`
}
