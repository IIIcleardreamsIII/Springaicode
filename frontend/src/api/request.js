import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * Axios 实例
 * 统一处理请求前缀、响应拦截、错误提示
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 120000, // 全局超时 2 分钟（AI 生成代码耗时较长）
  withCredentials: true, // 携带 Cookie（Session）
})

// 响应拦截器：统一处理后端 BaseResponse 格式
request.interceptors.response.use(
  (response) => {
    const res = response.data

    // 业务成功（code === 0）
    if (res.code === 0) {
      return res
    }

    // 未登录
    if (res.code === 40100) {
      ElMessage.error('未登录，请先登录')
      router.push('/login')
      return Promise.reject(res)
    }

    // 无权限
    if (res.code === 40101) {
      ElMessage.error('无权限访问')
      return Promise.reject(res)
    }

    // 其他业务错误
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(res)
  },
  (error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
