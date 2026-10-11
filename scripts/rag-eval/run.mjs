#!/usr/bin/env node
/**
 * MindOra 检索评测脚本（Stage 3.3 / F3）
 *
 * 做两件事：
 *   1. 指标模式（默认）：读取黄金测试集，调用检索接口，计算 DocHit@K / SpanHit@K / MRR，
 *      并给出「正例最小重排分 vs 负例最大重排分」的阈值分离分析（用于确定熔断拒答阈值）。
 *   2. 索引体检模式（--duplicates）：扫描切片，统计近重复切片与长度分布，
 *      用于客观衡量「切片推进量塌缩」这类缺陷是否已消失。
 *
 * 用法：
 *   node scripts/rag-eval/run.mjs --token <jwt> [--golden <path>] [--top-k 8]
 *   node scripts/rag-eval/run.mjs --email <email> --password <pwd>
 *   node scripts/rag-eval/run.mjs --duplicates --token <jwt>
 *   node scripts/rag-eval/run.mjs --token-file logs/admin-token.txt
 *
 * 说明：管理端接口需要 SUPER_ADMIN 令牌，脚本不保存任何凭据。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '../..')

function parseArgs(argv) {
  const args = { 'top-k': 8, scope: 'all', golden: resolve(here, 'golden.example.json'), base: 'http://localhost:8080' }
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i]
    if (!token.startsWith('--')) continue
    const key = token.slice(2)
    if (key === 'duplicates' || key === 'help') {
      args[key] = true
      continue
    }
    args[key] = argv[++i]
  }
  return args
}

const args = parseArgs(process.argv.slice(2))
if (args.help) {
  console.log(readFileSync(resolve(here, 'README.md'), 'utf8'))
  process.exit(0)
}

const base = String(args.base).replace(/\/$/, '')
const topK = Number(args['top-k']) || 8
const scope = args.scope

async function request(path, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8' }
  if (token) headers.Authorization = token
  const response = await fetch(`${base}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : Buffer.from(JSON.stringify(body), 'utf8')
  })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`${method} ${path} -> ${response.status} ${text.slice(0, 200)}`)
  }
  const payload = text ? JSON.parse(text) : {}
  return payload.data !== undefined ? payload.data : payload
}

async function resolveToken() {
  if (args.token) return args.token.startsWith('Bearer ') ? args.token : `Bearer ${args.token}`
  if (args['token-file']) {
    const raw = readFileSync(resolve(root, args['token-file']), 'utf8').trim()
    return raw.startsWith('Bearer ') ? raw : `Bearer ${raw}`
  }
  if (process.env.MINDORA_ADMIN_TOKEN) {
    const raw = process.env.MINDORA_ADMIN_TOKEN.trim()
    return raw.startsWith('Bearer ') ? raw : `Bearer ${raw}`
  }
  if (args.email && args.password) {
    const view = await request('/api/v1/auth/login', {
      method: 'POST',
      body: { email: args.email, password: args.password }
    })
    const accessToken = view.accessToken ?? view.token
    if (!accessToken) throw new Error('登录成功但未返回 accessToken')
    return accessToken.startsWith('Bearer ') ? accessToken : `Bearer ${accessToken}`
  }
  throw new Error('缺少凭据：请用 --token / --token-file / MINDORA_ADMIN_TOKEN，或 --email 与 --password')
}

// ---------------------------------------------------------------------------
// 索引体检：长度分布 + 近重复切片
// ---------------------------------------------------------------------------

/** 近重复：长度接近，且一方是另一方的后缀/子串（推进量塌缩的典型产物是逐字符位移的后缀）。 */
function nearDuplicate(left, right) {
  const minLength = Math.min(left.length, right.length)
  if (minLength < 20) return false
  if (Math.abs(left.length - right.length) > 5) return false
  return left.endsWith(right) || right.endsWith(left) || left.includes(right) || right.includes(left)
}

