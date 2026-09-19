import type { ArticleTocItem } from '@mindora/types';
import { onBeforeUnmount, ref } from 'vue';

export function useArticleToc() {
  const toc = ref<ArticleTocItem[]>([]);
  const activeId = ref('');
  let scrollListener: (() => void) | null = null;

  function slugify(text: string): string {
    return (
      text
        .toLowerCase()
        .replace(/<[^>]+>/g, '')
        .trim()
        .replace(/[^\w\u4e00-\u9fa5-]+/g, '-')
        .replace(/^-+|-+$/g, '') || 'heading'
    );
  }

  function parseMarkdown(markdown: string) {
    const lines = markdown.replace(/\r\n/g, '\n').split('\n');
    const items: ArticleTocItem[] = [];
    const counts: Record<string, number> = {};

    let inCode = false;
    for (const line of lines) {
      if (/^\s*```/.test(line)) {
        inCode = !inCode;
        continue;
      }
      if (inCode) continue;

      const match = line.match(/^\s{0,3}(#{1,3})\s+(.+)$/);
      if (match) {
        const level = match[1].length;
        const title = match[2].replace(/[*_~`]/g, '').trim();
        const baseId = slugify(title);
        counts[baseId] = (counts[baseId] ?? 0) + 1;
        const id = counts[baseId] > 1 ? `${baseId}-${counts[baseId]}` : baseId;

        items.push({ id, title, level });
      }
    }

    toc.value = items;
    if (items.length > 0) {
      activeId.value = items[0].id;
    }
  }

  function initScrollSpy() {
    if (scrollListener) {
      window.removeEventListener('scroll', scrollListener);
    }

    scrollListener = () => {
      const headings = toc.value.map((item) => document.getElementById(item.id)).filter(Boolean) as HTMLElement[];
      if (!headings.length) return;

      const scrollPosition = window.scrollY + 100;
      let current = headings[0].id;

      for (const el of headings) {
        if (el.offsetTop <= scrollPosition) {
          current = el.id;
        } else {
          break;
        }
      }
      activeId.value = current;
    };

    window.addEventListener('scroll', scrollListener, { passive: true });
    // Trigger once
    scrollListener();
  }

  function scrollToHeading(id: string) {
    const target = document.getElementById(id);
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      activeId.value = id;
    }
  }

  onBeforeUnmount(() => {
    if (scrollListener) {
      window.removeEventListener('scroll', scrollListener);
      scrollListener = null;
    }
  });

  return {
    toc,
    activeId,
    parseMarkdown,
    initScrollSpy,
    scrollToHeading
  };
}
