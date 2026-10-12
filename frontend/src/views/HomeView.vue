<template>
  <div class="page-container">
    <!-- Hero 区域：输入提示词创建应用 -->
    <div class="hero-section gh-card">
      <h1 class="hero-title">AI 零代码生成平台</h1>
      <p class="hero-desc">输入你的想法，AI 帮你生成完整的网站应用</p>

      <div class="prompt-box">
        <el-input
          v-model="initPrompt"
          type="textarea"
          :rows="4"
          placeholder="描述你想要的应用，例如：一个简洁的待办事项应用，支持添加、删除、标记完成和分类筛选"
          resize="none"
          maxlength="500"
          show-word-limit
          @keydown.ctrl.enter="handleCreate"
        />
        <div class="prompt-actions">
          <div class="prompt-type">
            <span class="prompt-tip">生成类型：</span>
            <el-radio-group v-model="codeGenType" size="small">
              <el-radio-button value="html">HTML 单文件</el-radio-button>
              <el-radio-button value="multi_file">多文件</el-radio-button>
              <el-radio-button value="project">工程化项目</el-radio-button>
            </el-radio-group>
            <el-button
              size="small"
              type="primary"
              plain
              :loading="suggesting"
              :disabled="!initPrompt.trim()"
              @click="handleSuggestType"
            >
              <el-icon v-if="!suggesting"><Cpu /></el-icon>
              {{ suggesting ? '分析中...' : 'AI 智能选择' }}
            </el-button>
          </div>
          <el-button
            type="primary"
            size="large"
            :loading="creating"
            :disabled="!initPrompt.trim()"
            @click="handleCreate"
          >
            <el-icon v-if="!creating"><MagicStick /></el-icon>
            {{ creating ? '创建中...' : '创建应用' }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 我的应用（前 8 个） -->
    <div class="section-block">
      <div class="section-header">
        <h2 class="section-title">我的应用</h2>
        <router-link to="/my-apps" class="section-more">查看全部 →</router-link>
      </div>
      <div v-if="myLoading" class="section-empty">加载中...</div>
      <div v-else-if="myApps.length === 0" class="section-empty">
        还没有应用，在上方输入提示词创建第一个应用吧
      </div>
      <div v-else class="app-grid">
        <div v-for="app in myApps" :key="app.id" class="app-card gh-card" @click="goChat(app)">
          <AppPreviewThumb :app="app" />
          <div class="app-card-body">
            <div class="app-card-name">{{ app.appName }}</div>
            <div class="app-card-meta">
              <span :class="['type-badge', app.codeGenType]">
                {{ typeLabel(app.codeGenType) }}
              </span>
              <el-tag v-if="app.deployKey" type="success" size="small" effect="plain">已部署</el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 精选应用 -->
    <div class="section-block">
      <div class="section-header">
        <h2 class="section-title">精选应用</h2>
      </div>
      <div v-if="goodLoading" class="section-empty">加载中...</div>
      <div v-else-if="goodApps.length === 0" class="section-empty">暂无精选应用</div>
      <div v-else class="app-grid">
        <div v-for="app in goodApps" :key="app.id" class="app-card gh-card" @click="showAppDetail(app)">
          <AppPreviewThumb :app="app" />
          <div class="app-card-body">
            <div class="app-card-name">{{ app.appName }}</div>
            <div class="app-card-author">
              <el-avatar :size="18" :src="app.user?.userAvatar">
                {{ app.user?.userName?.charAt(0) || 'U' }}
              </el-avatar>
              <span>{{ app.user?.userName || '匿名用户' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 应用详情对话框（精选应用查看） -->
    <el-dialog v-model="detailVisible" :title="currentApp?.appName" width="560px">
      <div class="detail-body">
        <div class="detail-row">
          <span class="detail-label">创建者</span>
          <span>{{ currentApp?.user?.userName || '匿名用户' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">生成类型</span>
          <span>{{ typeLabel(currentApp?.codeGenType) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">创建时间</span>
          <span>{{ formatDate(currentApp?.createTime) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">下载次数</span>
          <span class="detail-download">
            <el-icon><Download /></el-icon>
            {{ currentApp?.downloadCount ?? 0 }} 次
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">初始提示词</span>
          <p class="detail-prompt">{{ currentApp?.initPrompt }}</p>
        </div>
      </div>
      <template #footer>
        <el-button v-if="currentApp?.deployKey" type="primary" @click="visitDeployed(currentApp)">
          访问部署站点
        </el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, Download, Cpu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { addApp, listMyAppVOByPage, listGoodAppVOByPage, suggestCodeGenType } from '../api/app'
import AppPreviewThumb from '../components/AppPreviewThumb.vue'

const router = useRouter()

const initPrompt = ref('')
const creating = ref(false)
const suggesting = ref(false)
// 生成类型：html / multi_file / project，默认 html
const codeGenType = ref('html')

const myApps = ref([])
const myLoading = ref(false)
const goodApps = ref([])
const goodLoading = ref(false)

const detailVisible = ref(false)
const currentApp = ref(null)

// 生成类型文案
function typeLabel(type) {
  if (type === 'html') return 'HTML 单文件'
  if (type === 'multi_file') return '多文件'
  if (type === 'project') return '工程化项目'
  return '未知'
}

// AI 智能选择生成类型：根据应用描述让 AI 自动判断 html / multi_file / project，并回填到表单
async function handleSuggestType() {
  if (!initPrompt.value.trim()) {
    ElMessage.warning('请先输入应用描述，再让 AI 智能选择')
    return
  }
  suggesting.value = true
  try {
    const res = await suggestCodeGenType({ prompt: initPrompt.value.trim() })
    if (res.code === 0 && res.data) {
      codeGenType.value = res.data
      ElMessage.success(`AI 已为您选择：${typeLabel(res.data)}`)
    } else {
      ElMessage.warning(res.message || 'AI 选择失败，请重试')
    }
  } catch (err) {
    console.error('AI 智能选择失败:', err)
    ElMessage.error('AI 智能选择失败，请稍后重试')
  } finally {
    suggesting.value = false
  }
}

// 创建应用：入库后跳转对话页，由对话页根据"自己的 app + 无历史"自动开始首次生成
async function handleCreate() {
  if (!initPrompt.value.trim()) {
    ElMessage.warning('请输入应用描述')
    return
  }
  creating.value = true
  try {
    const res = await addApp({ initPrompt: initPrompt.value.trim(), codeGenType: codeGenType.value })
    if (res.code === 0 && res.data) {
      ElMessage.success('应用创建成功')
      router.push(`/app/chat/${res.data}`)
    }
  } catch (err) {
    console.error('创建应用失败:', err)
  } finally {
    creating.value = false
  }
}

function goChat(app) {
  router.push(`/app/chat/${app.id}`)
}

function showAppDetail(app) {
  currentApp.value = app
  detailVisible.value = true
}

// 访问部署站点：deployKey 经 vite 代理走后端静态资源控制器
function visitDeployed(app) {
  window.open(`/api/${app.deployKey}`, '_blank')
}

async function loadMyApps() {
  myLoading.value = true
  try {
    const res = await listMyAppVOByPage({ pageNum: 1, pageSize: 8 })
    if (res.code === 0) {
      myApps.value = res.data?.records || []
    }
  } catch (err) {
    console.error('加载我的应用失败:', err)
  } finally {
    myLoading.value = false
  }
}

async function loadGoodApps() {
  goodLoading.value = true
  try {
    const res = await listGoodAppVOByPage({ pageNum: 1, pageSize: 20 })
    if (res.code === 0) {
      goodApps.value = res.data?.records || []
    }
  } catch (err) {
    console.error('加载精选应用失败:', err)
  } finally {
    goodLoading.value = false
  }
}

function formatDate(time) {
  if (!time) return '-'
  return String(time).replace('T', ' ').substring(0, 16)
}

onMounted(() => {
  loadMyApps()
  loadGoodApps()
})
</script>

<style scoped>
/* Hero 区域 */
.hero-section {
  padding: 48px 40px;
  text-align: center;
  margin-bottom: 32px;
}

.hero-title {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 8px;
}

.hero-desc {
  font-size: 15px;
  color: var(--gh-fg-muted);
  margin-bottom: 28px;
}

.prompt-box {
  max-width: 720px;
  margin: 0 auto;
  text-align: left;
}

.prompt-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.prompt-tip {
  font-size: 12px;
  color: var(--gh-fg-subtle);
}

.prompt-type {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 区块 */
.section-block {
  margin-bottom: 32px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
}

.section-more {
  font-size: 13px;
}

.section-empty {
  padding: 40px;
  text-align: center;
  color: var(--gh-fg-subtle);
  font-size: 14px;
  border: 1px dashed var(--gh-border);
  border-radius: var(--gh-radius);
}

/* 应用卡片网格 */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.app-card {
  cursor: pointer;
  overflow: hidden;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.app-card:hover {
  box-shadow: var(--gh-shadow-md);
  transform: translateY(-2px);
}

.app-card-body {
  padding: 12px;
}

.app-card-name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 8px;
}

.app-card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.app-card-author {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--gh-fg-muted);
}

/* 类型徽章 */
.type-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

.type-badge.html {
  background-color: #ddf4ff;
  color: #0969da;
}

.type-badge.multi_file {
  background-color: #dafbe1;
  color: #1a7f37;
}

.type-badge.project {
  background-color: #faf3dd;
  color: #9a6700;
}

/* 详情对话框 */
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-row {
  display: flex;
  gap: 12px;
  font-size: 14px;
}

.detail-label {
  flex-shrink: 0;
  width: 72px;
  color: var(--gh-fg-muted);
}

.detail-prompt {
  flex: 1;
  padding: 8px 12px;
  background-color: var(--gh-bg);
  border-radius: var(--gh-radius);
  line-height: 1.6;
  word-break: break-all;
}

.detail-download {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
