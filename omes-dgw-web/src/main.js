import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import App from './App.vue'
import router, { setupDynamicRoutes } from './router'
import { useUserStore } from './stores/user'
import './style.css'

// 配置dayjs中文
dayjs.locale('zh-cn')

const app = createApp(App)
const pinia = createPinia()

app.config.globalProperties.$echarts = echarts

app.use(pinia)
setupDynamicRoutes(useUserStore().menus)
app.use(router)
app.use(Antd)
app.mount('#app')
