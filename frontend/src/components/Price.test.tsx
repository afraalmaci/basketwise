import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Price } from './Price'

const normalize = (text: string | null) => (text ?? '').replace(/ /g, ' ')

describe('Price', () => {
  it('keeps the plain price as its text while styling the cents apart', () => {
    const { container } = render(<Price cents={1842} />)

    expect(normalize(container.textContent)).toBe('€ 18,42')
    expect(container.querySelector('.price__cents')).toHaveTextContent('42')
    expect(container.querySelector('.price__currency')).toHaveTextContent('€')
  })

  it('handles thousands', () => {
    const { container } = render(<Price cents={123456} />)

    expect(normalize(container.textContent)).toBe('€ 1.234,56')
    expect(container.querySelector('.price__cents')).toHaveTextContent('56')
  })
})
