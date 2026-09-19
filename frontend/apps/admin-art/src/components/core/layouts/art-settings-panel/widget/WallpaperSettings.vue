<template>
  <SectionTitle title="环境背景与壁纸" />

  <div class="wallpaper-options grid grid-cols-3 gap-2 px-6 mb-4">
    <!-- 极光流光 -->
    <div
      class="wallpaper-item p-2 rounded-lg border text-center cursor-pointer transition-all"
      :class="
        wallpaperConfig.mode === 'aurora'
          ? 'border-emerald-500 bg-emerald-50/20 text-emerald-600 font-medium shadow-xs'
          : 'border-zinc-200 dark:border-zinc-700 text-zinc-600 dark:text-zinc-400 hover:border-zinc-400'
      "
      @click="settingStore.setWallpaperConfig({ mode: 'aurora' })"
    >
      <div class="h-10 rounded bg-gradient-to-tr from-emerald-500/20 via-blue-500/20 to-purple-500/20 mb-1.5 flex items-center justify-center text-[10px] font-mono">
        Aurora
      </div>
      <span class="text-xs">极光流光</span>
    </div>

    <!-- 自然雪峰 -->
    <div
      class="wallpaper-item p-2 rounded-lg border text-center cursor-pointer transition-all"
      :class="
        wallpaperConfig.mode === 'mountain'
          ? 'border-emerald-500 bg-emerald-50/20 text-emerald-600 font-medium shadow-xs'
          : 'border-zinc-200 dark:border-zinc-700 text-zinc-600 dark:text-zinc-400 hover:border-zinc-400'
      "
      @click="settingStore.setWallpaperConfig({ mode: 'mountain' })"
    >
      <div class="h-10 rounded bg-zinc-800 text-white mb-1.5 flex items-center justify-center text-[10px] font-mono">
        Mountain
      </div>
      <span class="text-xs">自然雪峰</span>
    </div>

    <!-- 素雅微网格 -->
    <div
      class="wallpaper-item p-2 rounded-lg border text-center cursor-pointer transition-all"
      :class="
        wallpaperConfig.mode === 'minimal'
          ? 'border-emerald-500 bg-emerald-50/20 text-emerald-600 font-medium shadow-xs'
          : 'border-zinc-200 dark:border-zinc-700 text-zinc-600 dark:text-zinc-400 hover:border-zinc-400'
      "
      @click="settingStore.setWallpaperConfig({ mode: 'minimal' })"
    >
      <div class="h-10 rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-300 mb-1.5 flex items-center justify-center text-[10px] font-mono">
        Grid
      </div>
      <span class="text-xs">素雅微网格</span>
    </div>
  </div>

  <div class="px-6 space-y-4 mb-4">
    <!-- 高斯模糊滑块 -->
    <div class="setting-item flex items-center justify-between text-xs">
      <span class="text-zinc-700 dark:text-zinc-300">高斯模糊半径: {{ wallpaperConfig.blur }}px</span>
      <ElSlider
        v-model="wallpaperBlur"
        :min="0"
        :max="60"
        :step="2"
        style="width: 140px"
        @change="(val: any) => settingStore.setWallpaperConfig({ blur: Number(val) })"
      />
    </div>

    <!-- 遮罩透明度滑块 -->
    <div class="setting-item flex items-center justify-between text-xs">
      <span class="text-zinc-700 dark:text-zinc-300">背景遮罩透明度: {{ Math.round(wallpaperConfig.opacity * 100) }}%</span>
      <ElSlider
        v-model="wallpaperOpacity"
        :min="20"
        :max="100"
        :step="5"
        style="width: 140px"
        @change="(val: any) => settingStore.setWallpaperConfig({ opacity: Number(val) / 100 })"
      />
    </div>

    <!-- 自定义图片 URL -->
    <div class="setting-item text-xs space-y-1.5">
      <div class="flex items-center justify-between">
        <span class="text-zinc-700 dark:text-zinc-300">自定义壁纸图片 URL</span>
        <button
          v-if="wallpaperConfig.mode !== 'custom'"
          type="button"
          class="text-[11px] text-emerald-600 underline cursor-pointer"
          @click="settingStore.setWallpaperConfig({ mode: 'custom' })"
        >
          启用自定义
        </button>
      </div>
      <ElInput
        v-model="customUrl"
        size="small"
        placeholder="https://images.unsplash.com/..."
        clearable
        @change="(val: string) => settingStore.setWallpaperConfig({ customUrl: val, mode: 'custom' })"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue'
  import { storeToRefs } from 'pinia'
  import SectionTitle from './SectionTitle.vue'
  import { useSettingStore } from '@/store/modules/setting'

  const settingStore = useSettingStore()
  const { wallpaperConfig } = storeToRefs(settingStore)

  const wallpaperBlur = ref(wallpaperConfig.value.blur)
  const wallpaperOpacity = ref(Math.round(wallpaperConfig.value.opacity * 100))
  const customUrl = ref(wallpaperConfig.value.customUrl || '')

  watch(
    () => wallpaperConfig.value,
    (conf) => {
      wallpaperBlur.value = conf.blur
      wallpaperOpacity.value = Math.round(conf.opacity * 100)
      customUrl.value = conf.customUrl || ''
    },
    { deep: true }
  )
</script>

<style scoped>
  .wallpaper-item {
    transition: all 0.2s ease;
  }
</style>
