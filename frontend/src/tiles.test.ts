import { describe, expect, it } from 'vitest'
import { tileSource } from './tiles'

describe('tileSource', () => {
  it('uses CARTO Voyager with the key when one is configured', () => {
    const tiles = tileSource(false, 'abc123')

    expect(tiles.url).toBe('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png?key=abc123')
    expect(tiles.attribution).toContain('CARTO')
    expect(tiles.cssDarken).toBe(false)
  })

  it('uses CARTO Dark Matter in dark mode', () => {
    expect(tileSource(true, 'abc123').url).toContain('/dark_all/')
  })

  it('falls back to OpenStreetMap without a key, darkened by CSS in dark mode', () => {
    for (const key of [undefined, '', '  ']) {
      expect(tileSource(false, key).url).toBe('https://tile.openstreetmap.org/{z}/{x}/{y}.png')
    }
    expect(tileSource(false, undefined).cssDarken).toBe(false)
    expect(tileSource(true, undefined).cssDarken).toBe(true)
    expect(tileSource(true, undefined).attribution).not.toContain('CARTO')
  })
})
