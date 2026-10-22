import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { wipeApi } from '../api/wipe'
import { auditApi } from '../api/audit'
import {
  FileText, Download, Shield, Calendar, Filter,
  CheckCircle2, Clock, HardDrive, ExternalLink
} from 'lucide-react'
import { format } from 'date-fns'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { downloadFile } from '../api/download'

export default function ReportsPage() {
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')

  const { data: historyData, isLoading } = useQuery({
    queryKey: ['wipe-history-reports'],
    queryFn: () => wipeApi.getWipeHistory({ size: 50, status: 'COMPLETED' }),
  })

  const completedJobs = historyData?.data?.content || []

  const handleExportCsv = async () => {
    const params: Record<string, string> = {}
    if (fromDate) params.from = fromDate
    if (toDate) params.to = toDate
    try {
      await auditApi.exportCsv(params)
      toast.success('Audit log CSV downloaded!')
    } catch {
      toast.error('Failed to download CSV. Admin access required.')
    }
  }

  const handleExportPdf = async () => {
    const params: Record<string, string> = {}
    if (fromDate) params.from = fromDate
    if (toDate) params.to = toDate
    try {
      await auditApi.exportPdf(params)
      toast.success('Audit log PDF downloaded!')
    } catch {
      toast.error('Failed to download PDF. Admin access required.')
    }
  }

  return (
    <div>
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Reports & Compliance</h1>
        <p className="text-slate-500 dark:text-slate-400 mt-1 text-sm">
          Generate wipe certificates and export compliance documentation
        </p>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {/* Audit Log Export */}
        <div className="xl:col-span-1 space-y-4">
          <div className="card p-6">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-xl bg-purple-500/10 flex items-center justify-center">
                <FileText className="w-5 h-5 text-purple-500" />
              </div>
              <div>
                <h2 className="font-semibold text-slate-900 dark:text-white">Export Audit Logs</h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">Download complete audit trail</p>
              </div>
            </div>

            <div className="space-y-3 mb-5">
              <div>
                <label className="block text-xs font-medium text-slate-600 dark:text-slate-400 mb-1.5">
                  <Calendar className="w-3 h-3 inline mr-1" /> From Date
                </label>
                <input
                  type="datetime-local"
                  value={fromDate}
                  onChange={(e) => setFromDate(e.target.value)}
                  className="input-field text-sm"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 dark:text-slate-400 mb-1.5">
                  <Calendar className="w-3 h-3 inline mr-1" /> To Date
                </label>
                <input
                  type="datetime-local"
                  value={toDate}
                  onChange={(e) => setToDate(e.target.value)}
                  className="input-field text-sm"
                />
              </div>
            </div>

            <div className="space-y-2">
              <button
                onClick={handleExportCsv}
                className="w-full flex items-center justify-center gap-2 py-2.5 bg-green-600 hover:bg-green-700 text-white rounded-lg text-sm font-medium transition-colors"
              >
                <Download className="w-4 h-4" />
                Export as CSV
              </button>
              <button
                onClick={handleExportPdf}
                className="w-full flex items-center justify-center gap-2 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-lg text-sm font-medium transition-colors"
              >
                <FileText className="w-4 h-4" />
                Export as PDF
              </button>
            </div>
          </div>

          {/* Compliance info */}
          <div className="card p-5">
            <h3 className="font-semibold text-slate-900 dark:text-white text-sm mb-3 flex items-center gap-2">
              <Shield className="w-4 h-4 text-blue-500" />
              Compliance Standards
            </h3>
            <ul className="space-y-2 text-xs text-slate-600 dark:text-slate-400">
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-green-500 mt-0.5 shrink-0" />
                DoD 5220.22-M Standard
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-green-500 mt-0.5 shrink-0" />
                Industry IT Security Policy
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-green-500 mt-0.5 shrink-0" />
                SecureWipe Data Governance
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-green-500 mt-0.5 shrink-0" />
                Complete audit trail maintained
              </li>
            </ul>
          </div>
        </div>

        {/* Wipe Certificates */}
        <div className="xl:col-span-2">
          <div className="card">
            <div className="px-6 py-4 border-b border-slate-200 dark:border-slate-700 flex items-center justify-between">
              <div>
                <h2 className="font-semibold text-slate-900 dark:text-white">Wipe Certificates</h2>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Download PDF certificates for completed wipes</p>
              </div>
              <span className="badge-green">
                {completedJobs.length} Available
              </span>
            </div>

            {isLoading ? (
              <div className="p-6 space-y-3">
                {[1, 2, 3].map(i => (
                  <div key={i} className="h-16 bg-slate-100 dark:bg-slate-700 rounded-lg animate-pulse" />
                ))}
              </div>
            ) : completedJobs.length === 0 ? (
              <div className="p-12 text-center">
                <FileText className="w-12 h-12 text-slate-300 dark:text-slate-600 mx-auto mb-3" />
                <p className="text-slate-500 dark:text-slate-400 text-sm">No completed wipes yet</p>
                <p className="text-slate-400 dark:text-slate-500 text-xs mt-1">Complete a wipe operation to generate certificates</p>
              </div>
            ) : (
              <div className="divide-y divide-slate-100 dark:divide-slate-700 max-h-[600px] overflow-y-auto">
                {completedJobs.map(job => (
                  <div key={job.jobId} className="px-6 py-4 flex items-center justify-between hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors">
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-lg bg-blue-500/10 flex items-center justify-center">
                        <HardDrive className="w-4.5 h-4.5 text-blue-500" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-slate-900 dark:text-white">{job.deviceName}</p>
                        <div className="flex items-center gap-2 mt-0.5">
                          <span className="text-xs text-slate-500 dark:text-slate-400">{job.wipeMethodName}</span>
                          <span className="text-slate-300 dark:text-slate-600">•</span>
                          <span className="text-xs text-slate-400">
                            <Clock className="w-3 h-3 inline mr-0.5" />
                            {job.completedAt ? format(new Date(job.completedAt), 'dd MMM yyyy') : '—'}
                          </span>
                          <span className="text-slate-300 dark:text-slate-600">•</span>
                          <span className="text-xs text-slate-400">{job.deviceSizeFormatted}</span>
                        </div>
                        {job.certificateId && (
                          <p className="text-xs font-mono text-slate-400 mt-0.5">
                            ID: {job.certificateId.substring(0, 12)}...
                          </p>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0 ml-4">
                      <span className="badge-green hidden sm:flex">
                        <CheckCircle2 className="w-3 h-3" />
                        Verified
                      </span>
                      <button
                        onClick={async () => {
                          try {
                            await downloadFile(
                              `/api/reports/certificate/${job.jobId}`,
                              `certificate-${job.jobId.substring(0, 8)}.pdf`
                            )
                            toast.success('Certificate downloaded!')
                          } catch {
                            toast.error('Failed to download certificate')
                          }
                        }}
                        className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-medium transition-colors"
                      >
                        <Download className="w-3.5 h-3.5" />
                        PDF
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
