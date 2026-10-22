import { useState } from 'react'
import {
  HelpCircle, ChevronDown, ChevronUp, Shield, Trash2,
  HardDrive, FileText, Lock, AlertTriangle, CheckCircle2, Book
} from 'lucide-react'
import clsx from 'clsx'

interface FAQItem {
  question: string
  answer: string
}

interface Section {
  id: string
  icon: React.ReactNode
  title: string
  content: React.ReactNode
}

const faqs: FAQItem[] = [
  {
    question: 'What wipe method should I use for government data?',
    answer: 'For government and sensitive data, we recommend DoD 5220.22-M (3-Pass) at minimum, or DoD 5220.22-M ECE (7-Pass) for classified information. These methods comply with US Department of Defense standards and industry IT security policies.'
  },
  {
    question: 'Can wiped data be recovered?',
    answer: 'After a proper DoD-standard wipe, data recovery is extremely difficult and practically impossible with modern forensic tools. The ATA Secure Erase method for SSDs makes recovery virtually impossible as it operates at the firmware level.'
  },
  {
    question: 'How long does a wipe take?',
    answer: 'Duration depends on drive size and method. A 500GB HDD with Single Pass Zeros takes ~8 minutes. DoD 3-Pass takes ~24 minutes. DoD 7-Pass takes ~56 minutes. SSDs with ATA Secure Erase complete in under a minute regardless of size.'
  },
  {
    question: 'Can I cancel a wipe once started?',
    answer: 'Yes, you can cancel an ongoing wipe by clicking the "Cancel Wipe" button. The operation will stop at the current position. Note that partial wipes may not fully protect the data — a complete wipe is recommended for security compliance.'
  },
  {
    question: 'What is a Wipe Certificate?',
    answer: 'A Wipe Certificate is a PDF document that serves as official proof of data destruction. It contains the Certificate ID, date/time, device details, wipe method used, operator name, and a QR code for verification. It meets compliance requirements for IT asset disposal.'
  },
  {
    question: 'What is ATA Secure Erase?',
    answer: 'ATA Secure Erase is a command built into SSD and HDD firmware that instructs the drive to reset all storage cells. For SSDs, this is the most effective method as it erases data at the hardware level, including cells that normal software cannot access.'
  },
  {
    question: 'Why does a USB drive not support ATA Secure Erase?',
    answer: 'Most USB flash drives do not support ATA commands as they use a different interface protocol. For USB drives, use Random Data Overwrite or DoD 3-Pass instead.'
  },
]