async function runDuplicates(token) {
  const documents = await request('/api/v1/admin/knowledge/documents', { token })
  const lengths = []
  const offenders = []
  let nearDuplicatePairs = 0

  for (const document of documents) {
    const chunks = await request(`/api/v1/admin/knowledge/documents/${document.id}/chunks`, { token })
    const ordered = [...chunks].sort((left, right) => left.sequence - right.sequence)
    let pairs = 0
    for (let i = 0; i < ordered.length; i++) {
      lengths.push(ordered[i].characterCount)
      if (i === 0) continue
      const previous = ordered[i - 1]
      const current = ordered[i]
      if (previous.failureReason === 'archived' || current.failureReason === 'archived') continue
      if (nearDuplicate(previous.content ?? '', current.content ?? '')) pairs++
    }
    nearDuplicatePairs += pairs
    if (pairs > 0) offenders.push({ title: document.title, chunks: ordered.length, pairs })
  }

  const total = lengths.length
  const bucket = (low, high) => lengths.filter((value) => value >= low && value < high).length
  const percentile = (ratio) => {
    if (total === 0) return 0
    const sorted = [...lengths].sort((a, b) => a - b)
    return sorted[Math.min(total - 1, Math.floor(total * ratio))]
  }

  console.log('=== 索引体检 ===')
  console.log(`文档数：${documents.length}    切片数：${total}`)
  console.log(`长度分位：p10=${percentile(0.1)}  p50=${percentile(0.5)}  p90=${percentile(0.9)}  max=${Math.max(0, ...lengths)}`)
  console.log(
    `长度分布：<50 ${bucket(0, 50)}（${pct(bucket(0, 50), total)}）  ` +
      `50-149 ${bucket(50, 150)}（${pct(bucket(50, 150), total)}）  ` +
      `150-599 ${bucket(150, 600)}（${pct(bucket(150, 600), total)}）  ` +
      `>=600 ${bucket(600, Number.MAX_SAFE_INTEGER)}（${pct(bucket(600, Number.MAX_SAFE_INTEGER), total)}）`
  )
  console.log(`近重复相邻切片对：${nearDuplicatePairs}`)
  if (offenders.length === 0) {
    console.log('  ✅ 未发现近重复切片（推进量塌缩缺陷的典型产物）')
  } else {
    console.log('  ⚠️ 受影响文档：')
    offenders
      .sort((left, right) => right.pairs - left.pairs)
      .slice(0, 10)
      .forEach((item) => console.log(`     ${item.title}：${item.pairs} 对 / ${item.chunks} 切片`))
  }
  return { documents: documents.length, chunks: total, nearDuplicatePairs, offenders }
}

// ---------------------------------------------------------------------------
// 指标模式
// ---------------------------------------------------------------------------

/** 检索接口返回带连字符的 UUID，切片接口返回无连字符的 32 位十六进制，比对前需归一化。 */
const normalizeId = (value) => String(value ?? '').replace(/-/g, '').toLowerCase()

function pct(value, total) {
  return total === 0 ? '0%' : `${((value / total) * 100).toFixed(1)}%`
}

