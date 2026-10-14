import { useState } from 'react'
import type { Chain, Product } from '../api/catalog'
import { formatPrice, formatUnitPrice } from '../format'
import { MIN_QUERY_LENGTH, useProductSearch } from '../hooks/useProductSearch'

type Props = { chains: Chain[] }

export function ProductSearch({ chains }: Props) {
  const [query, setQuery] = useState('')
  const search = useProductSearch(query)
  const chainByCode = new Map(chains.map((chain) => [chain.code, chain]))

  return (
    <div className="search">
      <label className="search__label" htmlFor="product-search">
        Search products
      </label>
      <input
        id="product-search"
        className="search__input"
        type="search"
        placeholder="e.g. halfvolle melk"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        autoComplete="off"
      />

      {search.state === 'idle' && query.trim().length > 0 && (
        <p className="muted">Type at least {MIN_QUERY_LENGTH} characters.</p>
      )}
      {search.state === 'loading' && <p className="muted">Searching…</p>}
      {search.state === 'error' && <p className="error">Search failed: {search.message}</p>}
      {search.state === 'done' && search.results.length === 0 && (
        <p className="muted">No products found for "{query.trim()}".</p>
      )}
      {search.state === 'done' && search.results.length > 0 && (
        <ul className="results">
          {search.results.map((product) => (
            <ProductResult key={product.id} product={product} chainByCode={chainByCode} />
          ))}
        </ul>
      )}
    </div>
  )
}

type ResultProps = { product: Product; chainByCode: Map<string, Chain> }

function ProductResult({ product, chainByCode }: ResultProps) {
  return (
    <li className="result">
      <h3 className="result__name">{product.name}</h3>
      <table className="offers">
        <tbody>
          {product.offers.map((offer, index) => {
            const chain = chainByCode.get(offer.chainCode)
            return (
              <tr key={`${offer.chainCode}-${offer.quantityAmount}`} className={index === 0 ? 'offers__best' : undefined}>
                <td>
                  <span className="dot" style={{ background: chain?.color }} aria-hidden />
                  {chain?.name ?? offer.chainCode}
                </td>
                <td className="offers__qty" title={offer.displayName}>
                  {offer.quantityText}
                </td>
                <td className="offers__price">{formatPrice(offer.priceCents)}</td>
                <td className="offers__unit">{formatUnitPrice(offer.unitPriceCents, product.unit)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </li>
  )
}
