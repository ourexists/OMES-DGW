export const POINT_KIND = {
  COLLECT: 'COLLECT',
  EXCEPTION: 'EXCEPTION',
  WRITE: 'WRITE'
}

export const resolvePointKind = item => {
  const kind = String(item?.pointKind || '').toUpperCase()
  if (kind === POINT_KIND.EXCEPTION || kind === POINT_KIND.WRITE || kind === POINT_KIND.COLLECT) {
    return kind
  }
  return ''
}

export const isChartablePoint = item => resolvePointKind(item) === POINT_KIND.COLLECT

export const isCollectablePoint = item => {
  const kind = resolvePointKind(item)
  return kind === POINT_KIND.COLLECT || kind === POINT_KIND.EXCEPTION
}
