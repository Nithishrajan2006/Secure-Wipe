// ========== AUTH TYPES ==========
export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  fullName: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  userId: number
  username: string
  email: string
  fullName: string
  role: UserRole
}

export interface UserDto {
  id: number
  username: string
  email: string
  fullName: string
  role: UserRole
  isActive: boolean
  createdAt: string
  totalWipes: number
}

// ========== ENUMS ==========
export type UserRole = 'ADMIN' | 'USER'

export type WipeMethod = 'ZEROS' | 'DOD_3PASS' | 'DOD_7PASS' | 'RANDOM' | 'ATA_SECURE_ERASE'

export type WipeStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export type DeviceType = 'HDD' | 'SSD' | 'USB' | 'EXTERNAL_HDD' | 'NVME' | 'PARTITION' | 'FILE' | 'FOLDER'

// ========== DEVICE TYPES ==========
export interface PartitionDto {
  name: string
  mountPoint: string
  fileSystem: string
  size: number
  sizeFormatted: string
  path: string
  type: DeviceType
}

export interface DeviceInfoDto {
  id: string
  name: string
  model: string
  path: string
  size: number
  sizeFormatted: string
  type: DeviceType
  fileSystem: string
  mountPoint: string
  serialNumber: string
  firmwareVersion: string
  removable: boolean
  partitions: PartitionDto[]
}

// ========== WIPE TYPES ==========
export interface StartWipeRequest {
  devicePath: string
  deviceName: string
  deviceType: DeviceType
  wipeMethod: WipeMethod
  deviceSize?: number
  fileSystem?: string
  mountPoint?: string
  confirmationText: string
}

export interface WipeJobDto {
  id: number
  jobId: string
  userId: number
  username: string
  deviceName: string
  devicePath: string
  deviceType: DeviceType
  deviceSize: number
  deviceSizeFormatted: string
  fileSystem: string
  mountPoint: string
  wipeMethod: WipeMethod
  wipeMethodName: string
  status: WipeStatus
  progressPercent: number
  bytesWiped: number
  wipeSpeedMbps: number
  estimatedTimeRemainingSeconds: number
  currentPass: number
  totalPasses: number
  startedAt: string
  completedAt: string
  errorMessage: string
  certificateId: string
  createdAt: string
  durationSeconds: number
}

export interface WipeProgressDto {
  jobId: string
  status: WipeStatus
  progressPercent: number
  bytesWiped: number
  totalBytes: number
  wipeSpeedMbps: number
  estimatedTimeRemainingSeconds: number
  currentPass: number
  totalPasses: number
  statusMessage: string
  errorMessage: string
}

// ========== DASHBOARD TYPES ==========
export interface DailyWipeStats {
  date: string
  count: number
  dataWiped: number
}

export interface DashboardStatsDto {
  totalWipes: number
  totalDataWipedBytes: number
  totalDataWipedFormatted: string
  activeOperations: number
  successRate: number
  completedWipes: number
  failedWipes: number
  recentWipes: WipeJobDto[]
  wipesByMethod: Record<string, number>
  wipesByStatus: Record<string, number>
  dailyStats: DailyWipeStats[]
}

// ========== AUDIT TYPES ==========
export interface AuditLogDto {
  id: number
  userId: number
  username: string
  action: string
  resourceType: string
  resourceId: string
  details: string
  ipAddress: string
  userAgent: string
  status: string
  createdAt: string
}

// ========== API RESPONSE ==========
export interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
  timestamp: string
  error?: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

// ========== WIPE METHOD INFO ==========
export interface WipeMethodInfo {
  method: WipeMethod
  name: string
  passes: number
  description: string
  recommended?: boolean
  suitableFor: string[]
  timeEstimate: string
  securityLevel: number
}

export const WIPE_METHODS: WipeMethodInfo[] = [
  {
    method: 'ZEROS',
    name: 'Single Pass Zeros',
    passes: 1,
    description: 'Overwrites entire storage with binary zeros. Fast and suitable for basic data removal before disposal.',
    suitableFor: ['Non-sensitive data', 'Personal devices', 'Quick wipe'],
    timeEstimate: 'Fast (~1 min/GB)',
    securityLevel: 1,
  },
  {
    method: 'DOD_3PASS',
    name: 'DoD 5220.22-M (3-Pass)',
    passes: 3,
    description: 'US Department of Defense standard. Three passes: zeros, ones, then random data. Meets most government compliance requirements.',
    suitableFor: ['Government data', 'Sensitive documents', 'Official records'],
    timeEstimate: 'Medium (~3 min/GB)',
    securityLevel: 3,
    recommended: true,
  },
  {
    method: 'DOD_7PASS',
    name: 'DoD 5220.22-M ECE (7-Pass)',
    passes: 7,
    description: 'Enhanced DoD standard for classified data. Seven overwrite passes with alternating patterns. Highest software-based security level.',
    suitableFor: ['Classified data', 'Top-secret files', 'Maximum security wipe'],
    timeEstimate: 'Slow (~7 min/GB)',
    securityLevel: 5,
  },
  {
    method: 'RANDOM',
    name: 'Random Data Overwrite',
    passes: 1,
    description: 'Overwrites with cryptographically secure random data from SecureRandom. Effective against most data recovery techniques.',
    suitableFor: ['Encrypted drives', 'SSD-friendly', 'Mixed use'],
    timeEstimate: 'Fast (~1.5 min/GB)',
    securityLevel: 2,
  },
  {
    method: 'ATA_SECURE_ERASE',
    name: 'ATA Secure Erase',
    passes: 1,
    description: 'Issues native ATA Secure Erase command directly to SSD firmware. Most effective method for SSDs as it resets all memory cells simultaneously.',
    suitableFor: ['SSDs only', 'NVMe drives', 'Hardware-level erase'],
    timeEstimate: 'Very Fast (~30 sec)',
    securityLevel: 5,
  },
]
