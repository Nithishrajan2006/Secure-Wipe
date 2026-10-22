/**
 * Authenticated file download helper.
 *
 * Uses fetch() with the stored Bearer token so the Authorization header
 * is always sent — whether the request goes through the Vite dev proxy
 * or directly to the backend in production. Falls back to window.open
 * for public endpoints.
 *
 * @param url       Full URL to request (e.g. /api/reports/certificate/xxx)
 * @param filename  Suggested filename for the downloaded file
 * @param auth      Whether to attach the Bearer token (default: true)
 */
export async function downloadFile(
  url: string,
  filename: string,
  auth = true,
): Promise<void> {
  const headers: Record<string, string> = {}

  if (auth) {
    const token = localStorage.getItem('accessToken')
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
  }

  const response = await fetch(url, { headers })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(`Download failed (${response.status}): ${text || response.statusText}`)
  }

  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)

  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = filename
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)

  // Release the object URL after a short delay
  setTimeout(() => URL.revokeObjectURL(objectUrl), 10_000)
}
