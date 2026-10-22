import { useState, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { devicesApi } from '../api/devices'
import { wipeApi } from '../api/wipe'
import { useWipeProgress } from '../hooks/useWipeProgress'
import {
  Shield, AlertTriangle, HardDrive, ChevronRight, Trash2,
  X, CheckCircle2, XCircle, Zap, Info, StopCircle
} from 'lucide-react'
import type { DeviceInfoDto, WipeJobDto, WipeMethod } from '../types'
import { WIPE_METHODS } from '../types'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { downloadFile } from '../api/download'

type WipeStep = 'select' | 'confirm1' | 'confirm2' | 'confirm3' | 'wiping' | 'done'

export default function WipePage() {
  const location = useLocation()
  const preselectedDevice = location.state?.device as DeviceInfoDto | undefined

  const [selectedDevice, setSelectedDevice] = useState<DeviceInfoDto | null>(preselectedDevice || null)
  const [selectedMethod, setSelectedMethod] = useState<WipeMethod | null>(null)
  const [step, setStep] = useState<WipeStep>('select')
  const [confirmName, setConfirmName] = useState('')
  const [confirmCheckbox, setConfirmCheckbox] = useState(false)
  const [activeJob, setActiveJob] = useState<WipeJobDto | null>(null)
  const [cancelling, setCancelling] = useState(false)

  const { progress } = useWipeProgress(activeJob?.jobId || null)

  const { data: devicesData, isLoading: devicesLoading } = useQuery({
    queryKey: ['devices'],
    queryFn: () => devicesApi.getAllDevices(),
  })

  const devices = devicesData?.data || []

  // Sync progress to job state
  useEffect(() => {
    if (progress && activeJob) {
      if (progress.status === 'COMPLETED') {
        setStep('done')
        toast.success('Wipe completed successfully!')
      } else if (progress.status === 'FAILED') {
        setStep('done')
        toast.error('Wipe failed: ' + (progress.errorMessage || 'Unknown error'))
      } else if (progress.status === 'CANCELLED') {
        setStep('done')
        toast('Wipe cancelled', { icon: '⚠️' })
      }
    }
  }, [progress])

  const handleStartWipe = async () => {
    if (!selectedDevice || !selectedMethod) return
    try {
      const res = await wipeApi.startWipe({
        devicePath: selectedDevice.path,
        deviceName: selectedDevice.name,
        deviceType: selectedDevice.type,
        wipeMethod: selectedMethod,
        deviceSize: selectedDevice.size,
        fileSystem: selectedDevice.fileSystem,
        mountPoint: selectedDevice.mountPoint,
        confirmationText: confirmName,
      })
      if (res.success && res.data) {
        setActiveJob(res.data)
        setStep('wiping')
        toast.success('Wipe operation started!')
      }
    } catch {
      // handled by interceptor
    }
  }

  const handleCancel = async () => {
    if (!activeJob) return
    setCancelling(true)
    try {
      await wipeApi.cancelWipe(activeJob.jobId)
      toast('Cancellation requested...', { icon: '⏳' })
    } catch {
      // handled
    } finally {
      setCancelling(false)
    }
  }

  const handleReset = () => {
    setSelectedDevice(preselectedDevice || null)
    setSelectedMethod(null)
    setStep('select')
    setConfirmName('')
    setConfirmCheckbox(false)
    setActiveJob(null)
  }

  const currentProgress = progress || (activeJob ? {
    progressPercent: activeJob.progressPercent,
    wipeSpeedMbps: activeJob.wipeSpeedMbps,
    estimatedTimeRemainingSeconds: activeJob.estimatedTimeRemainingSeconds,
    currentPass: activeJob.currentPass,
    totalPasses: activeJob.totalPasses,
    statusMessage: 'Initializing...',
    bytesWiped: activeJob.bytesWiped,
    totalBytes: activeJob.deviceSize,
  } : null)

  if (step === 'wiping' || step === 'done') {
    return (
      <WipeProgressView
        job={activeJob}
        progress={currentProgress}
        isDone={step === 'done'}
        onCancel={handleCancel}
        onReset={handleReset}
        cancelling={cancelling}
      />
    )
  }

  return (
    <div className="max-w-5xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Secure Wipe</h1>
        <p className="text-slate-500 dark:text-slate-400 mt-1 text-sm">
          Permanently and securely destroy data using industry-standard methods
        </p>
      </div>

      {/* Safety warning */}
      <div className="mb-6 p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl flex gap-3">
        <AlertTriangle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
        <div>
          <p className="text-sm font-semibold text-red-700 dark:text-red-400">⚠️ Irreversible Operation</p>
          <p className="text-sm text-red-600 dark:text-red-400 mt-0.5">
            Data wiped using this tool <strong>cannot be recovered</strong>. Ensure you have selected the correct device before proceeding.
          </p>
        </div>
      </div>

      {step === 'select' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Device Selection */}
          <div>
            <h2 className="text-sm font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-3">
              1. Select Device
            </h2>
            <div className="space-y-2 max-h-96 overflow-y-auto pr-1">
              {devicesLoading ? (
                <div className="space-y-2">
                  {[1, 2, 3].map(i => (
                    <div key={i} className="h-20 card animate-pulse" />
                  ))}
                </div>
              ) : devices.length === 0 ? (
                <div className="card p-8 text-center">
                  <HardDrive className="w-10 h-10 text-slate-300 dark:text-slate-600 mx-auto mb-2" />
                  <p className="text-slate-500 text-sm">No devices detected</p>
                </div>
              ) : (
                devices.map(device => (
                  <button
                    key={device.id}
                    onClick={() => setSelectedDevice(device)}
                    className={clsx(
                      'w-full text-left p-4 rounded-xl border transition-all',
                      selectedDevice?.id === device.id
                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 ring-1 ring-blue-500'
                        : 'card hover:border-slate-300 dark:hover:border-slate-600'
                    )}
                  >
                    <div className="flex items-center gap-3">
                      <HardDrive className={clsx('w-5 h-5', selectedDevice?.id === device.id ? 'text-blue-500' : 'text-slate-400')} />
                      <div className="flex-1 min-w-0">
                        <p className="font-medium text-slate-900 dark:text-white text-sm truncate">{device.name}</p>
                        <p className="text-xs text-slate-500 dark:text-slate-400 truncate">{device.model}</p>
                        <p className="text-xs text-slate-400 mt-0.5">{device.sizeFormatted} • {device.fileSystem} • {device.type}</p>
                      </div>
                      {selectedDevice?.id === device.id && (
                        <CheckCircle2 className="w-5 h-5 text-blue-500 shrink-0" />
                      )}
                    </div>
                  </button>
                ))
              )}
            </div>
          </div>

          {/* Wipe Method Selection */}
          <div>
            <h2 className="text-sm font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-3">
              2. Select Wipe Method
            </h2>
            <div className="space-y-2">
              {WIPE_METHODS.map(method => (
                <button
                  key={method.method}
                  onClick={() => setSelectedMethod(method.method)}
                  className={clsx(
                    'w-full text-left p-4 rounded-xl border transition-all',
                    selectedMethod === method.method
                      ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 ring-1 ring-blue-500'
                      : 'card hover:border-slate-300 dark:hover:border-slate-600'
                  )}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold text-slate-900 dark:text-white">{method.name}</span>
                        {method.recommended && (
                          <span className="badge bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 text-xs">Recommended</span>
                        )}
                      </div>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 line-clamp-2">{method.description}</p>
                      <div className="flex items-center gap-3 mt-2">
                        <span className="text-xs text-slate-400">{method.passes} pass{method.passes > 1 ? 'es' : ''}</span>
                        <span className="text-xs text-slate-400">⏱ {method.timeEstimate}</span>
                        <div className="flex gap-0.5">
                          {[1,2,3,4,5].map(n => (
                            <div key={n} className={clsx(
                              'w-2 h-2 rounded-full',
                              n <= method.securityLevel ? 'bg-blue-500' : 'bg-slate-200 dark:bg-slate-700'
                            )} />
                          ))}
                        </div>
                      </div>
                    </div>
                    {selectedMethod === method.method && (
                      <CheckCircle2 className="w-5 h-5 text-blue-500 shrink-0 mt-0.5" />
                    )}
                  </div>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Proceed button */}
      {step === 'select' && (
        <div className="mt-8 flex justify-end">
          <button
            disabled={!selectedDevice || !selectedMethod}
            onClick={() => setStep('confirm1')}
            className="btn-danger text-base px-8 py-3 disabled:opacity-40"
          >
            <ChevronRight className="w-5 h-5" />
            Proceed to Confirmation
          </button>
        </div>
      )}

      {/* Step 2: First confirmation */}
      {step === 'confirm1' && (
        <ConfirmationModal
          title="⚠️ Warning: Irreversible Action"
          device={selectedDevice!}
          method={WIPE_METHODS.find(m => m.method === selectedMethod)!}
          onBack={() => setStep('select')}
          onNext={() => setStep('confirm2')}
          nextLabel="I Understand, Continue"
        >
          <div className="space-y-3 text-sm">
            <p className="text-slate-700 dark:text-slate-300">
              You are about to <strong className="text-red-600 dark:text-red-400">permanently destroy all data</strong> on:
            </p>
            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3">
              <p className="font-mono text-red-700 dark:text-red-400 font-semibold">{selectedDevice?.name}</p>
              <p className="text-red-600 dark:text-red-400 text-xs mt-1">{selectedDevice?.model} — {selectedDevice?.sizeFormatted}</p>
            </div>
            <ul className="space-y-1.5 text-slate-600 dark:text-slate-400">
              <li>• All files, folders, and partitions will be destroyed</li>
              <li>• The wipe cannot be stopped once verified</li>
              <li>• Data recovery will be impossible after completion</li>
              <li>• Method: <strong>{WIPE_METHODS.find(m => m.method === selectedMethod)?.name}</strong></li>
            </ul>
          </div>
        </ConfirmationModal>
      )}

      {/* Step 3: Type device name */}
      {step === 'confirm2' && (
        <ConfirmationModal
          title="Type Device Name to Confirm"
          device={selectedDevice!}
          method={WIPE_METHODS.find(m => m.method === selectedMethod)!}
          onBack={() => setStep('confirm1')}
          onNext={() => setStep('confirm3')}
          nextDisabled={confirmName !== selectedDevice?.name}
          nextLabel="Confirm Device"
        >
          <div className="space-y-4 text-sm">
            <p className="text-slate-700 dark:text-slate-300">
              To confirm, type the exact device name below:
            </p>
            <code className="block text-center text-blue-600 dark:text-blue-400 bg-slate-100 dark:bg-slate-700 px-4 py-2 rounded-lg font-mono font-bold text-base">
              {selectedDevice?.name}
            </code>
            <input
              type="text"
              value={confirmName}
              onChange={(e) => setConfirmName(e.target.value)}
              placeholder="Type device name here..."
              className="input-field"
              autoFocus
            />
            {confirmName && confirmName !== selectedDevice?.name && (
              <p className="text-red-500 text-xs flex items-center gap-1">
                <XCircle className="w-3.5 h-3.5" /> Device name does not match
              </p>
            )}
            {confirmName === selectedDevice?.name && (
              <p className="text-green-500 text-xs flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5" /> Device name confirmed
              </p>
            )}
          </div>
        </ConfirmationModal>
      )}

      {/* Step 4: Final checkbox */}
      {step === 'confirm3' && (
        <ConfirmationModal
          title="🚨 Final Confirmation"
          device={selectedDevice!}
          method={WIPE_METHODS.find(m => m.method === selectedMethod)!}
          onBack={() => setStep('confirm2')}
          onNext={handleStartWipe}
          nextDisabled={!confirmCheckbox}
          nextLabel="Start Secure Wipe"
          nextDanger
        >
          <div className="space-y-4">
            <div className="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-lg p-4">
              <p className="text-sm font-semibold text-amber-700 dark:text-amber-400">Last Chance Warning</p>
              <p className="text-xs text-amber-600 dark:text-amber-400 mt-1">
                This is the final step. Clicking "Start Secure Wipe" will begin the irreversible data destruction process.
              </p>
            </div>
            <label className="flex items-start gap-3 cursor-pointer group">
              <input
                type="checkbox"
                checked={confirmCheckbox}
                onChange={(e) => setConfirmCheckbox(e.target.checked)}
                className="mt-0.5 w-4 h-4 text-red-600 rounded border-slate-300 dark:border-slate-600 focus:ring-red-500"
              />
              <span className="text-sm text-slate-700 dark:text-slate-300">
                I confirm that I want to <strong>permanently and irreversibly destroy</strong> all data on{' '}
                <strong className="text-red-600 dark:text-red-400">{selectedDevice?.name}</strong> using{' '}
                <strong>{WIPE_METHODS.find(m => m.method === selectedMethod)?.name}</strong>. I understand this action cannot be undone.
              </span>
            </label>
          </div>
        </ConfirmationModal>
      )}
    </div>
  )
}

function ConfirmationModal({
  title, device, method, children, onBack, onNext,
  nextLabel, nextDisabled, nextDanger
}: {
  title: string
  device: DeviceInfoDto
  method: any
  children: React.ReactNode
  onBack: () => void
  onNext: () => void
  nextLabel: string
  nextDisabled?: boolean
  nextDanger?: boolean
}) {
  return (
    <div className="max-w-xl mx-auto">
      <div className="card p-8 shadow-xl">
        <h2 className="text-xl font-bold text-slate-900 dark:text-white mb-2">{title}</h2>
        <div className="flex items-center gap-2 mb-6 text-xs text-slate-500 dark:text-slate-400">
          <Shield className="w-3.5 h-3.5" />
          <span>{device.name} • {method?.name}</span>
        </div>
        <div className="mb-8">{children}</div>
        <div className="flex items-center gap-3">
          <button onClick={onBack} className="btn-secondary flex-1">
            ← Back
          </button>
          <button
            onClick={onNext}
            disabled={nextDisabled}
            className={clsx(
              'flex-1 font-semibold py-2.5 px-4 rounded-lg transition-all disabled:opacity-40 disabled:cursor-not-allowed',
              nextDanger
                ? 'bg-red-600 hover:bg-red-700 text-white'
                : 'btn-primary'
            )}
          >
            {nextLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

function WipeProgressView({
  job, progress, isDone, onCancel, onReset, cancelling
}: {
  job: WipeJobDto | null
  progress: any
  isDone: boolean
  onCancel: () => void
  onReset: () => void
  cancelling: boolean
}) {
  const pct = progress?.progressPercent ?? 0
  const isSuccess = progress?.status === 'COMPLETED' || isDone && !progress?.errorMessage
  const isFailed = progress?.status === 'FAILED'
  const isCancelled = progress?.status === 'CANCELLED'

  return (
    <div className="max-w-2xl mx-auto">
      <div className="card p-8 shadow-xl">
        {/* Header */}
        <div className="flex items-center gap-4 mb-8">
          {isDone ? (
            isSuccess ? (
              <div className="w-14 h-14 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center">
                <CheckCircle2 className="w-8 h-8 text-green-500" />
              </div>
            ) : (
              <div className="w-14 h-14 rounded-full bg-red-100 dark:bg-red-900/30 flex items-center justify-center">
                <XCircle className="w-8 h-8 text-red-500" />
              </div>
            )
          ) : (
            <div className="w-14 h-14 rounded-full bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center">
              <Trash2 className="w-8 h-8 text-blue-500 animate-pulse" />
            </div>
          )}
          <div>
            <h2 className="text-xl font-bold text-slate-900 dark:text-white">
              {isDone
                ? isSuccess ? '✅ Wipe Complete' : isCancelled ? '⚠️ Wipe Cancelled' : '❌ Wipe Failed'
                : '🔄 Wiping in Progress...'}
            </h2>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-0.5">
              {job?.deviceName} • {job?.wipeMethodName}
            </p>
          </div>
        </div>

        {/* Progress bar */}
        <div className="mb-6">
          <div className="flex items-center justify-between text-sm mb-2">
            <span className="text-slate-700 dark:text-slate-300 font-medium">
              {progress?.statusMessage || 'Processing...'}
            </span>
            <span className="font-bold text-slate-900 dark:text-white">{pct}%</span>
          </div>
          <div className="wipe-progress-bar">
            <div
              className={clsx(
                'wipe-progress-fill',
                isDone && isSuccess && 'bg-green-500 bg-none',
                isDone && (isFailed || isCancelled) && 'bg-red-500 bg-none'
              )}
              style={{ width: `${pct}%` }}
            />
          </div>
          {progress?.currentPass != null && progress.totalPasses > 0 && !isDone && (
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-1.5">
              Pass {progress.currentPass} of {progress.totalPasses}
            </p>
          )}
        </div>

        {/* Stats grid */}
        {!isDone && (
          <div className="grid grid-cols-3 gap-4 mb-8">
            <div className="bg-slate-50 dark:bg-slate-700/40 rounded-xl p-4 text-center">
              <p className="text-xl font-bold text-slate-900 dark:text-white">
                {progress?.wipeSpeedMbps?.toFixed(1) ?? '0'} <span className="text-sm font-normal">MB/s</span>
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Speed</p>
            </div>
            <div className="bg-slate-50 dark:bg-slate-700/40 rounded-xl p-4 text-center">
              <p className="text-xl font-bold text-slate-900 dark:text-white">
                {formatEta(progress?.estimatedTimeRemainingSeconds ?? 0)}
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">ETA</p>
            </div>
            <div className="bg-slate-50 dark:bg-slate-700/40 rounded-xl p-4 text-center">
              <p className="text-xl font-bold text-slate-900 dark:text-white">
                {formatBytes(progress?.bytesWiped ?? 0)}
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Written</p>
            </div>
          </div>
        )}

        {/* Done actions */}
        {isDone ? (
          <div className="space-y-3">
            {isSuccess && job?.certificateId && (
              <button
                onClick={async () => {
                  try {
                    await downloadFile(
                      `/api/reports/certificate/${job.jobId}`,
                      `wipe-certificate-${job.jobId.substring(0, 8)}.pdf`
                    )
                    toast.success('Certificate downloaded!')
                  } catch {
                    toast.error('Failed to download certificate. Please try again.')
                  }
                }}
                className="btn-primary w-full justify-center py-3"
              >
                <Shield className="w-5 h-5" />
                Download Wipe Certificate (PDF)
              </button>
            )}
            <button onClick={onReset} className="btn-secondary w-full justify-center py-3">
              Start New Wipe
            </button>
          </div>
        ) : (
          <button
            onClick={onCancel}
            disabled={cancelling}
            className="w-full flex items-center justify-center gap-2 py-3 border border-red-300 dark:border-red-800 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg font-medium transition-colors"
          >
            <StopCircle className="w-5 h-5" />
            {cancelling ? 'Cancelling...' : 'Cancel Wipe'}
          </button>
        )}
      </div>
    </div>
  )
}

function formatEta(seconds: number): string {
  if (seconds <= 0) return '--'
  if (seconds < 60) return `${seconds}s`
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}m ${s}s`
}

function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}
