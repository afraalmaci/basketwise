import { formatPrice } from '../format'

/**
 * A price set like a supermarket shelf tag: a small euro sign, the euros at full size and
 * the cents smaller and raised. The text content stays the plain formatted price, so screen
 * readers, copy-paste and tests still get "€ 18,42".
 */
export function Price({ cents }: { cents: number }) {
  const text = formatPrice(cents)
  const match = /^(€\s?)(-?[\d.]+)(,)(\d+)$/.exec(text)
  if (!match) return <span className="price">{text}</span>
  const [, currency, euros, comma, rest] = match
  return (
    <span className="price">
      <span className="price__currency">{currency}</span>
      {euros}
      <span className="price__comma">{comma}</span>
      <span className="price__cents">{rest}</span>
    </span>
  )
}
