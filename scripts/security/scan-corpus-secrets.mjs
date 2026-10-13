#!/usr/bin/env node
/**
 * 语料凭据扫描（可重复执行）。
 *
 * 用途：
 *   1) 上线前摸清「知识库里到底有哪些疑似凭据」——报告只给位置与规则命中数，**绝不回显命中内容本身**
 *      （否则报告自己就是一份密钥清单）；
 *   2) 脱敏 + 全量重建之后复扫，验收应为 0 命中。
 *
 * 规则与后端兜底 **同源**：以 `backend/mindora-rag/src/main/java/com/mindora/rag/application/SecretGuard.java`
 * 为准，本文件是它的 JS 镜像。**改动任一侧都必须同步另一侧**（规则表刻意保持简短，便于人工对齐）。
 *
 * 用法：
 *   node scripts/security/scan-corpus-secrets.mjs                 # 扫描全部文档
 *   node scripts/security/scan-corpus-secrets.mjs --visibility=public   # 只看公开可见文档
 *   node scripts/security/scan-corpus-secrets.mjs --baseline      # 同时把本次结果存为基线
 *
 * 前置：后端已启动；`logs/admin-token.txt` 内有有效管理员令牌（见 logs/mint-admin-token.mjs）。
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs'

const BASE = process.env.MINDORA_BASE ?? 'http://localhost:8080'
const TOKEN_FILE = 'logs/admin-token.txt'
const REPORT_FILE = 'logs/corpus-secret-scan.json'
const BASELINE_FILE = 'logs/corpus-secret-scan-baseline.json'
const CONCURRENCY = 4

const args = process.argv.slice(2)
const baseline = args.includes('--baseline')
const visibilityArg = args.find((arg) => arg.startsWith('--visibility='))
const visibility = visibilityArg ? visibilityArg.split('=')[1] : 'all'

/** 与 SecretGuard.java 对齐的规则表。prefixOnly=true 的规则也用于「检索后上下文」兜底。 */
const RULES = [
  { id: 'openai_style_key', prefixOnly: true, pattern: /\bsk-[A-Za-z0-9_-]{32,}\b/g },
  { id: 'aws_access_key', prefixOnly: true, pattern: /\b(?:AKIA|ASIA|ABIA|ACCA)[A-Z0-9]{16}\b/g },
  { id: 'alibaba_access_key', prefixOnly: true, pattern: /\bLTAI[A-Za-z0-9]{16,24}\b/g },
  { id: 'github_token', prefixOnly: true, pattern: /\bgh[pousr]_[A-Za-z0-9]{36,}\b/g },
  { id: 'google_api_key', prefixOnly: true, pattern: /\bAIza[0-9A-Za-z_-]{35}\b/g },
  { id: 'slack_token', prefixOnly: true, pattern: /\bxox[baprs]-[0-9A-Za-z-]{10,}\b/g },
  { id: 'private_key_block', prefixOnly: true, pattern: /-----BEGIN [A-Z0-9 ]*PRIVATE KEY(?: BLOCK)?-----/g },
  {
    id: 'credential_assignment',
    prefixOnly: false,
    pattern: /\b(?:api[_-]?key|token|secret|password|passwd|pwd)\b["']?\s*[:=]\s*["']?[A-Za-z0-9_+=./~-]{20,64}/gi,
  },
]

/** 占位符白名单（与 SecretGuard 的 PLACEHOLDER_VALUE 对齐）。 */
const PLACEHOLDER = new RegExp(
  '^(?:\\$\\{[^}]*\\}|\\{\\{[^}]*\\}\\}|<[^>]+>|x{5,}|\\*{5,}|changeme\\w*|your[-_a-z0-9]*|' +
    'placeholder|example\\w*|redacted|todo|none|null|xxx+)$',
  'i',
)

function isPlaceholder(match) {
  const value = match
    .replace(/^.*?[:=]\s*/, '')
    .trim()
    .replace(/^["']/, '')
    .replace(/["']$/, '')
  return PLACEHOLDER.test(value)
}

function token() {
  if (!existsSync(TOKEN_FILE)) {
    console.error(`缺少 ${TOKEN_FILE}：先用 logs/mint-admin-token.mjs 生成管理员令牌`)
    process.exit(2)
  }
  return readFileSync(TOKEN_FILE, 'utf8').trim()
}

const authToken = token()

async function api(path) {
  const response = await fetch(BASE + path, { headers: { Authorization: authToken } })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`${response.status} ${path} -> ${text.slice(0, 200)}`)
  }
  return JSON.parse(text).data
}

/** 逐文档并发取切片并扫描；命中只记位置，不记内容。 */
async function scanDocument(document, ruleCounts, hits) {
  const chunks = await api(`/api/v1/admin/knowledge/documents/${document.id}/chunks`)
  let characters = 0
  for (const chunk of chunks) {
    const content = chunk.content ?? ''
    const heading = chunk.headingPath ?? ''
    characters += content.length
    for (const rule of RULES) {
      rule.pattern.lastIndex = 0
      let match
      while ((match = rule.pattern.exec(content)) !== null) {
        if (!rule.prefixOnly && isPlaceholder(match[0])) {
          continue
        }
        ruleCounts[rule.id] = (ruleCounts[rule.id] ?? 0) + 1
        hits.push({
          ruleId: rule.id,
          documentTitle: document.title,
          sourcePath: document.sourcePath,
          visibility: document.visibility,
          sequence: chunk.sequence,
          // 只记录「在本切片第几段字命中」，便于人工定位；不记录命中文本
          headingPath: heading || null,
        })
        break // 同一规则在同一切片内只记一次，避免噪声
      }
    }
  }
  return { chunks: chunks.length, characters }
}

async function main() {
  const documents = (await api('/api/v1/admin/knowledge/documents')).filter(
    (document) => visibility === 'all' || document.visibility === visibility,
  )
  console.log(`扫描 ${documents.length} 篇文档（visibility=${visibility}），规则 ${RULES.length} 条`)

  const ruleCounts = {}
  const hits = []
  let chunkTotal = 0
  let characterTotal = 0

  for (let index = 0; index < documents.length; index += CONCURRENCY) {
    const batch = documents.slice(index, index + CONCURRENCY)
    const results = await Promise.all(batch.map((document) => scanDocument(document, ruleCounts, hits)))
    for (const result of results) {
      chunkTotal += result.chunks
      characterTotal += result.characters
    }
    process.stdout.write(`\r已扫描 ${Math.min(index + CONCURRENCY, documents.length)}/${documents.length} 篇`)
  }
  console.log('')

  const report = {
    scannedAt: new Date().toISOString(),
    baseUrl: BASE,
    visibility,
    documents: documents.length,
    chunks: chunkTotal,
    characters: characterTotal,
    ruleCounts,
    hits,
  }
  writeFileSync(REPORT_FILE, JSON.stringify(report, null, 2), 'utf8')
  if (baseline) {
    writeFileSync(BASELINE_FILE, JSON.stringify(report, null, 2), 'utf8')
  }

  const totalHits = Object.values(ruleCounts).reduce((sum, value) => sum + value, 0)
  console.log(`命中合计 ${totalHits} 处：`)
  for (const rule of RULES) {
    const count = ruleCounts[rule.id] ?? 0
    const mark = count === 0 ? '✓' : '✗'
    console.log(`  ${mark} ${rule.id}: ${count}`)
  }
  const documentsWithHits = [...new Set(hits.map((hit) => hit.documentTitle))]
  if (documentsWithHits.length > 0) {
    console.log(`涉及文档（${documentsWithHits.length} 篇）：${documentsWithHits.slice(0, 15).join('、')}${
      documentsWithHits.length > 15 ? ' …' : ''
    }`)
  }
  console.log(`明细（不含命中文本）：${REPORT_FILE}${baseline ? `，基线：${BASELINE_FILE}` : ''}`)
  process.exit(totalHits === 0 ? 0 : 1)
}

main().catch((error) => {
  console.error(`扫描失败：${error.message}`)
  console.error('（后端是否已启动？令牌是否有效？）')
  process.exit(2)
})
