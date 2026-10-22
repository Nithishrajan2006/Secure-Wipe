import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Shield, CheckCircle2, XCircle, Loader2, HardDrive, Clock, Hash } from 'lucide-react'
import { verifyCertificate, type VerifyCertificateDto } from '../api/reports'

export default function VerifyPage() {
  const { certificateId } = useParams<{ certificateId: string }>()
  const [data, setData] = useState<VerifyCertificateDto | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!certificateId) {
      setData({ valid: false, certificateId: '' })
      setLoading(false)
      return
    }
    verifyCertificate(certificateId)
      .then(setData)
      .catch(() => setData({ valid: false, certificateId: certificateId }))
      .finally(() => setLoading(false))
  }, [certificateId])

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4">
      {/* Background glow */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-40 -right-40 w-96 h-96 bg-blue-600/10 rounded-full blur-3xl" />
        <div className="absolute -bottom-40 -left-40 w-96 h-96 bg-indigo-600/10 rounded-full blur-3xl" />
      </div>

      <div className="w-full max-w-2xl relative z-10">
        {/* Header branding */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center">
            <Shield className="w-6 h-6 text-white" />
          </div>
          <div>
            <h1 className="text-white font-bold text-xl leading-none">SecureWipe</h1>
            <p className="text-slate-400 text-xs mt-0.5">Certificate Verification</p>
          </div>
        </div>

        {loading ? (
          <div className="bg-slate-900 rounded-2xl border border-slate-700/50 p-12 text-center">
            <Loader2 className="w-10 h-10 text-blue-400 animate-spin mx-auto mb-4" />
            <p className="text-slate-300 font-medium">Verifying certificate…</p>
            <p className="text-slate-500 text-sm mt-1">Looking up certificate ID</p>
          </div>
        ) : data?.valid ? (
          <ValidCertificate data={data} />
        ) : (
          <InvalidCertificate certificateId={certificateId ?? ''} />
        )}

        <p className="text-center text-slate-600 text-xs mt-6">
          SecureWipe · Data Security You Can Trust
        </p>
      </div>
    </div>
  )
}

function ValidCertificate({ data }: { data: VerifyCertificateDto }) {
  return (
    <div className="bg-slate-900 rounded-2xl border border-slate-700/50 shadow-2xl overflow-hidden">
      {/* Green success banner */}
      <div className="bg-green-600/20 border-b border-green-600/30 px-6 py-5 flex items-center gap-4">
        <div className="w-12 h-12 rounded-full bg-green-500/20 flex items-center justify-center shrink-0">
          <CheckCircle2 className="w-7 h-7 text-green-400" />
        </div>
        <div>
          <h2 className="text-green-400 font-bold text-lg leading-none">✔ Certificate Verified</h2>
          <p className="text-green-500/80 text-sm mt-1">
            This certificate is authentic and on record.
          </p>
        </div>
      </div>

      <div className="p-6 space-y-5">
        {/* Certificate ID */}
        <div className="bg-slate-800/60 rounded-xl p-4 border border-slate-700/50">
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
            Certificate ID
          </p>
          <p className="text-blue-400 font-mono font-semibold text-sm">{data.certificateId}</p>
        </div>

        {/* Two-column grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Section title="Device Information" icon={<HardDrive className="w-4 h-4" />}>
            <Row label="Device Name"    value={data.deviceName} />
            <Row label="Model"          value={data.deviceModel} />
            <Row label="Serial Number"  value={data.serialNumber} />
            <Row label="Capacity"       value={data.capacity} />
            <Row label="Drive Type"     value={data.deviceType} />
            <Row label="Device Path"    value={data.devicePath} />
            {data.fileSystem && data.fileSystem !== 'N/A' && (
              <Row label="File System" value={data.fileSystem} />
            )}
          </Section>

          <Section title="Wipe Operation" icon={<Shield className="w-4 h-4" />}>
            <Row label="Wipe Method"    value={data.wipeMethod} />
            <Row label="Algorithm"      value={data.algorithm} />
            <Row label="Start Time"     value={data.startedAt} />
            <Row label="End Time"       value={data.completedAt} />
            <Row label="Duration"       value={data.duration} />
            <Row label="Operator"       value={data.operator} />
            <Row
              label="Status"
              value={
                <span className="inline-flex items-center gap-1 text-green-400 font-semibold">
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  Successfully Wiped
                </span>
              }
            />
          </Section>
        </div>

        {/* Certificate metadata */}
        <div className="bg-slate-800/40 rounded-xl p-4 border border-slate-700/50">
          <div className="flex items-center gap-2 mb-3">
            <Clock className="w-4 h-4 text-slate-400" />
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Certificate Metadata
            </p>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <Row label="Issued At"      value={data.issuedAt} />
            <Row label="Job ID"         value={data.jobId ? data.jobId.substring(0, 12) + '…' : 'N/A'} />
          </div>
        </div>

        {/* SHA-256 Hash */}
        <div className="bg-slate-800/40 rounded-xl p-4 border border-slate-700/50">
          <div className="flex items-center gap-2 mb-2">
            <Hash className="w-4 h-4 text-slate-400" />
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              SHA-256 Certificate Hash
            </p>
          </div>
          <p className="text-xs font-mono text-slate-400 break-all leading-relaxed">
            {data.sha256Hash}
          </p>
        </div>
      </div>
    </div>
  )
}

