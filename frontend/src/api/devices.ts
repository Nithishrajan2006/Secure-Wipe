import axiosInstance from './axios'
import type { ApiResponse, DeviceInfoDto } from '../types'

export const devicesApi = {
  getAllDevices: async () => {
    const res = await axiosInstance.get<ApiResponse<DeviceInfoDto[]>>('/devices')
    return res.data
  },

  refreshDevices: async () => {
    const res = await axiosInstance.get<ApiResponse<DeviceInfoDto[]>>('/devices/refresh')
    return res.data
  },
}
