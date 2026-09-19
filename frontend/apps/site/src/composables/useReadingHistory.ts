import type { ReadingHistoryItem } from '@mindora/types';
import { onMounted, ref } from 'vue';

const STORAGE_KEY = 'mindora-reading-history';
const MAX_HISTORY = 6;

const history = ref<ReadingHistoryItem[]>([]);

export function useReadingHistory() {
  function load() {
    try {
      const cached = localStorage.getItem(STORAGE_KEY);
      if (cached) {
        history.value = JSON.parse(cached) as ReadingHistoryItem[];
      }
    } catch {
      history.value = [];
    }
  }

  function save() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(history.value));
    } catch {
      // ignore
    }
  }

  function recordArticle(article: { slug: string; title: string; categoryName?: string }, progress = 0) {
    const existingIndex = history.value.findIndex((item) => item.slug === article.slug);
    const item: ReadingHistoryItem = {
      slug: article.slug,
      title: article.title,
      categoryName: article.categoryName,
      progress: existingIndex >= 0 ? Math.max(history.value[existingIndex].progress, progress) : progress,
      updatedAt: Date.now()
    };

    if (existingIndex >= 0) {
      history.value.splice(existingIndex, 1);
    }
    history.value.unshift(item);
    if (history.value.length > MAX_HISTORY) {
      history.value = history.value.slice(0, MAX_HISTORY);
    }
    save();
  }

  function updateProgress(slug: string, progress: number) {
    const target = history.value.find((item) => item.slug === slug);
    if (target) {
      target.progress = Math.min(100, Math.max(target.progress, Math.round(progress)));
      target.updatedAt = Date.now();
      save();
    }
  }

  onMounted(() => {
    load();
  });

  return {
    history,
    recordArticle,
    updateProgress
  };
}
