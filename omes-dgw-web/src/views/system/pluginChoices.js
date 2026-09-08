// 插件选择入口；新增实现时同步接入 StorePluginPanel 和服务端插件接口。
export const pluginChoices = {
  'cache-flush': [
    { value: 'caffeine', label: 'Caffeine 本地缓存', description: '内置缓存与本地队列，无需安装插件' },
    { value: 'redis', label: 'Redis 缓存与刷数', description: '通过 Redis 插件提供实时缓存与指标选主', pluginType: 'redis' }
  ],
  'monitor-collect': [
    { value: 'none', label: '无', description: '默认不启用采集监控存储' },
    { value: 'influxdb', label: 'Influxdb', description: '存储采集历史与运行指标，需安装插件', pluginType: 'influx' }
  ]
}
