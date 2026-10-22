import axiosInstance from './axios'
import type { ApiResponse, AuthResponse, LoginRequest, RegisterRequest, UserDto } from '../types'

export const authApi = {
  login: async (data: LoginRequest) => {
    const res = await axiosInstance.post<ApiResponse<AuthResponse>>('/auth/login', data)
    return res.data
  },

  register: async (data: RegisterRequest) => {
    const res = await axiosInstance.post<ApiResponse<AuthResponse>>('/auth/register', data)
    return res.data
  },

  getCurrentUser: async () => {
    const res = await axiosInstance.get<ApiResponse<UserDto>>('/auth/me')
    return res.data
  },

  refreshToken: async (refreshToken: string) => {
    const res = await axiosInstance.post<ApiResponse<AuthResponse>>('/auth/refresh', {}, {
      headers: { 'X-Refresh-Token': refreshToken },
    })
    return res.data
  },
}
