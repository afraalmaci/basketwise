import type { Chain } from '../api/catalog'
import { formatQuantity } from '../format'
import { MAX_AMOUNT, type ListItem, type ShoppingList } from '../hooks/useShoppingList'
import { CloseIcon, MinusIcon, PlusIcon } from './icons'
import { ProductSearch } from './ProductSearch'

type Props = {
  list: ShoppingList
  chains: Chain[]
}

/** The left column: what to buy and how much. */
export function ShoppingListPanel({ list, chains }: Props) {
  const onList = new Set(list.items.map((item) => item.productId))

  return (
    <aside className="card sidebar" aria-labelledby="list-heading">
      <div className="section-heading">
        <h2 id="list-heading" className="section-title">
          Shopping list
          {list.items.length > 0 && <span className="count">{list.items.length}</span>}
        </h2>
        {list.items.length > 0 && (
          <button type="button" className="text-button text-button--inline" onClick={list.clear}>
            Clear
          </button>
        )}
      </div>

      <ProductSearch chains={chains} onAdd={list.add} isOnList={(id) => onList.has(id)} />

      {list.items.length > 0 && (
        <ul className="list" aria-label="Shopping list items">
          {list.items.map((item) => (
            <ListRow key={item.productId} item={item} list={list} />
          ))}
        </ul>
      )}
      <p className="hint">Amounts are what you need. BasketWise picks the packs.</p>
    </aside>
  )
}

function ListRow({ item, list }: { item: ListItem; list: ShoppingList }) {
  const atMax = item.quantity + item.step > MAX_AMOUNT[item.unit]
  return (
    <li className="list-item">
      <span className="list-item__name">{item.name}</span>
      <span className="stepper">
        <button
          type="button"
          className="icon-button"
          aria-label={`Less ${item.name}`}
          onClick={() => list.changeQuantity(item.productId, -1)}
        >
          <MinusIcon size={16} />
        </button>
        <span className="stepper__value" aria-label={`${item.name} quantity`}>
          {formatQuantity(item.quantity, item.unit)}
        </span>
        <button
          type="button"
          className="icon-button"
          aria-label={`More ${item.name}`}
          disabled={atMax}
          onClick={() => list.changeQuantity(item.productId, 1)}
        >
          <PlusIcon size={16} />
        </button>
      </span>
      <button
        type="button"
        className="icon-button icon-button--quiet"
        aria-label={`Remove ${item.name}`}
        onClick={() => list.remove(item.productId)}
      >
        <CloseIcon size={16} />
      </button>
    </li>
  )
}
