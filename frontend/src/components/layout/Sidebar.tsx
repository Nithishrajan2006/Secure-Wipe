import { NavLink, useLocation } from 'react-router-dom'
import {
  LayoutDashboard, HardDrive, Trash2, Clock, FileText,
  ScrollText, Users, HelpCircle, Shield, X, ChevronRight
} from 'lucide-react'
import { useAuthStore } from '../../store/authStore'
import clsx from 'clsx'

interface SidebarProps {
  open: boolean
  onClose: () => void
}

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/devices', label: 'Devices', icon: HardDrive },
  { to: '/wipe', label: 'Secure Wipe', icon: Trash2 },
  { to: '/history', label: 'Wipe History', icon: Clock },
  { to: '/reports', label: 'Reports', icon: FileText },
]

const adminNavItems = [
  { to: '/audit', label: 'Audit Logs', icon: ScrollText },
  { to: '/users', label: 'User Management', icon: Users },
]

const bottomItems = [
  { to: '/help', label: 'Help & Docs', icon: HelpCircle },
]

export default function Sidebar({ open, onClose }: SidebarProps) {
  const { user } = useAuthStore()
  const isAdmin = user?.role === 'ADMIN'

  return (
    <>
      {/* Desktop sidebar */}
      <aside className={clsx(
        'hidden lg:flex flex-col w-64 bg-slate-900 dark:bg-slate-950 border-r border-slate-700/50 shrink-0',
      )}>
        <SidebarContent user={user} isAdmin={isAdmin} />
      </aside>

      {/* Mobile sidebar */}
      <aside className={clsx(
        'fixed inset-y-0 left-0 z-30 w-64 flex flex-col bg-slate-900 dark:bg-slate-950 border-r border-slate-700/50 transform transition-transform duration-300 lg:hidden',
        open ? 'translate-x-0' : '-translate-x-full'
      )}>
        <div className="flex items-center justify-between p-4 border-b border-slate-700">
          <span className="text-white font-semibold">Menu</span>
          <button onClick={onClose} className="text-slate-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>
        <SidebarContent user={user} isAdmin={isAdmin} />
      </aside>
    </>
  )
}

function SidebarContent({ user, isAdmin }: { user: any; isAdmin: boolean }) {
  return (
    <div className="flex flex-col h-full">
      {/* Logo */}
      <div className="px-6 py-5 border-b border-slate-700/50">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center shadow-lg">
            <Shield className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-white font-bold text-base leading-none">SecureWipe</h1>
            <p className="text-slate-400 text-xs mt-0.5">Data Destruction Platform</p>
          </div>
        </div>
        <div className="mt-3 px-2 py-1.5 bg-blue-500/10 rounded-lg border border-blue-500/20">
          <p className="text-blue-400 text-xs font-medium">Enterprise Edition</p>
          <p className="text-slate-500 text-xs">Secure Data Destruction</p>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
        {navItems.map((item) => (
          <NavItem key={item.to} {...item} />
        ))}

        {isAdmin && (
          <>
            <div className="pt-3 pb-1 px-3">
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Admin</p>
            </div>
            {adminNavItems.map((item) => (
              <NavItem key={item.to} {...item} />
            ))}
          </>
        )}
      </nav>

      {/* Bottom */}
      <div className="px-3 py-3 border-t border-slate-700/50">
        {bottomItems.map((item) => (
          <NavItem key={item.to} {...item} />
        ))}
      </div>

      {/* User info */}
      <div className="px-4 py-4 border-t border-slate-700/50">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white text-sm font-semibold">
            {user?.fullName?.[0]?.toUpperCase() || user?.username?.[0]?.toUpperCase() || 'U'}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-white text-sm font-medium truncate">{user?.fullName || user?.username}</p>
            <p className="text-slate-400 text-xs truncate">
              {user?.role === 'ADMIN' ? '🔑 Administrator' : '👤 User'}
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}

function NavItem({ to, label, icon: Icon }: { to: string; label: string; icon: any }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) => clsx(
        'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 group',
        isActive
          ? 'bg-blue-600/20 text-blue-400 border border-blue-500/30'
          : 'text-slate-400 hover:text-white hover:bg-slate-700/50'
      )}
    >
      <Icon className="w-4.5 h-4.5 shrink-0" />
      <span className="flex-1">{label}</span>
    </NavLink>
  )
}
