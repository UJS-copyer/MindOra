<script setup lang="ts">
import { ref } from 'vue';
import { useWallpaper } from '../composables/useWallpaper';
import type { WallpaperMode } from '@mindora/types';

defineProps<{
  onNavigateHome: () => void;
  onNavigateBlog: () => void;
  onOpenSearch: () => void;
}>();

const { wallpaper, setMode } = useWallpaper();
const isDark = ref(document.documentElement.classList.contains('dark'));

function toggleDarkMode() {
  isDark.value = !isDark.value;
  if (isDark.value) {
    document.documentElement.classList.add('dark');
    localStorage.setItem('mindora-theme', 'dark');
  } else {
    document.documentElement.classList.remove('dark');
    localStorage.setItem('mindora-theme', 'light');
  }
}

function handleWallpaperChange(mode: WallpaperMode) {
  setMode(mode);
}
</script>

<template>
  <header class="sticky top-0 z-50 glass-header">
    <div class="max-w-7xl mx-auto px-6 h-14 flex items-center justify-between">
      <!-- 品牌标识与主页跳转 -->
      <div
        class="flex items-center gap-3 cursor-pointer group"
        role="button"
        tabindex="0"
        @click="onNavigateHome"
        @keydown.enter="onNavigateHome"
      >
        <div
          class="w-8 h-8 rounded-lg bg-emerald-600 dark:bg-emerald-500 flex items-center justify-center text-white font-bold text-sm tracking-tight shadow-md shadow-emerald-500/20 group-hover:scale-105 transition-transform"
        >
          M
        </div>
        <div class="flex items-baseline gap-2">
          <span class="font-semibold text-base text-zinc-900 dark:text-zinc-50 tracking-tight">MindOra</span>
          <span class="text-xs text-zinc-400 dark:text-zinc-500 font-normal hidden sm:inline">知识工坊 · 深度阅读</span>
        </div>
      </div>

      <!-- 中间与右侧控制栏 -->
      <div class="flex items-center gap-3">
        <!-- 背景环境切换微控制器 -->
        <div class="hidden sm:flex items-center gap-1 p-1 rounded-lg glass-card text-xs text-zinc-500">
          <span class="text-[11px] px-1.5 text-zinc-400">背景:</span>
          <button
            type="button"
            class="px-2 py-0.5 rounded transition-all cursor-pointer"
            :class="wallpaper.mode === 'aurora' ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 font-medium shadow-xs' : 'hover:text-zinc-800 dark:hover:text-zinc-200'"
            @click="handleWallpaperChange('aurora')"
          >
            极光流光
          </button>
          <button
            type="button"
            class="px-2 py-0.5 rounded transition-all cursor-pointer"
            :class="wallpaper.mode === 'mountain' ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 font-medium shadow-xs' : 'hover:text-zinc-800 dark:hover:text-zinc-200'"
            @click="handleWallpaperChange('mountain')"
          >
            自然雪峰
          </button>
          <button
            type="button"
            class="px-2 py-0.5 rounded transition-all cursor-pointer"
            :class="wallpaper.mode === 'minimal' ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 font-medium shadow-xs' : 'hover:text-zinc-800 dark:hover:text-zinc-200'"
            @click="handleWallpaperChange('minimal')"
          >
            素雅微网格
          </button>
        </div>

        <!-- 全局语义混合搜索按钮 -->
        <button
          type="button"
          class="h-9 px-3.5 rounded-lg glass-card text-xs text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100 flex items-center gap-3 transition-colors cursor-pointer"
          @click="onOpenSearch"
        >
          <svg class="w-4 h-4 text-zinc-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <span class="hidden md:inline">全站文章与知识库检索...</span>
          <kbd class="text-[11px] px-1.5 py-0.5 rounded bg-zinc-200/70 dark:bg-zinc-800 text-zinc-500 font-mono">⌘K</kbd>
        </button>

        <!-- 深浅主题切换 -->
        <button
          type="button"
          class="w-9 h-9 rounded-lg glass-card flex items-center justify-center text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100 transition-colors cursor-pointer"
          :title="isDark ? '切换到浅色模式' : '切换到暗色模式'"
          @click="toggleDarkMode"
        >
          <svg v-if="!isDark" class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
          </svg>
          <svg v-else class="w-4 h-4 text-amber-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="5" />
            <line x1="12" y1="1" x2="12" y2="3" />
            <line x1="12" y1="21" x2="12" y2="23" />
            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
            <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
            <line x1="1" y1="12" x2="3" y2="12" />
            <line x1="21" y1="12" x2="23" y2="12" />
            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
            <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
          </svg>
        </button>
      </div>
    </div>
  </header>
</template>
