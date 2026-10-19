/** Error from the API. `message` prefers the server's explanation (ProblemDetail "detail"). */
export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function handle<T>(url: string, response: Response): Promise<T> {
  if (!response.ok) {
    let detail: string | undefined
    try {
      const body = (await response.json()) as { detail?: unknown }
      if (typeof body?.detail === 'string') detail = body.detail
    } catch {
      // No JSON body; fall back to the status code.
    }
    throw new ApiError(detail ?? `${url} responded with ${response.status}`, response.status)
  }
  return response.json() as Promise<T>
}

export async function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  return handle<T>(url, await fetch(url, { signal }))
}

export async function postJson<T>(url: string, body: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  })
  return handle<T>(url, response)
}