async function runMetrics(token) {
  const golden = JSON.parse(readFileSync(String(args.golden), 'utf8'))
  const cases = golden.cases ?? golden
  const chunkCache = new Map()

  async function chunksOf(documentId) {
    if (!chunkCache.has(documentId)) {
      const chunks = await request(`/api/v1/admin/knowledge/documents/${documentId}/chunks`, { token })
      chunkCache.set(documentId, new Map(chunks.map((chunk) => [normalizeId(chunk.id), chunk])))
    }
    return chunkCache.get(documentId)
  }

  const rows = []
  for (const item of cases) {
    const outcome = await request('/api/v1/admin/knowledge/retrieval/search', {
      method: 'POST',
      token,
      body: {
        query: item.question,
        scope: item.scope ?? scope,
        rerankTopK: topK,
        rerankEnabled: true,
        keywordEnabled: item.keywordEnabled ?? true
      }
    })
    const results = (outcome.results ?? []).slice(0, topK)
    const expectedDocs = item.expectedDocs ?? []
    const keySpans = item.keySpans ?? []

    let firstHitRank = 0
    let docHit = false
    let spanHit = false
    let matchedRerank = null

    for (let index = 0; index < results.length; index++) {
      const hit = results[index]
      const byDoc = expectedDocs.includes(hit.documentTitle) || expectedDocs.includes(hit.documentId)
      let bySpan = false
      if (keySpans.length > 0) {
        const chunk = (await chunksOf(hit.documentId)).get(normalizeId(hit.chunkId))
        // 判定范围 = 切片正文 + headingPath（关键词索引与重排输入都含 headingPath）
        const content = `${chunk?.content ?? hit.contentPreview ?? ''}\n${chunk?.headingPath ?? ''}`
        bySpan = keySpans.some((span) => content.includes(span))
      }
      if (byDoc) docHit = true
      if (bySpan) spanHit = true
      if ((byDoc || bySpan) && firstHitRank === 0) {
        firstHitRank = index + 1
        matchedRerank = hit.rerankScore ?? null
      }
    }

    const maxRerank = results.reduce((best, hit) => Math.max(best, hit.rerankScore ?? 0), 0)
    rows.push({
      id: item.id ?? item.question.slice(0, 12),
      type: item.type ?? '-',
      noAnswer: item.noAnswer === true,
      docHit,
      spanHit,
      mrr: firstHitRank === 0 ? 0 : 1 / firstHitRank,
      firstHitRank,
      matchedRerank,
      maxRerank,
      resultCount: results.length,
      latencyMs: outcome.latencyMs ?? null
    })
  }

  const positives = rows.filter((row) => !row.noAnswer)
  const negatives = rows.filter((row) => row.noAnswer)
  const mean = (values) => (values.length === 0 ? 0 : values.reduce((sum, value) => sum + value, 0) / values.length)

  console.log(`=== 检索评测（topK=${topK}, scope=${scope}, 用例 ${rows.length}）===`)
  console.log('用例结果：')
  for (const row of rows) {
    const flag = row.noAnswer ? '负例' : row.spanHit ? '✅' : row.docHit ? '⚠️ 仅文档命中' : '❌'
    const rank = row.firstHitRank === 0 ? '-' : `#${row.firstHitRank}`
    console.log(
      `  ${String(row.id).padEnd(6)} ${flag.padEnd(12)} ${String(row.type).padEnd(14)} ` +
        `首命中=${rank.padEnd(4)} 最大重排分=${row.maxRerank.toFixed(4)} 耗时=${row.latencyMs ?? '-'}ms  ${row.noAnswer ? '' : ''}`
    )
  }

  const docHitRate = mean(positives.map((row) => (row.docHit ? 1 : 0)))
  const spanHitRate = mean(positives.map((row) => (row.spanHit ? 1 : 0)))
  const mrr = mean(positives.map((row) => row.mrr))

  console.log('')
  console.log(`正例 ${positives.length} 条：DocHit@${topK} = ${(docHitRate * 100).toFixed(1)}%   SpanHit@${topK} = ${(spanHitRate * 100).toFixed(1)}%   MRR = ${mrr.toFixed(3)}`)

  const positiveMin = positives.filter((row) => row.matchedRerank !== null).map((row) => row.matchedRerank)
  const negativeMax = negatives.map((row) => row.maxRerank)
  if (positiveMin.length > 0) {
    console.log(`命中切片的最低重排分：${Math.min(...positiveMin).toFixed(4)}`)
  }
  if (negativeMax.length > 0) {
    console.log(`负例（知识库无答案）的最高重排分：${Math.max(...negativeMax).toFixed(4)}`)
    if (positiveMin.length > 0) {
      const gap = Math.min(...positiveMin) - Math.max(...negativeMax)
      console.log(
        gap > 0
          ? `阈值分离：✅ 可用熔断阈值约 ${(((Math.min(...positiveMin) + Math.max(...negativeMax)) / 2)).toFixed(3)}（间隔 ${gap.toFixed(4)}）`
          : `阈值分离：❌ 存在重叠（间隔 ${gap.toFixed(4)}），需要更多负例或调整检索参数`
      )
    }
  }

  if (args.json) {
    writeFileSync(resolve(root, args.json), JSON.stringify({ rows, summary: { docHitRate, spanHitRate, mrr } }, null, 2), 'utf8')
    console.log(`明细已写入 ${args.json}`)
  }
  return { docHitRate, spanHitRate, mrr }
}

const token = await resolveToken()
if (args.duplicates) {
  await runDuplicates(token)
} else {
  await runMetrics(token)
}
