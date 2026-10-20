import type { Chain } from '../api/catalog'
import { formatPrice, formatQuantity } from '../format'
import { MAX_AMOUNT, type ListItem, type ShoppingList } from '../hooks/useShoppingList'
import { PRIORITIES, PRIORITY_ORDER, savingPerTenMinutesCents, type Priority } from '../priorities'
import { CloseIcon, MinusIcon, PlusIcon } from './icons'
import { ProductSearch } from './ProductSearch'

type Props = {
  list: ShoppingList
  chains: Chain[]
  priority: Priority
  onPriorityChange: (priority: Priority) => void
}

/** What to buy and what matters more: price or time. */
export function ShoppingListPanel({ list, chains, priority, onPriorityChange }: Props) {
  const onList = new Set(list.items.map((item) => item.productId))

  return (
    <section className="panel-section shopping" aria-labelledby="list-heading">
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

      {list.items.length === 0 ? (
        <p className="hint">Search for products and add them with how much you need. BasketWise then works out where to buy them.</p>
      ) : (
        <ul className="list" aria-label="Shopping list items">
          {list.items.map((item) => (
            <ListRow key={item.productId} item={item} list={list} />
          ))}
        </ul>
      )}

      <PriorityPicker priority={priority} onChange={onPriorityChange} />
    </section>
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

function PriorityPicker({ priority, onChange }: { priority: Priority; onChange: (priority: Priority) => void }) {
  const eurosPerHour = PRIORITIES[priority].eurosPerHour
  return (
    <div className="priority">
      <fieldset className="segmented">
        <legend className="section-subtitle">What matters more?</legend>
        {PRIORITY_ORDER.map((option) => (
          <label key={option} className={option === priority ? 'segmented__option is-active' : 'segmented__option'}>
            <input
              type="radio"
              name="priority"
              value={option}
              checked={option === priority}
              onChange={() => onChange(option)}
            />
            {PRIORITIES[option].label}
          </label>
        ))}
      </fieldset>
      <p className="hint">
        Your time counts as €{eurosPerHour}/h: 10 extra minutes must save at least{' '}
        {formatPrice(savingPerTenMinutesCents(eurosPerHour))}.
      </p>
    </div>
  )
}
