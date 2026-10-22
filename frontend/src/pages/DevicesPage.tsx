import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { devicesApi } from '../api/devices'
import {
  HardDrive, RefreshCw, Trash2, Usb, Cpu, Database,
  FolderOpen, Server, Info, ChevronDown, ChevronUp
} from 'lucide-react'
import type { DeviceInfoDto, DeviceType } from '../types'
import clsx from 'clsx'
import toast from 'react-hot-toast'

const DEVICE_ICONS: Record<DeviceType, React.ReactNode> = {
  HDD: <HardDrive className="w-6 h-6" />,
  SSD: <Cpu className="w-6 h-6" />,
  USB: <Usb className="w-6 h-6" />,
  EXTERNAL_HDD: <Server className="w-6 h-6" />,
  NVME: <Database className="w-6 h-6" />,
  PARTITION: <FolderOpen className="w-6 h-6" />,
  FILE: <FolderOpen className="w-6 h-6" />,
  FOLDER: <FolderOpen className="w-6 h-6" />,
}

const DEVICE_COLORS: Record<DeviceType, string> = {
  HDD: 'bg-blue-500/10 text-blue-500 dark:text-blue-400',
  SSD: 'bg-purple-500/10 text-purple-500 dark:text-purple-400',
  USB: 'bg-cyan-500/10 text-cyan-500 dark:text-cyan-400',
  EXTERNAL_HDD: 'bg-orange-500/10 text-orange-500 dark:text-orange-400',
  NVME: 'bg-green-500/10 text-green-500 dark:text-green-400',
  PARTITION: 'bg-slate-500/10 text-slate-500 dark:text-slate-400',
  FILE: 'bg-slate-500/10 text-slate-500',
  FOLDER: 'bg-slate-500/10 text-slate-500',
}

const TYPE_BADGE: Record<DeviceType, string> = {
  HDD: 'badge-blue',
  SSD: 'badge-purple',
  USB: 'badge bg-cyan-100 dark:bg-cyan-900/30 text-cyan-700 dark:text-cyan-400',
  EXTERNAL_HDD: 'badge-yellow',
  NVME: 'badge-green',
  PARTITION: 'badge-gray',
  FILE: 'badge-gray',
  FOLDER: 'badge-gray',
}

