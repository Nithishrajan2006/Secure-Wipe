/**
 * Public certificate verification API.
 * This endpoint requires NO authentication — it is called by
 * the VerifyPage when a user scans the QR code on a certificate.
 */

export interface VerifyCertificateDto {
  valid: boolean
  certificateId: string
  jobId?: string
  deviceName?: string
  deviceModel?: string
  deviceType?: string
  devicePath?: string
  capacity?: string
  serialNumber?: string
  fileSystem?: string
  wipeMethod?: string
  algorithm?: string
  operator?: string
  status?: string
  startedAt?: string
  completedAt?: string
  duration?: string
  issuedAt?: string
  sha256Hash?: string
}

interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
}

export async function verifyCertificate(
  certificateId: string,
): Promise<VerifyCertificateDto> {
  // Use fetch directly — no auth token needed, this is a public endpoint.
  const res = await fetch(`/api/reports/verify/${encodeURIComponent(certificateId)}`)

  if (!res.ok) {
    // Backend returned an HTTP error — treat as "not found"
    return { valid: false, certificateId }
  }

  const json: ApiResponse<VerifyCertificateDto> = await res.json()
  return json.data
}
