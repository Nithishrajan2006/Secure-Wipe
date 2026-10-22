import axiosInstance from './axios'
import { downloadFile } from './download'
import type { ApiResponse, AuditLogDto, PageResponse } from '../types'

export const auditApi = {
  getAuditLogs: async (params?: {
    userId?: number
    action?: string
    from?: string
    to?: string
    page?: number
    size?: number
  }) => {
    const res = await axiosInstance.get<ApiResponse<PageResponse<AuditLogDto>>>('/audit/logs', { params })
    return res.data
  },

  /**
   * Export audit logs as CSV.
   * Uses the authenticated downloadFile helper so the Bearer token is sent —
   * the /api/reports/audit/csv endpoint requires ADMIN role.
   */
  exportCsv: async (params?: Record<string, string>) => {
    const queryString = params && Object.keys(params).length > 0
      ? '?' + new URLSearchParams(params).toString()
      : ''
    const filename = `audit-log-${new Date().toISOString().slice(0, 10)}.csv`
    await downloadFile(`/api/reports/audit/csv${queryString}`, filename)
  },

  /**
   * Export audit logs as PDF.
   * Uses the authenticated downloadFile helper so the Bearer token is sent —
   * the /api/reports/audit/pdf endpoint requires ADMIN role.
   */
  exportPdf: async (params?: Record<string, string>) => {
    const queryString = params && Object.keys(params).length > 0
      ? '?' + new URLSearchParams(params).toString()
      : ''
    const filename = `audit-log-${new Date().toISOString().slice(0, 10)}.pdf`
    await downloadFile(`/api/reports/audit/pdf${queryString}`, filename)
  },
}
