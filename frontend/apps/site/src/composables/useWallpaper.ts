import { DEFAULT_WALLPAPER_SETTINGS, PRESET_WALLPAPERS } from '@mindora/ui';
import type { WallpaperMode, WallpaperSettings } from '@mindora/types';
import { computed, onMounted, ref } from 'vue';

const STORAGE_KEY = 'mindora-wallpaper-settings';

const state = ref<WallpaperSettings>({
  ...DEFAULT_WALLPAPER_SETTINGS
});

export function useWallpaper() {
  function load() {
    try {
      const cached = localStorage.getItem(STORAGE_KEY);
      if (cached) {
        const parsed = JSON.parse(cached) as Partial<WallpaperSettings>;
        state.value = {
          ...DEFAULT_WALLPAPER_SETTINGS,
          ...parsed
        };
      }
    } catch {
      state.value = { ...DEFAULT_WALLPAPER_SETTINGS };
    }
  }

  function save() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state.value));
    } catch {
      // ignore localStorage quota errors
    }
  }

  function setMode(mode: WallpaperMode) {
    state.value.mode = mode;
    save();
  }

  function updateSettings(settings: Partial<WallpaperSettings>) {
    state.value = {
      ...state.value,
      ...settings
    };
    save();
  }

  const layerStyle = computed(() => {
    const { mode, blur, opacity, customUrl } = state.value;
    const filter = `blur(${blur}px)`;

    if (mode === 'mountain') {
      const url = PRESET_WALLPAPERS.mountain.imageUrl;
      return {
        backgroundImage: `url('${url}')`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
        filter,
        opacity,
        transform: 'scale(1.05)'
      };
    }

    if (mode === 'custom' && customUrl) {
      return {
        backgroundImage: `url('${customUrl}')`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
        filter,
        opacity,
        transform: 'scale(1.05)'
      };
    }

    // Default aurora or minimal
    return {
      filter,
      opacity
    };
  });

  const layerClass = computed(() => {
    if (state.value.mode === 'minimal') {
      return 'bg-grid';
    }
    if (state.value.mode === 'aurora') {
      return 'bg-aurora';
    }
    return '';
  });

  onMounted(() => {
    load();
  });

  return {
    wallpaper: state,
    setMode,
    updateSettings,
    layerStyle,
    layerClass,
    presets: PRESET_WALLPAPERS
  };
}
