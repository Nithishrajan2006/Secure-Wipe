import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { auditApi } from '../api/audit'
import { useAuthStore } from '../store/authStore'
import { Navigate } from 'react-router-dom'
import {
  ScrollText, Search, Download, ChevronLeft, ChevronRight,
  CheckCircle2, XCircle, User, Calendar
} from 'lucide-react'
import { format } from 'date-fns'
import clsx from 'clsx'
import toast from 'react-hot-toast'

export default function AuditLogPage() {
  const { user } = useAuthStore()
  const [page, setPage] = useState(0)
  const [actionFilter, setActionFilter] = useState('')
  const [search, setSearch] = useState('')

  // Admin only
  if (user?.role !== 'ADMIN') {
    return <Navigate to="/dashboard" replace />
  }

  const { data, isLoading } = useQuery({
    queryKey: ['audit-logs', page, actionFilter],
    queryFn: () => auditApi.getAuditLogs({
      page,
      size: 30,
      action: actionFilter || undefined,
    }),
  })

  const logs = data?.data?.content || []
  const totalPages = data?.data?.totalPages || 0
  const totalElements = data?.data?.totalElements || 0

  const filtered = search
    ? logs.filter(l =>
        l.username?.toLowerCase().includes(search.toLowerCase()) ||
        l.action?.toLowerCase().includes(search.toLowerCase()) ||
        l.details?.toLowerCase().includes(search.toLowerCase()) ||
        l.ipAddress?.includes(search)
      )
    : logs

  const handleExportCsv = () => {
    auditApi.exportCsv()
    toast.success('Downloading CSV...')
  }

  const handleExportPdf = () => {
    auditApi.exportPdf()
    toast.success('Generating PDF...')
  }

  const ACTION_OPTIONS = [
    '', 'USER_LOGIN', 'USER_REGISTER', 'WIPE_STARTED', 'WIPE_COMPLETED',
    'WIPE_FAILED', 'WIPE_CANCELLED', 'USER_ENABLED', 'USER_DISABLED'
  ]

  return (
    <div>
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Audit Logs</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1 text-sm">
            {totalElements.toLocaleString()} total audit entries
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={handleExportCsv} className="btn-secondary text-sm">
            <Download className="w-4 h-4" />
            CSV
          </button>
          <button onClick={handleExportPdf} className="btn-secondary text-sm">
            <Download className="w-4 h-4" />
            PDF
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="card p-4 mb-6 flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search user, action, IP..."
            className="input-field pl-9"
          />
        </div>
        <select
          value={actionFilter}
          onChange={(e) => { setActionFilter(e.target.value); setPage(0) }}
          className="input-field sm:w-52"
        >
          <option value="">All Actions</option>
          {ACTION_OPTIONS.slice(1).map(a => (
            <option key={a} value={a}>{a.replace(/_/g, ' ')}</option>
          ))}
        </select>
      </div>

      {/* Table */}
      <div className="table-container">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 dark:bg-slate-700/50 border-b border-slate-200 dark:border-slate-700">
                {['ID', 'User', 'Action', 'Resource', 'Details', 'IP Address', 'Status', 'Timestamp'].map(h => (
                  <th key={h} className="text-left px-4 py-3.5 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
              {isLoading ? (
                [...Array(8)].map((_, i) => (
                  <tr key={i}>
                    {[...Array(8)].map((_, j) => (
                      <td key={j} className="px-4 py-3.5">
                        <div className="h-3.5 bg-slate-100 dark:bg-slate-700 rounded animate-pulse" />
                      </td>
                    ))}
                  </tr>
                ))
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center">
                    <ScrollText className="w-10 h-10 text-slate-300 dark:text-slate-600 mx-auto mb-2" />
                    <p className="text-slate-500 dark:text-slate-400 text-sm">No audit logs found</p>
                  </td>
                </tr>
              ) : (
                filtered.map(log => (
                  <tr key={log.id} className="hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors">
                    <td className="px-4 py-3 text-xs text-slate-400 font-mono">{log.id}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1.5">
                        <User className="w-3.5 h-3.5 text-slate-400" />
                        <span className="text-xs text-slate-700 dark:text-slate-300">{log.username || '—'}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={clsx(
                        'text-xs font-medium px-2 py-0.5 rounded-full',
                        log.action?.includes('FAILED') || log.action?.includes('ERROR')
                          ? 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'
                          : log.action?.includes('WIPE')
                          ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400'
                          : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-400'
                      )}>
                        {log.action?.replace(/_/g, ' ')}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-500 dark:text-slate-400">{log.resourceType || '—'}</td>
                    <td className="px-4 py-3">
                      <p className="text-xs text-slate-600 dark:text-slate-300 max-w-[200px] truncate" title={log.details}>
                        {log.details || '—'}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-xs font-mono text-slate-500 dark:text-slate-400">{log.ipAddress || '—'}</td>
                    <td className="px-4 py-3">
                      <span className={clsx(
                        'flex items-center gap-1 text-xs w-fit',
                        log.status === 'SUCCESS'
                          ? 'text-green-600 dark:text-green-400'
                          : 'text-red-600 dark:text-red-400'
                      )}>
                        {log.status === 'SUCCESS'
                          ? <CheckCircle2 className="w-3.5 h-3.5" />
                          : <XCircle className="w-3.5 h-3.5" />}
                        {log.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-500 dark:text-slate-400 whitespace-nowrap">
                      {log.createdAt ? format(new Date(log.createdAt), 'dd MMM yy HH:mm') : '—'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="px-6 py-4 border-t border-slate-200 dark:border-slate-700 flex items-center justify-between">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Page {page + 1} of {totalPages} ({totalElements.toLocaleString()} entries)
            </p>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-40"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-40"
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
