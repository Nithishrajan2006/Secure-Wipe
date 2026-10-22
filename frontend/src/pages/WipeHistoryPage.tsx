import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { wipeApi } from '../api/wipe'
import {
  Search, Filter, Download, FileText, Clock, HardDrive,
  CheckCircle2, XCircle, AlertCircle, Loader2, ChevronLeft, ChevronRight
} from 'lucide-react'
import type { WipeStatus } from '../types'
import { WIPE_METHODS } from '../types'
import clsx from 'clsx'
import { format } from 'date-fns'
import { downloadFile } from '../api/download'
import toast from 'react-hot-toast'

const STATUS_CONFIG: Record<WipeStatus, { label: string; className: string; icon: React.ReactNode }> = {
  COMPLETED: { label: 'Completed', className: 'badge-green', icon: <CheckCircle2 className="w-3 h-3" /> },
  IN_PROGRESS: { label: 'In Progress', className: 'badge-blue', icon: <Loader2 className="w-3 h-3 animate-spin" /> },
  PENDING: { label: 'Pending', className: 'badge-yellow', icon: <Clock className="w-3 h-3" /> },
  FAILED: { label: 'Failed', className: 'badge-red', icon: <XCircle className="w-3 h-3" /> },
  CANCELLED: { label: 'Cancelled', className: 'badge-gray', icon: <AlertCircle className="w-3 h-3" /> },
}

export default function WipeHistoryPage() {
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<WipeStatus | ''>('')
  const [methodFilter, setMethodFilter] = useState('')
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')

  // Debounce search input so we don't fire on every keystroke
  const handleSearchChange = (val: string) => {
    setSearchInput(val)
    // Reset page and update committed search after a short delay
    setPage(0)
    setSearch(val)
  }

  const { data, isLoading } = useQuery({
    queryKey: ['wipe-history', page, statusFilter, methodFilter, search],
    queryFn: () => wipeApi.getWipeHistory({
      page,
      size: 15,
      status: statusFilter as WipeStatus || undefined,
      method: methodFilter || undefined,
    }),
    staleTime: 0,
  })

  const jobs = data?.data?.content || []
  const totalPages = data?.data?.totalPages || 0
  const totalElements = data?.data?.totalElements || 0

  // Client-side search on device name / job id / username
  // (search param not yet wired to backend query — filters from server cover status+method)
  const filtered = search
    ? jobs.filter(j =>
        j.deviceName.toLowerCase().includes(search.toLowerCase()) ||
        j.jobId.toLowerCase().includes(search.toLowerCase()) ||
        j.username.toLowerCase().includes(search.toLowerCase())
      )
    : jobs

  const formatDuration = (seconds: number) => {
    if (!seconds) return '—'
    if (seconds < 60) return `${seconds}s`
    return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
  }

  return (
    <div>
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Wipe History</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1 text-sm">
            {totalElements} total wipe operation{totalElements !== 1 ? 's' : ''}
          </p>
        </div>
      </div>

      {/* Filters */}
      <div className="card p-4 mb-6 flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            value={searchInput}
            onChange={(e) => handleSearchChange(e.target.value)}
            placeholder="Search by device, job ID, or user..."
            className="input-field pl-9"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value as WipeStatus | ''); setPage(0) }}
          className="input-field sm:w-44"
        >
          <option value="">All Status</option>
          <option value="COMPLETED">Completed</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="FAILED">Failed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <select
          value={methodFilter}
          onChange={(e) => { setMethodFilter(e.target.value); setPage(0) }}
          className="input-field sm:w-52"
        >
          <option value="">All Methods</option>
          {WIPE_METHODS.map(m => (
            <option key={m.method} value={m.method}>{m.name}</option>
          ))}
        </select>
      </div>

      {/* Table */}
      <div className="table-container">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 dark:bg-slate-700/50 border-b border-slate-200 dark:border-slate-700">
                <th className="text-left px-6 py-3.5 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Device</th>
                <th className="text-left px-4 py-3.5 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Method</th>
                <th className="text-left px-4 py-3.5 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Status</th>
                <th className="text-left px-4 py-3.5 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Size</th>
                <th className="text-left px-4 py-3.5 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Duration</th>
                <th className="text-left px-4 py-3.5 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Date</th>
                <th className="text-left px-4 py-3.5 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
              {isLoading ? (
                [...Array(5)].map((_, i) => (
                  <tr key={i}>
                    {[...Array(7)].map((_, j) => (
                      <td key={j} className="px-4 py-4">
                        <div className="h-4 bg-slate-100 dark:bg-slate-700 rounded animate-pulse" />
                      </td>
                    ))}
                  </tr>
                ))
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-16 text-center">
                    <HardDrive className="w-10 h-10 text-slate-300 dark:text-slate-600 mx-auto mb-2" />
                    <p className="text-slate-500 dark:text-slate-400">No wipe operations found</p>
                  </td>
                </tr>
              ) : (
                filtered.map(job => {
                  const statusCfg = STATUS_CONFIG[job.status]
                  return (
                    <tr key={job.jobId} className="hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors">
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <HardDrive className="w-4 h-4 text-slate-400 shrink-0" />
                          <div>
                            <p className="font-medium text-slate-900 dark:text-white truncate max-w-[200px]">{job.deviceName}</p>
                            <p className="text-xs text-slate-400 font-mono">{job.jobId.substring(0, 8)}...</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-4">
                        <span className="text-xs text-slate-600 dark:text-slate-300">{job.wipeMethodName}</span>
                      </td>
                      <td className="px-4 py-4">
                        <span className={clsx('badge flex items-center gap-1 w-fit', statusCfg.className)}>
                          {statusCfg.icon}
                          {statusCfg.label}
                        </span>
                      </td>
                      <td className="px-4 py-4 text-xs text-slate-600 dark:text-slate-300">{job.deviceSizeFormatted}</td>
                      <td className="px-4 py-4 text-xs text-slate-600 dark:text-slate-300">{formatDuration(job.durationSeconds)}</td>
                      <td className="px-4 py-4 text-xs text-slate-600 dark:text-slate-300">
                        {job.createdAt ? format(new Date(job.createdAt), 'dd MMM yyyy HH:mm') : '—'}
                      </td>
                      <td className="px-4 py-4">
                        {job.status === 'COMPLETED' && job.certificateId && (
                          <button
                            onClick={async () => {
                              try {
                                await downloadFile(
                                  `/api/reports/certificate/${job.jobId}`,
                                  `certificate-${job.jobId.substring(0, 8)}.pdf`
                                )
                              } catch {
                                toast.error('Failed to download certificate')
                              }
                            }}
                            className="inline-flex items-center gap-1 text-xs text-blue-600 dark:text-blue-400 hover:underline"
                          >
                            <FileText className="w-3.5 h-3.5" />
                            Certificate
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="px-6 py-4 border-t border-slate-200 dark:border-slate-700 flex items-center justify-between">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Page {page + 1} of {totalPages}
            </p>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
