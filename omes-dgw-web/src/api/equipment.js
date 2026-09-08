import request from '@/utils/request'

let deviceCategoriesAllRequest = null
let deviceProtocolsRequest = null

async function mutateDeviceCategory(action, data) {
  const response = await request({ url: `/device/categories/${action}`, method: 'post', data })
  deviceCategoriesAllRequest = null
  return response
}

export const createDeviceCategory = data => mutateDeviceCategory('create', data)
export const updateDeviceCategory = data => mutateDeviceCategory('update', data)
export const deleteDeviceCategory = id => mutateDeviceCategory('delete', { id })

// 获取全部设备分组，默认复用请求结果，刷新时重新请求
export function getDeviceCategoriesAll(refresh = false) {
  if (refresh) deviceCategoriesAllRequest = null
  if (!deviceCategoriesAllRequest) {
    deviceCategoriesAllRequest = request({
      url: '/device/categories/all',
      method: 'get'
    }).catch(error => {
      deviceCategoriesAllRequest = null
      throw error
    })
  }

  return deviceCategoriesAllRequest
}

// 获取设备协议类型，只请求一次并复用同一个请求结果
export function getDeviceProtocols() {
  if (!deviceProtocolsRequest) {
    deviceProtocolsRequest = request({
      url: '/device/devices/protocols',
      method: 'get'
    }).catch(error => {
      deviceProtocolsRequest = null
      throw error
    })
  }

  return deviceProtocolsRequest
}

// 分页查询设备
export function getDevicePage(data) {
  return request({
    url: '/device/devices/page',
    method: 'post',
    data
  })
}

// 导入设备
export function importDevices(file) {
  const formData = new FormData()
  formData.append('file', file)

  return request({
    url: '/device/devices/import',
    method: 'post',
    data: formData
  })
}

// 导出设备
export function exportDevices(data) {
  return request({
    url: '/device/devices/export',
    method: 'post',
    data,
    responseType: 'blob'
  })
}

// 下载设备导入模板
export function downloadDeviceImportTemplate() {
  return request({
    url: '/device/devices/import/template',
    method: 'get',
    responseType: 'blob'
  })
}

// 获取设备实时列表
export function getDeviceRealtimeList() {
  return request({
    url: '/device/realtime/list',
    method: 'get'
  })
}

// 打开设备实时连接
export function openDeviceRealtimeConnection(deviceId) {
  return request({
    url: '/device/realtime/connection/open',
    method: 'get',
    params: { deviceId }
  })
}

// 关闭设备实时连接
export function closeDeviceRealtimeConnection(deviceId) {
  return request({
    url: '/device/realtime/connection/close',
    method: 'get',
    params: { deviceId }
  })
}

// 获取设备历史曲线
export function getDeviceSnapshotChar(data) {
  return request({
    url: '/device/snapshot/chart',
    method: 'post',
    data
  })
}

// 获取设备历史快照
export function getDeviceSnapshotHistory(data) {
  return request({
    url: '/device/snapshot/history',
    method: 'post',
    data
  })
}

// 获取设备性能指标汇总曲线
export function getCollectMetricsSummaryChart(params) {
  return request({
    url: '/device/collect-metrics/chart',
    method: 'get',
    params
  })
}

// 测试写入设备点位
export function writeDevicePoint(data) {
  return request({
    url: '/device/points/write',
    method: 'post',
    data
  })
}

// 分页查询写入指令历史
export function getDevicePointWriteHistory(data) {
  return request({
    url: '/device/points/write/history',
    method: 'post',
    data
  })
}

// 按任务 ID 聚合分页查询写入历史
export function getDevicePointWriteTaskHistory(data) {
  return request({
    url: '/device/points/write/history/tasks',
    method: 'post',
    data
  })
}

// 新建设备
export function createDevice(data) {
  return request({
    url: '/device/devices/create',
    method: 'post',
    data
  })
}

// 复制设备（含点位）
export function copyDevice(data) {
  return request({
    url: '/device/devices/copy',
    method: 'post',
    data
  })
}

