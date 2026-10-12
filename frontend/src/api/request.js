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

// 统一处理业务错误码（非 0），返回 Promise.reject 供调用方 catch
function handleBusinessError(res) {
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
}

// 响应拦截器：统一处理后端 BaseResponse 格式
request.interceptors.response.use(
  async (response) => {
    const res = response.data

    // 文件流（blob）响应：后端直接返回二进制（如代码下载 zip），
    // 无 BaseResponse 包装，直接透传给调用方
    if (res instanceof Blob) {
      const contentType = response.headers['content-type'] || ''
      // 后端异常时（如未登录/未部署），局部异常处理器返回 application/json 的 Result，
      // responseType 为 blob 时也会被包装成 Blob，需解析后走错误处理，避免把 JSON 当文件下载
      if (contentType.includes('application/json')) {
        const text = await res.text()
        let errRes
        try {
          errRes = JSON.parse(text)
        } catch (e) {
          errRes = { code: 500, message: '下载失败' }
        }
        return handleBusinessError(errRes)
      }
      return res
    }

    // 业务成功（code === 0）
    if (res.code === 0) {
      return res
    }

    return handleBusinessError(res)
  },
  (error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
