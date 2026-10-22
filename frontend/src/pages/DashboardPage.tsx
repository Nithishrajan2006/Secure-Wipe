import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '../api/dashboard'
import { useAuthStore } from '../store/authStore'
import {
  Trash2, Database, Activity, TrendingUp, Clock, CheckCircle2,
  XCircle, HardDrive, Zap, RefreshCw
} from 'lucide-react'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, BarChart, Bar
} from 'recharts'
import clsx from 'clsx'
import { formatDistanceToNow } from 'date-fns'

const STATUS_COLORS: Record<string, string> = {
  COMPLETED: 'badge-green',
  IN_PROGRESS: 'badge-blue',
  PENDING: 'badge-yellow',
  FAILED: 'badge-red',
  CANCELLED: 'badge-gray',
}

export default function DashboardPage() {
  const { user } = useAuthStore()
  const { data, isLoading, isFetching, refetch } = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: () => dashboardApi.getStats(),
    refetchInterval: 30000,
    staleTime: 0,
  })

  const stats = data?.data

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
            Welcome back, {user?.username} 👋
          </h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1 text-sm">
            Here's your SecureWipe overview for today
          </p>
        </div>
        <button
          onClick={() => refetch()}
          className="btn-secondary text-sm"
          disabled={isFetching}
        >
          <RefreshCw className={clsx('w-4 h-4', isFetching && 'animate-spin')} />
          {isFetching ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-8">
        <StatCard
          title="Total Wipes"
          value={stats?.totalWipes ?? 0}
          icon={<Trash2 className="w-5 h-5" />}
          color="blue"
          subtitle="All time operations"
          loading={isLoading}
        />
        <StatCard
          title="Data Destroyed"
          value={stats?.totalDataWipedFormatted ?? '0 B'}
          icon={<Database className="w-5 h-5" />}
          color="purple"
          subtitle="Total data wiped"
          loading={isLoading}
        />
        <StatCard
          title="Active Operations"
          value={stats?.activeOperations ?? 0}
          icon={<Activity className="w-5 h-5" />}
          color="orange"
          subtitle="Currently running"
          loading={isLoading}
          pulse={!!(stats?.activeOperations && stats.activeOperations > 0)}
        />
        <StatCard
          title="Success Rate"
          value={`${stats?.successRate?.toFixed(1) ?? 0}%`}
          icon={<TrendingUp className="w-5 h-5" />}
          color="green"
          subtitle="Completed successfully"
          loading={isLoading}
        />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6 mb-6">
        {/* Wipe trend chart */}
        <div className="card p-6 xl:col-span-2">
          <div className="flex items-center justify-between mb-5">
            <div>
              <h2 className="text-base font-semibold text-slate-900 dark:text-white">Wipe Operations (Last 7 Days)</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Daily wipe operation count</p>
            </div>
          </div>
          {isLoading ? (
            <div className="h-52 bg-slate-100 dark:bg-slate-700 rounded-lg animate-pulse" />
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={stats?.dailyStats || []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" strokeOpacity={0.5} />
                <XAxis
                  dataKey="date"
                  tick={{ fill: '#94a3b8', fontSize: 11 }}
                  tickFormatter={(val) => val.slice(5)}
                />
                <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#1e293b',
                    border: '1px solid #334155',
                    borderRadius: '8px',
                    color: '#f1f5f9',
                  }}
                />
                <Bar dataKey="count" fill="#3b82f6" radius={[4, 4, 0, 0]} name="Wipes" />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Wipe by method */}
        <div className="card p-6">
          <h2 className="text-base font-semibold text-slate-900 dark:text-white mb-5">By Wipe Method</h2>
          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4].map(i => (
                <div key={i} className="h-8 bg-slate-100 dark:bg-slate-700 rounded animate-pulse" />
              ))}
            </div>
          ) : (
            <div className="space-y-3">
              {Object.entries(stats?.wipesByMethod || {}).map(([method, count]) => {
                const total = stats?.totalWipes || 1
                const pct = Math.round((count / total) * 100)
                return (
                  <div key={method}>
                    <div className="flex items-center justify-between text-sm mb-1">
                      <span className="text-slate-600 dark:text-slate-300 truncate max-w-[70%]">{method}</span>
                      <span className="text-slate-900 dark:text-white font-medium ml-2">{count}</span>
                    </div>
                    <div className="h-1.5 bg-slate-200 dark:bg-slate-700 rounded-full">
                      <div
                        className="h-full bg-gradient-to-r from-blue-500 to-indigo-500 rounded-full"
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                )
              })}
              {Object.keys(stats?.wipesByMethod || {}).length === 0 && (
                <p className="text-slate-500 dark:text-slate-400 text-sm text-center py-4">No data yet</p>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Recent wipes */}
      <div className="card">
        <div className="px-6 py-4 border-b border-slate-200 dark:border-slate-700 flex items-center justify-between">
          <h2 className="text-base font-semibold text-slate-900 dark:text-white">Recent Wipe Operations</h2>
          <a href="/history" className="text-sm text-blue-600 dark:text-blue-400 hover:underline">View all</a>
        </div>
        <div className="divide-y divide-slate-100 dark:divide-slate-700">
          {isLoading ? (
            <div className="p-6 space-y-4">
              {[1, 2, 3].map(i => (
                <div key={i} className="h-12 bg-slate-100 dark:bg-slate-700 rounded animate-pulse" />
              ))}
            </div>
          ) : stats?.recentWipes?.length === 0 ? (
            <div className="p-8 text-center">
              <Trash2 className="w-10 h-10 text-slate-300 dark:text-slate-600 mx-auto mb-2" />
              <p className="text-slate-500 dark:text-slate-400 text-sm">No wipe operations yet</p>
            </div>
          ) : (
            stats?.recentWipes?.slice(0, 8).map((job) => (
              <div key={job.jobId} className="px-6 py-4 flex items-center justify-between hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-700 flex items-center justify-center shrink-0">
                    <HardDrive className="w-4 h-4 text-slate-500 dark:text-slate-400" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-slate-900 dark:text-white truncate">{job.deviceName}</p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">{job.wipeMethodName}</p>
                  </div>
                </div>
                <div className="flex items-center gap-3 shrink-0 ml-4">
                  <span className={STATUS_COLORS[job.status] || 'badge-gray'}>
                    {job.status}
                  </span>
                  <span className="text-xs text-slate-400 hidden sm:block">
                    {formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}
                  </span>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}

function StatCard({
  title, value, icon, color, subtitle, loading, pulse
}: {
  title: string
  value: string | number
  icon: React.ReactNode
  color: 'blue' | 'purple' | 'orange' | 'green'
  subtitle: string
  loading?: boolean
  pulse?: boolean
}) {
  const colors = {
    blue: 'bg-blue-500/10 text-blue-600 dark:text-blue-400',
    purple: 'bg-purple-500/10 text-purple-600 dark:text-purple-400',
    orange: 'bg-orange-500/10 text-orange-600 dark:text-orange-400',
    green: 'bg-green-500/10 text-green-600 dark:text-green-400',
  }

  return (
    <div className="card p-6">
      <div className="flex items-center justify-between mb-4">
        <div className={clsx('w-10 h-10 rounded-xl flex items-center justify-center', colors[color])}>
          {icon}
        </div>
        {pulse && (
          <span className="flex h-2.5 w-2.5">
            <span className="animate-ping absolute inline-flex h-2.5 w-2.5 rounded-full bg-orange-400 opacity-75" />
            <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-orange-500" />
          </span>
        )}
      </div>
      {loading ? (
        <>
          <div className="h-8 w-24 bg-slate-200 dark:bg-slate-700 rounded animate-pulse mb-2" />
          <div className="h-3 w-32 bg-slate-100 dark:bg-slate-700 rounded animate-pulse" />
        </>
      ) : (
        <>
          <p className="text-2xl font-bold text-slate-900 dark:text-white">{value}</p>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">{subtitle}</p>
        </>
      )}
    </div>
  )
}
