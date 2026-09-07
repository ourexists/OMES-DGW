/** 固定参数开关量：电源 / 运行 / 异常，用于三色指示灯，不在实时点位列表中展示 */
export const SWITCH_LIGHTS = [
  { key: 'power', label: '电源', code: 'POWER', field: 'powerSwitchPointCode' },
  { key: 'running', label: '运行', code: 'RUN', field: 'runningSwitchPointCode' },
  { key: 'fault', label: '异常', code: 'FAULT', field: 'faultSwitchPointCode' }
]

const RESERVED_SWITCH_CODES = new Set(SWITCH_LIGHTS.map(item => item.code))

const listPoints = device => {
  const fromMap = Object.values(device?.points || {})
  if (fromMap.length) return fromMap
  return Array.isArray(device?.pointList) ? device.pointList : []
}

const trimCode = value => String(value || '').trim()

const findPointByCode = (points, code) => {
  const normalized = trimCode(code)
  if (!normalized) return null
  return points.find(item => trimCode(item?.code) === normalized) || null
}

export const getSwitchPointCodes = collectConfig => {
  const codes = new Set()
  SWITCH_LIGHTS.forEach(item => {
    const bound = trimCode(collectConfig?.[item.field])
    codes.add(bound || item.code)
  })
  return codes
}

export const isFixedSwitchPoint = (point, collectConfig) => {
  const code = trimCode(point?.code)
  if (!code) return false
  if (RESERVED_SWITCH_CODES.has(code)) return true
  return getSwitchPointCodes(collectConfig).has(code)
}

/** 已配置的固定开关量（电源 / 运行 / 异常），用于历史甘特图 */
export const resolveConfiguredSwitchPoints = collectConfig => {
  const points = Array.isArray(collectConfig?.points) ? collectConfig.points : []
  return SWITCH_LIGHTS.map(kind => {
    const boundCode = trimCode(collectConfig?.[kind.field])
    const point = findPointByCode(points, boundCode || kind.code)
    if (!boundCode && !point) return null
    return {
      ...kind,
      code: trimCode(point?.code || boundCode || kind.code),
      name: String(point?.name || '').trim() || kind.label,
      point
    }
  }).filter(Boolean)
}

export const parseSwitchOn = value => {
  if (value === true || value === 1) return true
  if (value === false || value === 0) return false
  const text = String(value ?? '').trim().toLowerCase()
  if (!text) return null
  if (['true', '1', 'on', 'yes', '开'].includes(text)) return true
  if (['false', '0', 'off', 'no', '关'].includes(text)) return false
  return null
}

export const resolveSwitchLights = device => {
  const points = listPoints(device)
  const collectConfig = device?.collectConfig || {}

  return SWITCH_LIGHTS.map(kind => {
    const boundCode = trimCode(collectConfig[kind.field])
    const point = findPointByCode(points, boundCode || kind.code)
    const configured = Boolean(boundCode || point)
    const on = configured ? parseSwitchOn(point?.value ?? point?.rawValue) : null
    let state = 'unset'
    if (configured) {
      if (point?.error && on == null) state = 'error'
      else if (on === true) state = 'on'
      else if (on === false) state = 'off'
      else state = 'unknown'
    }

    return {
      ...kind,
      configured,
      state,
      point,
      value: point?.value,
      error: point?.error || ''
    }
  })
}
