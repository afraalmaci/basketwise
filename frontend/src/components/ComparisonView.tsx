import type { Chain } from '../api/catalog'
import type { ChainColumn, ComparisonRow, OptimizeResponse } from '../api/planning'
import { formatDuration, formatQuantity } from '../format'
import type { ListItem } from '../hooks/useShoppingList'
import { extraText, packsText } from '../planText'
import { Price } from './Price'

type Props = {
  data: OptimizeResponse
  chains: Chain[]
  /** The list, for the row order: the table follows the order things were added. */
  items: ListItem[]
  onSeePlan: () => void
}

/**
 * The list priced at every chain: rows are items, columns are chains, the cheapest price
 * of each row highlighted. Above it, the recommended plan in one line.
 */
export function ComparisonView({ data, chains, items, onSeePlan }: Props) {
  const chainByCode = new Map(chains.map((chain) => [chain.code, chain]))
  const rowById = new Map(data.comparison.items.map((row) => [row.productId, row]))
  const rows = items.map((item) => rowById.get(item.productId)).filter((row): row is ComparisonRow => row !== undefined)
  const columns = data.comparison.chains
  const best = data.plans[0]

  return (
    <div className="results__body">
      {best && (
        <div className="best-banner">
          <div>
            <p className="best-banner__eyebrow">Best plan</p>
            <p className="best-banner__title">
              {best.storeCount} {best.storeCount === 1 ? 'store' : 'stores'} · <Price cents={best.basketCents} /> ·{' '}
              {formatDuration(best.travelSeconds)}
            </p>
            <p className="best-banner__text">{best.stops.map((stop) => stop.storeName).join(' and ')}</p>
          </div>
          <button type="button" className="button" onClick={onSeePlan}>
            See the plan
          </button>
        </div>
      )}

      {data.unavailable.length > 0 && (
        <p className="notice notice--warning">
          Not sold in any demo store: {data.unavailable.map((item) => item.name).join(', ')}.
        </p>
      )}

      <h2 className="section-title section-title--spaced">Your list in every chain</h2>
      <p className="hint hint--tight">Cheapest packs for the amount you need. Green is the lowest price for that item.</p>

      <div className="table-scroll">
        <table className="compare" aria-label="Prices per chain">
          <thead>
            <tr>
              <th scope="col" className="compare__item">
                Item
              </th>
              {columns.map((column) => (
                <ColumnHeader key={column.chainCode} column={column} chain={chainByCode.get(column.chainCode)} />
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.productId}>
                <th scope="row" className="compare__item">
                  <span className="compare__name">{row.name}</span>
                  <span className="compare__meta">{formatQuantity(row.neededAmount, row.unit)}</span>
                </th>
                {columns.map((column) => (
                  <PriceCell key={column.chainCode} row={row} chainCode={column.chainCode} />
                ))}
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th scope="row" className="compare__item">
                All at one chain
              </th>
              {columns.map((column) => {
                const complete = column.itemsCovered === rows.length
                return (
                  <td key={column.chainCode} className="compare__cell">
                    <span className="compare__price">{column.itemsCovered > 0 ? <Price cents={column.totalCents} /> : '–'}</span>
                    <span className={complete ? 'compare__meta' : 'compare__meta compare__meta--short'}>
                      {complete ? `all ${rows.length} items` : `${column.itemsCovered} of ${rows.length} items`}
                    </span>
                  </td>
                )
              })}
            </tr>
          </tfoot>
        </table>
      </div>
      <p className="hint">
        "+500 g" means a bigger pack was the only size or the cheaper way to cover your amount. Prices are per chain,
        the same in every branch.
      </p>
    </div>
  )
}

function ColumnHeader({ column, chain }: { column: ChainColumn; chain: Chain | undefined }) {
  return (
    <th scope="col" className="compare__cell">
      <span className="compare__chain">
        <span className="dot" style={{ background: chain?.color }} aria-hidden />
        {chain?.name ?? column.chainCode}
      </span>
      {column.nearestSeconds !== null && (
        <span className="compare__meta" title={column.nearestStoreName ?? undefined}>
          nearest {formatDuration(column.nearestSeconds)}
        </span>
      )}
    </th>
  )
}

function PriceCell({ row, chainCode }: { row: ComparisonRow; chainCode: string }) {
  const price = row.prices.find((candidate) => candidate.chainCode === chainCode)
  if (!price) {
    return (
      <td className="compare__cell compare__cell--missing">
        <span className="compare__price">–</span>
        <span className="compare__meta">not sold</span>
      </td>
    )
  }
  const extra = extraText(price.boughtAmount, row.neededAmount, row.unit)
  return (
    <td className={price.cheapest ? 'compare__cell compare__cell--best' : 'compare__cell'}>
      <span className="compare__price">
        <Price cents={price.costCents} />
      </span>
      <span className="compare__meta">
        {packsText(price.packs)}
        {extra && ` · ${extra}`}
      </span>
    </td>
  )
}
