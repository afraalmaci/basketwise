/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Optional CARTO basemaps key. Without it the map uses OpenStreetMap tiles. */
  readonly VITE_CARTO_API_KEY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