export default function HelpPage() {
  const [openFaq, setOpenFaq] = useState<number | null>(null)
  const [openSection, setOpenSection] = useState<string | null>('getting-started')

  const sections: Section[] = [
    {
      id: 'getting-started',
      icon: <Book className="w-5 h-5" />,
      title: 'Getting Started',
      content: (
        <div className="space-y-4 text-sm text-slate-600 dark:text-slate-300">
          <p>Welcome to SecureWipe — your secure data destruction platform. Here's how to get started:</p>
          <ol className="space-y-3 list-none">
            {[
              { n: '1', title: 'Login', desc: 'Sign in with your credentials. Contact your administrator for access.' },
              { n: '2', title: 'View Devices', desc: 'Navigate to Devices to see all connected storage devices auto-detected by the system.' },
              { n: '3', title: 'Select & Wipe', desc: 'Click "Secure Wipe" on any device, select your wipe method, and follow the 3-step confirmation.' },
              { n: '4', title: 'Monitor Progress', desc: 'Watch real-time progress including speed, ETA, and current pass number.' },
              { n: '5', title: 'Download Certificate', desc: 'After completion, download your PDF Wipe Certificate for compliance records.' },
            ].map(s => (
              <li key={s.n} className="flex gap-3">
                <span className="w-6 h-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center shrink-0 mt-0.5 font-bold">{s.n}</span>
                <div>
                  <strong className="text-slate-900 dark:text-white">{s.title}:</strong> {s.desc}
                </div>
              </li>
            ))}
          </ol>
        </div>
      )
    },
    {
      id: 'wipe-methods',
      icon: <Trash2 className="w-5 h-5" />,
      title: 'Wipe Methods Comparison',
      content: (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-700">
                <th className="text-left py-2.5 pr-4 text-xs font-semibold text-slate-500 uppercase">Method</th>
                <th className="text-left py-2.5 pr-4 text-xs font-semibold text-slate-500 uppercase">Passes</th>
                <th className="text-left py-2.5 pr-4 text-xs font-semibold text-slate-500 uppercase">Security</th>
                <th className="text-left py-2.5 pr-4 text-xs font-semibold text-slate-500 uppercase">Speed</th>
                <th className="text-left py-2.5 text-xs font-semibold text-slate-500 uppercase">Best For</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
              {[
                { m: 'Single Pass Zeros', p: 1, s: '⭐', sp: 'Fast', b: 'Non-sensitive data' },
                { m: 'Random Overwrite', p: 1, s: '⭐⭐', sp: 'Fast', b: 'General use' },
                { m: 'DoD 3-Pass', p: 3, s: '⭐⭐⭐', sp: 'Medium', b: 'Government data ✓' },
                { m: 'DoD 7-Pass (ECE)', p: 7, s: '⭐⭐⭐⭐⭐', sp: 'Slow', b: 'Classified data' },
                { m: 'ATA Secure Erase', p: 1, s: '⭐⭐⭐⭐⭐', sp: 'Very Fast', b: 'SSDs/NVMe only' },
              ].map(row => (
                <tr key={row.m} className="hover:bg-slate-50 dark:hover:bg-slate-700/30">
                  <td className="py-3 pr-4 font-medium text-slate-900 dark:text-white">{row.m}</td>
                  <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">{row.p}</td>
                  <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">{row.s}</td>
                  <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">{row.sp}</td>
                  <td className="py-3 text-slate-600 dark:text-slate-400">{row.b}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )
    },
    {
      id: 'device-types',
      icon: <HardDrive className="w-5 h-5" />,
      title: 'Supported Device Types',
      content: (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
          {[
            { type: 'HDD', desc: 'Traditional hard disk drives. All wipe methods supported.' },
            { type: 'SSD', desc: 'Solid state drives. ATA Secure Erase recommended.' },
            { type: 'USB', desc: 'Flash drives. DoD 3-Pass or Random recommended.' },
            { type: 'NVMe', desc: 'High-speed PCIe drives. ATA Secure Erase supported.' },
            { type: 'External HDD', desc: 'Portable hard drives via USB. All methods supported.' },
            { type: 'Partitions', desc: 'Individual partitions can be targeted separately.' },
          ].map(d => (
            <div key={d.type} className="bg-slate-50 dark:bg-slate-700/40 rounded-lg p-3">
              <p className="font-semibold text-slate-900 dark:text-white">{d.type}</p>
              <p className="text-slate-500 dark:text-slate-400 text-xs mt-0.5">{d.desc}</p>
            </div>
          ))}
        </div>
      )
    },
    {
      id: 'certificates',
      icon: <FileText className="w-5 h-5" />,
      title: 'Wipe Certificates',
      content: (
        <div className="space-y-3 text-sm text-slate-600 dark:text-slate-300">
          <p>Wipe Certificates are automatically generated for all completed wipe operations and contain:</p>
          <ul className="space-y-2">
            {[
              'Unique Certificate ID for verification',
              'Date, time, and duration of the wipe',
              'Complete device details (name, model, size, serial)',
              'Wipe method used and number of passes',
              'Operator name and username',
              'QR code linking to online verification',
              'SecureWipe official branding',
              'Signature placeholders for supervisor approval',
            ].map(item => (
              <li key={item} className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-green-500 shrink-0 mt-0.5" />
                {item}
              </li>
            ))}
          </ul>
        </div>
      )
    },
    {
      id: 'security',
      icon: <Lock className="w-5 h-5" />,
      title: 'Security & Safety Features',
      content: (
        <div className="space-y-3 text-sm text-slate-600 dark:text-slate-300">
          <p>SecureWipe has multiple layers of protection to prevent accidental data destruction:</p>
          <ul className="space-y-2">
            {[
              'JWT authentication with 24-hour token expiry',
              'Role-based access (Admin / User) for feature control',
              '3-step confirmation dialog before any wipe starts',
              'Type exact device name to confirm (prevents mistakes)',
              'Mandatory checkbox acknowledgment of irreversibility',
              'Complete audit trail of every action',
              'IP address logging for security monitoring',
              'BCrypt password hashing (strength 12)',
            ].map(item => (
              <li key={item} className="flex items-start gap-2">
                <Shield className="w-4 h-4 text-blue-500 shrink-0 mt-0.5" />
                {item}
              </li>
            ))}
          </ul>
        </div>
      )
    },
  ]

  return (
    <div className="max-w-4xl mx-auto">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Help & Documentation</h1>
        <p className="text-slate-500 dark:text-slate-400 mt-1 text-sm">
          Everything you need to know about using SecureWipe
        </p>
      </div>

      {/* Warning banner */}
      <div className="mb-8 p-4 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-xl flex gap-3">
        <AlertTriangle className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />
        <div className="text-sm">
          <p className="font-semibold text-amber-700 dark:text-amber-400">Important Reminder</p>
          <p className="text-amber-600 dark:text-amber-400 mt-0.5">
            All wipe operations are <strong>permanent and irreversible</strong>. Always verify the device and ensure you have authorization before proceeding.
          </p>
        </div>
      </div>

      {/* Documentation sections */}
      <div className="space-y-3 mb-10">
        {sections.map(section => (
          <div key={section.id} className="card overflow-hidden">
            <button
              onClick={() => setOpenSection(openSection === section.id ? null : section.id)}
              className="w-full flex items-center justify-between px-6 py-4 text-left hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors"
            >
              <div className="flex items-center gap-3">
                <span className="text-blue-500">{section.icon}</span>
                <span className="font-semibold text-slate-900 dark:text-white">{section.title}</span>
              </div>
              {openSection === section.id
                ? <ChevronUp className="w-4 h-4 text-slate-400" />
                : <ChevronDown className="w-4 h-4 text-slate-400" />}
            </button>
            {openSection === section.id && (
              <div className="px-6 pb-5 border-t border-slate-100 dark:border-slate-700 pt-4">
                {section.content}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* FAQ */}
      <div>
        <h2 className="text-lg font-bold text-slate-900 dark:text-white mb-4 flex items-center gap-2">
          <HelpCircle className="w-5 h-5 text-blue-500" />
          Frequently Asked Questions
        </h2>
        <div className="space-y-2">
          {faqs.map((faq, i) => (
            <div key={i} className="card overflow-hidden">
              <button
                onClick={() => setOpenFaq(openFaq === i ? null : i)}
                className="w-full flex items-center justify-between px-5 py-4 text-left hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors"
              >
                <span className="font-medium text-slate-900 dark:text-white text-sm pr-4">{faq.question}</span>
                {openFaq === i
                  ? <ChevronUp className="w-4 h-4 text-slate-400 shrink-0" />
                  : <ChevronDown className="w-4 h-4 text-slate-400 shrink-0" />}
              </button>
              {openFaq === i && (
                <div className="px-5 pb-4 border-t border-slate-100 dark:border-slate-700 pt-3">
                  <p className="text-sm text-slate-600 dark:text-slate-300">{faq.answer}</p>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Contact */}
      <div className="mt-8 card p-6 text-center">
        <Shield className="w-10 h-10 text-blue-500 mx-auto mb-3" />
        <h3 className="font-semibold text-slate-900 dark:text-white mb-1">Need Support?</h3>
        <p className="text-sm text-slate-500 dark:text-slate-400 mb-3">
          Contact the SecureWipe IT Security Team
        </p>
        <div className="space-y-1 text-sm text-slate-600 dark:text-slate-300">
          <p>📧 support@securewipe.io</p>
          <p>🌐 securewipe.io</p>
        </div>
      </div>
    </div>
  )
}
