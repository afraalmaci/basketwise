/**
 * Which map tiles to load.
 *
 * CARTO basemaps (Voyager, Dark Matter) look closest to a navigation app, but since
 * August 2026 every request needs an API key. It is free for non-commercial use: set
 * VITE_CARTO_API_KEY (see README). Without a key, the app falls back to the standard
 * OpenStreetMap tiles, which need no key, so the map never shows a watermark.
 *
 * The key ends up in the browser bundle; that is how tile keys work. Restrict it to your
 * domains in the CARTO dashboard.
 */
export type TileSource = {
  url: string
  attribution: string
  subdomains: string
  /** OSM has no dark style; in dark mode its tiles are darkened with a CSS filter instead. */
  cssDarken: boolean
}

const OSM_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'

export function tileSource(dark: boolean, cartoKey: string | undefined = import.meta.env.VITE_CARTO_API_KEY): TileSource {
  const key = cartoKey?.trim()
  if (key) {
    const style = dark ? 'dark_all' : 'rastertiles/voyager'
    return {
      // {r} becomes "@2x" on high-DPI screens.
      url: `https://{s}.basemaps.cartocdn.com/${style}/{z}/{x}/{y}{r}.png?key=${encodeURIComponent(key)}`,
      attribution: `${OSM_ATTRIBUTION} &copy; <a href="https://carto.com/attributions">CARTO</a>`,
      subdomains: 'abcd',
      cssDarken: false,
    }
  }
  return {
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: OSM_ATTRIBUTION,
    subdomains: 'abc',
    cssDarken: dark,
  }
}
