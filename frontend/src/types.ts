export type ProviderType = 'OPENAI' | 'ANTHROPIC'

export type Model = { modelName: string; inputPrice: number; outputPrice: number }

export type Provider = {
  id: number
  name: string
  type: ProviderType
  baseUrl: string
  priority: number
  enabled: boolean
  hasApiKey: boolean
  models: Model[]
  createdAt: string
  updatedAt: string
  consecutiveFailures: number
  coolingDown: boolean
}

export type ConnectionTest = { ok: boolean; status: number; latencyMs: number; message: string; models: string[] }

export type ApiKey = {
  id: number
  name: string
  keyPrefix: string
  monthlyBudget: number | null
  rateLimitPerMinute: number | null
  enabled: boolean
  allowedProviderIds: number[]
  spentThisMonth: number
  createdAt: string
  lastUsedAt: string | null
}

export type Session = { token: string; role: 'ADMIN' | 'USER'; name: string }

export type Summary = {
  from: string
  to: string
  totalCost: number
  requests: number
  errors: number
  inputTokens: number
  outputTokens: number
  activeKeys: number
}

export type DailyRow = {
  date: string
  group: string
  cost: number
  requests: number
  inputTokens: number
  outputTokens: number
}

export type RequestRow = {
  id: number
  createdAt: string
  keyId: number
  keyName: string
  providerId: number | null
  providerName: string | null
  apiFormat: ProviderType
  model: string | null
  statusCode: number
  streamed: boolean
  inputTokens: number
  outputTokens: number
  cost: number
  latencyMs: number
  attempts: number
  errorMessage: string | null
}

export type RequestDetail = { request: RequestRow; requestBody: string | null; responseBody: string | null }

export type Page<T> = { items: T[]; page: number; size: number; total: number }

export type AlertSettings = { thresholds: number[]; webhookUrl: string; emails: string[]; emailConfigured: boolean }

export type DeliveryResult = { webhook: string; email: string }

export type AlertEvent = {
  id: number
  keyId: number
  keyName: string
  month: string
  thresholdPercent: number
  spent: number
  budget: number
  delivered: boolean
  errorMessage: string | null
  createdAt: string
}

export type Me = {
  keyId: number
  name: string
  keyPrefix: string
  monthlyBudget: number | null
  spentThisMonth: number
  rateLimitPerMinute: number | null
  month: string
}
