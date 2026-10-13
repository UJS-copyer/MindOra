/**
 * 标题锚点 id 的唯一实现。
 *
 * 同一篇 Markdown 的标题会在三处被转成 id：文章详情的渲染（`ArticleReader` 写 `id`）、
 * 阅读大纲（`useArticleToc` 生成目录项）、以及引用回链（`headingPath` → 目标章节）。
 * 三处必须用同一套规则，否则「点了引用跳不到那一节」。
 */

/** 标题文本 → DOM id：保留中日韩与 `\w`，其余折叠成 `-`；空结果兜底为 `heading`。 */
export function anchorIdFromText(text: string): string {
  return (
    text
      .toLowerCase()
      .replace(/<[^>]+>/g, '')
      .trim()
      .replace(/[^\w\u4e00-\u9fa5-]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'heading'
  );
}

/**
 * 知识库引用里的 `headingPath`（形如 `A > B > C`）→ 目标章节的锚点 id。
 * 取最后一段（最贴近切片的那一节）；没有可用层级时返回 null，由调用方退化为文章级回链。
 */
export function anchorIdFromHeadingPath(headingPath: string | null | undefined): string | null {
  if (!headingPath) return null;
  const segments = headingPath
    .split('>')
    .map((segment) => segment.trim())
    .filter((segment) => segment.length > 0);
  if (!segments.length) return null;
  return anchorIdFromText(segments[segments.length - 1]);
}
