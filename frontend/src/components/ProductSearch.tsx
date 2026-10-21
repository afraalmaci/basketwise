import { useState } from 'react'
import type { Chain, Product } from '../api/catalog'
import { formatPrice, formatUnitPrice } from '../format'
import { MIN_QUERY_LENGTH, useProductSearch } from '../hooks/useProductSearch'
import { CheckIcon, PlusIcon, SearchIcon } from './icons'

type Props = {
  chains: Chain[]
  /** Adds the product to the shopping list. Without it, search only compares prices. */
  onAdd?: (product: Product) => void
  isOnList?: (productId: number) => boolean
}

export function ProductSearch({ chains, onAdd, isOnList = () => false }: Props) {
  const [query, setQuery] = useState('')
  const search = useProductSearch(query)
  const chainByCode = new Map(chains.map((chain) => [chain.code, chain]))

  return (
    <div className="search">
      <label className="visually-hidden" htmlFor="product-search">
        Search products
      </label>
      <div className="search-box">
        <SearchIcon />
        <input
          id="product-search"
          className="search-box__input"
          type="search"
          placeholder="Add a product, e.g. halfvolle melk"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          autoComplete="off"
        />
      </div>

      {search.state === 'idle' && query.trim().length > 0 && (
        <p className="muted search__status">Type at least {MIN_QUERY_LENGTH} characters.</p>
      )}
      {search.state === 'loading' && <p className="muted search__status">Searching…</p>}
      {search.state === 'error' && <p className="error search__status">Search failed: {search.message}</p>}
      {search.state === 'done' && search.results.length === 0 && (
        <p className="muted search__status">No products found for "{query.trim()}".</p>
      )}
      {search.state === 'done' && search.results.length > 0 && (
        <ul className="results-list">
          {search.results.map((product) => (
            <ProductResult
              key={product.id}
              product={product}
              chainByCode={chainByCode}
              onAdd={onAdd}
              onList={isOnList(product.id)}
            />
          ))}
        </ul>
      )}
    </div>
  )
}

type ResultProps = {
  product: Product
  chainByCode: Map<string, Chain>
  onAdd?: (product: Product) => void
  onList: boolean
}

function ProductResult({ product, chainByCode, onAdd, onList }: ResultProps) {
  return (
    <li className="result">
      <div className="result__header">
        <h3 className="result__name">{product.name}</h3>
        {onAdd &&
          (onList ? (
            <span className="result__added">
              <CheckIcon size={16} /> On list
            </span>
          ) : (
            <button
              type="button"
              className="chip-button"
              aria-label={`Add ${product.name} to list`}
              onClick={() => onAdd(product)}
            >
              <PlusIcon size={16} /> Add
            </button>
          ))}
      </div>
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