export default function DevicesPage() {
  const navigate = useNavigate()
  const [filter, setFilter] = useState<DeviceType | 'ALL'>('ALL')
  const [expandedId, setExpandedId] = useState<string | null>(null)

  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['devices'],
    queryFn: () => devicesApi.getAllDevices(),
  })

  const devices = data?.data || []
  const filtered = filter === 'ALL' ? devices : devices.filter(d => d.type === filter)

  const handleRefresh = async () => {
    await refetch()
    toast.success('Devices refreshed')
  }

  const handleWipe = (device: DeviceInfoDto) => {
    navigate('/wipe', { state: { device } })
  }

  const typeFilters: Array<{ value: DeviceType | 'ALL'; label: string }> = [
    { value: 'ALL', label: 'All Devices' },
    { value: 'HDD', label: 'HDD' },
    { value: 'SSD', label: 'SSD' },
    { value: 'USB', label: 'USB' },
    { value: 'EXTERNAL_HDD', label: 'External' },
    { value: 'NVME', label: 'NVMe' },
  ]

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Storage Devices</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1 text-sm">
            {devices.length} device{devices.length !== 1 ? 's' : ''} detected
          </p>
        </div>
        <button
          onClick={handleRefresh}
          disabled={isFetching}
          className="btn-primary"
        >
          <RefreshCw className={clsx('w-4 h-4', isFetching && 'animate-spin')} />
          {isFetching ? 'Scanning...' : 'Refresh Devices'}
        </button>
      </div>

      {/* Type filters */}
      <div className="flex gap-2 flex-wrap mb-6">
        {typeFilters.map(f => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={clsx(
              'px-3 py-1.5 rounded-full text-sm font-medium transition-all',
              filter === f.value
                ? 'bg-blue-600 text-white shadow-sm'
                : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-700'
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* Device grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="card p-6 animate-pulse">
              <div className="flex items-center gap-4 mb-4">
                <div className="w-12 h-12 rounded-xl bg-slate-200 dark:bg-slate-700" />
                <div className="flex-1">
                  <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-3/4 mb-2" />
                  <div className="h-3 bg-slate-100 dark:bg-slate-700 rounded w-1/2" />
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="card p-16 text-center">
          <HardDrive className="w-16 h-16 text-slate-300 dark:text-slate-600 mx-auto mb-4" />
          <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-2">No devices found</h3>
          <p className="text-slate-500 dark:text-slate-400 text-sm mb-4">
            Click "Refresh Devices" to scan for connected storage devices
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filtered.map(device => (
            <DeviceCard
              key={device.id}
              device={device}
              onWipe={handleWipe}
              expanded={expandedId === device.id}
              onToggle={() => setExpandedId(expandedId === device.id ? null : device.id)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function DeviceCard({
  device, onWipe, expanded, onToggle
}: {
  device: DeviceInfoDto
  onWipe: (d: DeviceInfoDto) => void
  expanded: boolean
  onToggle: () => void
}) {
  return (
    <div className={clsx(
      'card-hover p-5 transition-all duration-200',
      expanded && 'ring-1 ring-blue-500/40'
    )}>
      <div className="flex items-start gap-4">
        <div className={clsx('w-12 h-12 rounded-xl flex items-center justify-center shrink-0', DEVICE_COLORS[device.type])}>
          {DEVICE_ICONS[device.type]}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <h3 className="font-semibold text-slate-900 dark:text-white text-sm truncate">{device.name}</h3>
            <span className={clsx(TYPE_BADGE[device.type], 'shrink-0 text-xs')}>
              {device.type}
            </span>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400 truncate mt-0.5">{device.model}</p>
        </div>
      </div>

      {/* Stats */}
      <div className="mt-4 grid grid-cols-2 gap-2">
        <InfoRow label="Size" value={device.sizeFormatted} />
        <InfoRow label="FS" value={device.fileSystem || 'N/A'} />
        <InfoRow label="Mount" value={device.mountPoint || 'N/A'} />
        <InfoRow label="S/N" value={device.serialNumber || 'N/A'} />
      </div>

      {/* Partitions expandable */}
      {device.partitions?.length > 0 && (
        <button
          onClick={onToggle}
          className="mt-3 w-full flex items-center justify-between text-xs text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300 py-1.5 border-t border-slate-100 dark:border-slate-700"
        >
          <span>{device.partitions.length} partition{device.partitions.length > 1 ? 's' : ''}</span>
          {expanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
        </button>
      )}

      {expanded && device.partitions?.length > 0 && (
        <div className="mt-2 space-y-2">
          {device.partitions.map((p, i) => (
            <div key={i} className="bg-slate-50 dark:bg-slate-700/40 rounded-lg p-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-slate-700 dark:text-slate-300">{p.name}</span>
                <span className="text-xs text-slate-500">{p.sizeFormatted}</span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">{p.mountPoint} | {p.fileSystem}</p>
            </div>
          ))}
        </div>
      )}

      {/* Action */}
      <button
        onClick={() => onWipe(device)}
        className="mt-4 w-full flex items-center justify-center gap-2 py-2 bg-red-50 dark:bg-red-900/20 hover:bg-red-100 dark:hover:bg-red-900/30 text-red-600 dark:text-red-400 rounded-lg text-sm font-medium border border-red-200 dark:border-red-800 transition-colors"
      >
        <Trash2 className="w-4 h-4" />
        Secure Wipe
      </button>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-slate-400 dark:text-slate-500">{label}</p>
      <p className="text-xs font-medium text-slate-700 dark:text-slate-300 truncate">{value}</p>
    </div>
  )
}