function InvalidCertificate({ certificateId }: { certificateId: string }) {
  return (
    <div className="bg-slate-900 rounded-2xl border border-slate-700/50 shadow-2xl overflow-hidden">
      {/* Red error banner */}
      <div className="bg-red-600/20 border-b border-red-600/30 px-6 py-5 flex items-center gap-4">
        <div className="w-12 h-12 rounded-full bg-red-500/20 flex items-center justify-center shrink-0">
          <XCircle className="w-7 h-7 text-red-400" />
        </div>
        <div>
          <h2 className="text-red-400 font-bold text-lg leading-none">❌ Certificate Not Found</h2>
          <p className="text-red-500/80 text-sm mt-1">
            No matching certificate exists in the SecureWipe database.
          </p>
        </div>
      </div>

      <div className="p-6 space-y-4">
        {certificateId && (
          <div className="bg-slate-800/60 rounded-xl p-4 border border-slate-700/50">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
              Queried ID
            </p>
            <p className="text-slate-300 font-mono text-sm break-all">{certificateId}</p>
          </div>
        )}

        <div className="bg-amber-900/20 border border-amber-700/40 rounded-xl p-4 text-sm text-amber-400/80 space-y-1.5">
          <p className="font-semibold text-amber-400">Possible reasons:</p>
          <ul className="list-disc list-inside space-y-1 text-amber-400/70">
            <li>The certificate ID is incorrect or has been altered.</li>
            <li>The wipe operation was cancelled before completion.</li>
            <li>This certificate was not generated by SecureWipe.</li>
          </ul>
        </div>

        <p className="text-slate-500 text-xs text-center">
          If you believe this is an error, contact your SecureWipe administrator.
        </p>
      </div>
    </div>
  )
}

function Section({
  title,
  icon,
  children,
}: {
  title: string
  icon: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <div className="bg-slate-800/40 rounded-xl p-4 border border-slate-700/50">
      <div className="flex items-center gap-2 mb-3">
        <span className="text-blue-400">{icon}</span>
        <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">{title}</p>
      </div>
      <div className="space-y-2">{children}</div>
    </div>
  )
}

function Row({
  label,
  value,
}: {
  label: string
  value?: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-slate-500">{label}</span>
      <span className="text-sm text-slate-200 font-medium break-words">
        {value ?? <span className="text-slate-500 font-normal">N/A</span>}
      </span>
    </div>
  )
}
