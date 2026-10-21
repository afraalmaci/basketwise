import { useEffect, useId, useRef, useState, type ReactNode } from 'react'

type Props = {
  /** Content of the button that opens the popover. */
  label: ReactNode
  /** Accessible name of the button, when the visible label is not enough. */
  ariaLabel?: string
  /** Heading inside the popover; also names the dialog. */
  title: string
  children: (close: () => void) => ReactNode
  align?: 'start' | 'end'
}

/**
 * A chip that opens a small panel below it, like the filter chips of a maps app. Closes on
 * Escape (focus goes back to the chip) and on a click outside.
 *
 * Children are rendered only while open, so a map inside measures its real size on mount.
 */
export function Popover({ label, ariaLabel, title, children, align = 'start' }: Props) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const id = useId()

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  // Closing removes the focused element (a radio inside, say). Send focus back to the chip
  // then, unless the user already clicked somewhere else.
  const wasOpen = useRef(false)
  useEffect(() => {
    if (wasOpen.current && !open && document.activeElement === document.body) {
      buttonRef.current?.focus()
    }
    wasOpen.current = open
  }, [open])

  const close = () => setOpen(false)

  return (
    // Escape works from the chip as well as from inside the panel.
    <div
      ref={rootRef}
      className="popover-root"
      onKeyDown={(event) => {
        if (open && event.key === 'Escape') {
          setOpen(false)
          buttonRef.current?.focus()
        }
      }}
    >
      <button
        ref={buttonRef}
        type="button"
        className={open ? 'chip is-open' : 'chip'}
        aria-label={ariaLabel}
        aria-expanded={open}
        aria-controls={open ? id : undefined}
        onClick={() => setOpen((current) => !current)}
      >
        {label}
      </button>
      {open && (
        <div
          id={id}
          role="dialog"
          aria-label={title}
          className={align === 'end' ? 'popover popover--end' : 'popover'}
        >
          <h2 className="popover__title">{title}</h2>
          {children(close)}
        </div>
      )}
    </div>
  )
}