// 测试设备连接
export function testDeviceConnection(data) {
  return request({
    url: '/device/devices/test-connection',
    method: 'post',
    data
  })
}

// 更新设备
export function updateDevice(data) {
  return request({
    url: '/device/devices/update',
    method: 'post',
    data
  })
}

// 删除设备
export function deleteDevice(id) {
  return request({
    url: '/device/devices/delete',
    method: 'post',
    data: { id }
  })
}

// 获取设备详情
export function getDeviceDetail(id) {
  return request({
    url: '/device/points/list',
    method: 'get',
    params: { deviceId:id }
  })
}

// 获取设备点位数据类型
export function getDevicePointDataTypes(deviceId) {
  return request({
    url: '/device/points/data-types',
    method: 'get',
    params: { deviceId }
  })
}

// 获取点位读写权限
export function getDevicePointAccessTypes() {
  return request({
    url: '/device/points/access-types',
    method: 'get'
  })
}

// 获取点位字节序枚举
export function getDevicePointByteOrders(deviceId) {
  return request({
    url: '/device/points/byte-orders',
    method: 'get',
    params: { deviceId }
  })
}

// 获取 Modbus 扫描区域枚举
export function getModscanAreas() {
  return request({
    url: '/modscan/areas',
    method: 'get'
  })
}

// 打开 Modbus 扫描会话
export function openModscanSession(data) {
  return request({
    url: '/modscan/session/open',
    method: 'post',
    data
  })
}

// 关闭 Modbus 扫描会话
export function closeModscanSession(data) {
  return request({
    url: '/modscan/session/close',
    method: 'post',
    data
  })
}

// 扫描 Modbus 点位
export function scanModbusPoints(data) {
  return request({
    url: '/modscan/scan',
    method: 'post',
    data
  })
}

// 保存设备点位
export function saveDevicePoint(data) {
  return request({
    url: '/device/points/save',
    method: 'post',
    data
  })
}

// 从其他设备复制点位到目标设备
export function copyDevicePoints(data) {
  return request({
    url: '/device/points/copy',
    method: 'post',
    data
  })
}

// 启用设备实时采集
export function enableDeviceRealtime(deviceId) {
  return request({
    url: '/device/realtime/enable',
    method: 'get',
    params: { deviceId }
  })
}

// 停止设备实时采集
export function stopDeviceRealtime(deviceId) {
  return request({
    url: '/device/realtime/stop',
    method: 'get',
    params: { deviceId }
  })
}

// 批量启用设备实时采集（后端循环启用，建连错开）
export function enableDeviceRealtimeBatch(deviceIds) {
  return request({
    url: '/device/realtime/enable/batch',
    method: 'post',
    data: { deviceIds },
    timeout: 60000
  })
}

// 批量停止设备实时采集
export function stopDeviceRealtimeBatch(deviceIds) {
  return request({
    url: '/device/realtime/stop/batch',
    method: 'post',
    data: { deviceIds },
    timeout: 60000
  })
}

export default {
  getDeviceCategoriesAll,
  getDeviceProtocols,
  openDeviceRealtimeConnection,
  closeDeviceRealtimeConnection,
  getDevicePage,
  importDevices,
  exportDevices,
  downloadDeviceImportTemplate,
  getDeviceRealtimeList,
  getDeviceSnapshotChar,
  getDeviceSnapshotHistory,
  getCollectMetricsSummaryChart,
  writeDevicePoint,
  getDevicePointWriteHistory,
  getDevicePointWriteTaskHistory,
  createDevice,
  copyDevice,
  testDeviceConnection,
  updateDevice,
  deleteDevice,
  getDeviceDetail,
  getDevicePointDataTypes,
  getDevicePointAccessTypes,
  getDevicePointByteOrders,
  getModscanAreas,
  openModscanSession,
  closeModscanSession,
  scanModbusPoints,
  saveDevicePoint,
  copyDevicePoints,
  enableDeviceRealtime,
  enableDeviceRealtimeBatch,
  stopDeviceRealtime,
  stopDeviceRealtimeBatch
}
