import request from '@/utils/request'

export function restartSystem() {
  return request({ url: '/system/restart', method: 'post' })
}

export function getSystemRestartStatus() {
  return request({ url: '/system/restart', method: 'get', timeout: 3000, silent: true })
}

export function getPortalInfo() {
  return request({
    url: '/system/portal',
    method: 'get'
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
