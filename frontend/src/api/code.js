import request from './request'

/**
 * AI 代码生成相关 API
 */

// 同步生成代码（超时 2 分钟，AI 生成耗时较长）
export function generateCode(data) {
  return request.post('/code/generate', data, { timeout: 120000 })
}

/**
 * SSE 流式生成代码
 * 使用 EventSource 接收实时生成的代码片段
 *
 * 后端推送协议约定（与后端 SseEmitter 实现对应）：
 * - 普通消息（onmessage）：data 为 JSON 字符串 {"d": "代码片段"}，
 *   包装为 JSON 是为了完整保留片段首尾的空白字符（SSE 协议会吞掉纯文本 data 首尾的空白）
 * - 完成通知：名为 done 的命名事件（event: done），收到后应关闭连接
 * - 错误通知：名为 error 的命名事件（event: error），data 为错误信息文本
 *
 * @param {string} userMessage 用户提示词
 * @param {string} codeGenType 生成类型：html / multi_file
 * @param {function} onMessage 每收到一个片段的回调（参数为纯文本片段）
 * @param {function} onComplete 生成完成的回调
 * @param {function} onError 错误回调
 * @returns {EventSource} EventSource 实例（可用于手动关闭）
 */
export function generateCodeStream({ userMessage, codeGenType, appName, onMessage, onComplete, onError }) {
  const params = new URLSearchParams({ userMessage, codeGenType })
  if (appName) {
    params.set('appName', appName)
  }
  const url = `/api/code/generate/stream?${params.toString()}`

  const eventSource = new EventSource(url)
  let completed = false

  // 统一的"只触发一次"完成处理
  const finish = (fn, arg) => {
    if (!completed) {
      completed = true
      eventSource.close()
      fn?.(arg)
    }
  }

  // 普通消息：data 为 JSON {"d": "..."}，解析出片段（空白已完整保留）
  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      onMessage?.(data.d ?? '')
    } catch {
      // 非 JSON 数据（理论上不会出现），忽略
    }
  }

  // 完成通知：后端主动推送的 done 命名事件，data 为 JSON {"appId": 新应用id}
  eventSource.addEventListener('done', (event) => {
    let payload
    try {
      payload = event.data ? JSON.parse(event.data) : undefined
    } catch {
      payload = undefined
    }
    finish(onComplete, payload)
  })

  // 错误处理：onerror 对"命名 error 事件"和"连接层异常"都会触发，
  // 通过 event.data 区分——带 data 的是后端推送的业务错误
  eventSource.onerror = (event) => {
    if (event.data) {
      finish(onError, new Error(event.data))
      return
    }
    // readyState === CLOSED 表示服务端正常关闭了连接
    if (eventSource.readyState === EventSource.CLOSED) {
      finish(onComplete)
    } else {
      finish(onError, new Error('SSE 连接异常'))
    }
  }

  return eventSource
}

// ==================== 生成历史 ====================

// 获取生成历史列表
export function listGeneratedCode() {
  return request.get('/code/list')
}

// 获取指定生成记录的代码内容
export function getCodeContent(dir) {
  return request.get('/code/content', { params: { dir } })
}
