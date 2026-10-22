import axiosInstance from './axios'
import type { ApiResponse, PageResponse, StartWipeRequest, WipeJobDto, WipeStatus } from '../types'

export const wipeApi = {
  startWipe: async (data: StartWipeRequest) => {
    const res = await axiosInstance.post<ApiResponse<WipeJobDto>>('/wipe/start', data)
    return res.data
  },

  getWipeProgress: async (jobId: string) => {
    const res = await axiosInstance.get<ApiResponse<WipeJobDto>>(`/wipe/progress/${jobId}`)
    return res.data
  },

  cancelWipe: async (jobId: string) => {
    const res = await axiosInstance.post<ApiResponse<WipeJobDto>>(`/wipe/cancel/${jobId}`)
    return res.data
  },

  getWipeHistory: async (params?: {
    status?: WipeStatus
    method?: string
    from?: string
    to?: string
    page?: number
    size?: number
  }) => {
    const res = await axiosInstance.get<ApiResponse<PageResponse<WipeJobDto>>>('/wipe/history', {
      params,
    })
    return res.data
  },

  getWipeJob: async (jobId: string) => {
    const res = await axiosInstance.get<ApiResponse<WipeJobDto>>(`/wipe/${jobId}`)
    return res.data
  },
}
