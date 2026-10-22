/** 存储 / 监控后端选项（编译期 store 模块，非插件） */
export const backendChoices = {
  'cache-flush': [
    { value: 'caffeine', label: 'Caffeine（进程内）', description: '边缘盒子默认；无需外部缓存' },
    { value: 'redis', label: 'Redis', description: '中控电脑；实时缓存' }
  ],
  'monitor-collect': [
    { value: 'none', label: '无', description: '不写时序历史（边缘盒子）' },
    { value: 'influxdb', label: 'InfluxDB', description: '中控电脑；快照历史与协议指标' }
  ]
}
