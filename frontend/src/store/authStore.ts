import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserDto, UserRole } from '../types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: UserDto | null
  isAuthenticated: boolean

  setAuth: (accessToken: string, refreshToken: string, user: UserDto) => void
  logout: () => void
  updateUser: (user: UserDto) => void
  isAdmin: () => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,

      setAuth: (accessToken, refreshToken, user) => {
        localStorage.setItem('accessToken', accessToken)
        localStorage.setItem('refreshToken', refreshToken)
        set({ accessToken, refreshToken, user, isAuthenticated: true })
      },

      logout: () => {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        set({ accessToken: null, refreshToken: null, user: null, isAuthenticated: false })
      },

      updateUser: (user) => set({ user }),

      isAdmin: () => get().user?.role === 'ADMIN',
    }),
    {
      name: 'securewipe-auth',
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
)
