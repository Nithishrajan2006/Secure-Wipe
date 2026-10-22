import axiosInstance from './axios'
import type { ApiResponse, DashboardStatsDto } from '../types'

export const dashboardApi = {
  getStats: async () => {
    const res = await axiosInstance.get<ApiResponse<DashboardStatsDto>>('/dashboard/stats')
    return res.data
  },
}
