import request from '@/utils/request'

export function getMonitoringOverview(params) {
  return request({
    url: '/monitoring/overview',
    method: 'get',
    params
  })
}
