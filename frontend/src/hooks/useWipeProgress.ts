import { useEffect, useRef, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { WipeProgressDto, WipeJobDto } from '../types'
import axiosInstance from '../api/axios'

const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED'])
const POLL_INTERVAL_MS  = 2000   // poll DB every 2s as fallback
const POLL_MAX_RETRIES  = 150    // ~5 minutes max polling

/**
 * Subscribes to real-time wipe progress via WebSocket (STOMP).
 *
 * WHY HTTP POLLING IS ALSO NEEDED
 * ─────────────────────────────────
 * The backend broadcasts the final FAILED/COMPLETED message over WebSocket
 * from the async wipe thread. If the wipe fails very quickly (e.g. the
 * Administrator check fails in <1 second), the STOMP client may not yet have
 * completed its handshake by the time the message is sent. WebSocket is
 * fire-and-forget — there is no replay. The frontend then stays stuck on
 * "Wiping in Progress…" forever because it never receives the terminal status.
 *
 * The polling fallback calls GET /wipe/progress/:jobId every 2 seconds.
 * Once a terminal status (COMPLETED / FAILED / CANCELLED) is read from the DB
 * it is merged into the progress state and polling stops. This ensures the UI
 * always transitions correctly regardless of WebSocket timing.
 */
export function useWipeProgress(jobId: string | null) {
  const [progress, setProgress] = useState<WipeProgressDto | null>(null)
  const [connected, setConnected] = useState(false)
  const clientRef      = useRef<Client | null>(null)
  const terminalRef    = useRef(false)   // set to true once a terminal status is seen
  const pollTimerRef   = useRef<ReturnType<typeof setInterval> | null>(null)
  const pollCountRef   = useRef(0)

  // ── Merge incoming progress, stop polling when terminal ─────────────────────
  const applyProgress = useCallback((incoming: WipeProgressDto) => {
    setProgress(incoming)
    if (incoming.status && TERMINAL_STATUSES.has(incoming.status)) {
      terminalRef.current = true
      stopPolling()
    }
  }, [])

  // ── HTTP polling fallback ────────────────────────────────────────────────────
  const stopPolling = useCallback(() => {
    if (pollTimerRef.current != null) {
      clearInterval(pollTimerRef.current)
      pollTimerRef.current = null
    }
  }, [])

  const startPolling = useCallback((id: string) => {
    stopPolling()
    pollCountRef.current = 0

    pollTimerRef.current = setInterval(async () => {
      if (terminalRef.current) { stopPolling(); return }
      if (pollCountRef.current >= POLL_MAX_RETRIES) { stopPolling(); return }
      pollCountRef.current++

      try {
        const res = await axiosInstance.get<{ data: WipeJobDto }>(`/wipe/progress/${id}`)
        const job = res.data?.data
        if (!job) return

        // Convert WipeJobDto fields to WipeProgressDto shape
        const dto: WipeProgressDto = {
          jobId:                         job.jobId,
          status:                        job.status,
          progressPercent:               job.progressPercent,
          bytesWiped:                    job.bytesWiped,
          totalBytes:                    job.deviceSize,
          wipeSpeedMbps:                 job.wipeSpeedMbps,
          estimatedTimeRemainingSeconds: job.estimatedTimeRemainingSeconds,
          currentPass:                   job.currentPass,
          totalPasses:                   job.totalPasses,
          statusMessage:                 job.status === 'FAILED'
                                           ? ('❌ ' + (job.errorMessage || 'Wipe failed'))
                                           : job.status === 'COMPLETED'
                                           ? '✅ Wipe completed successfully!'
                                           : '',
          errorMessage:                  job.errorMessage,
        }
        applyProgress(dto)
      } catch {
        // Network error during poll — keep retrying silently
      }
    }, POLL_INTERVAL_MS)
  }, [applyProgress, stopPolling])

  // ── WebSocket subscription ───────────────────────────────────────────────────
  useEffect(() => {
    if (!jobId) return

    // Reset state for the new job
    terminalRef.current = false
    setProgress(null)
    setConnected(false)

    // Start HTTP polling immediately — it will self-cancel once a terminal
    // status arrives either from WebSocket or from the poll itself.
    startPolling(jobId)

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/wipe-progress/${jobId}`, (message) => {
          try {
            const data: WipeProgressDto = JSON.parse(message.body)
            applyProgress(data)
          } catch (e) {
            console.error('Failed to parse progress message:', e)
          }
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => {
        console.error('STOMP error:', frame)
        setConnected(false)
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      stopPolling()
      if (clientRef.current?.active) {
        clientRef.current.deactivate()
      }
    }
  }, [jobId])

  const disconnect = useCallback(() => {
    stopPolling()
    if (clientRef.current?.active) {
      clientRef.current.deactivate()
    }
  }, [stopPolling])

  return { progress, connected, disconnect }
}
