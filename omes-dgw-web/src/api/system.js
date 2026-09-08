import request from '@/utils/request'

export function restartSystem() {
  return request({ url: '/system/restart', method: 'post' })
}

export function getSystemRestartStatus() {
  return request({ url: '/system/restart', method: 'get', timeout: 3000, silent: true })
}

export function getStorePluginStatus() {
  return request({
    url: '/system/store-plugin',
    method: 'get'
  })
}

export function uploadStorePlugin(type, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/system/store-plugin/upload',
    method: 'post',
    params: { type },
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function removeStorePlugin(type) {
  return request({
    url: '/system/store-plugin',
    method: 'delete',
    params: { type }
  })
}

export function saveStorePluginConfig(data) {
  return request({
    url: '/system/store-plugin/config',
    timeout: 120000,
    method: 'put',
    data
  })
}

export function probeRedis(params) {
  return request({
    url: '/system/store-plugin/probe/redis',
    method: 'post',
    params
  })
}

export function probeInflux(params) {
  return request({
    url: '/system/store-plugin/probe/influx',
    method: 'post',
    params
  })
}

export function listRuntimeConfigSections(params) {
  return request({
    url: '/system/runtime-config',
    method: 'get',
    params
  })
}

export function getRuntimeConfigSection(id) {
  return request({
    url: `/system/runtime-config/${id}`,
    method: 'get'
  })
}

export function saveRuntimeConfigSection(id, data) {
  return request({
    url: `/system/runtime-config/${id}`,
    timeout: 120000,
    method: 'put',
    data
  })
}
