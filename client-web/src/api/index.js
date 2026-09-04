import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({ baseURL: '/api', timeout: 90000 })

api.interceptors.request.use(config => {
  const token = sessionStorage.getItem('token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  if (config.data instanceof FormData) {
    delete config.headers['Content-Type']
  }
  return config
})

api.interceptors.response.use(
  res => {
    const data = res.data
    if (data.code !== 0) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data.data
  },
  err => {
    const status = err.response?.status
    const msg = err.response?.data?.message
      || (status === 401 || status === 403 ? '登录已失效，请重新登录' : null)
      || (err.code === 'ECONNABORTED' ? '请求超时，请确认后端已启动' : null)
      || (err.message === 'Network Error' ? '连不上后端（请确认 MySQL、Redis、后端 8080 和本页开发服务都在运行）' : err.message)
      || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default api
