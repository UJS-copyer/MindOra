import path from 'node:path';
import { fileURLToPath } from 'node:url';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';
import ElementPlus from 'unplugin-element-plus/vite';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig, loadEnv } from 'vite';

const dirname = path.dirname(fileURLToPath(import.meta.url));
const resolvePath = (value: string) => path.resolve(dirname, value);

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, dirname, '');
  const appVersion = env.VITE_VERSION || process.env.npm_package_version || '0.0.0';
  const apiProxyTarget = env.VITE_API_PROXY_URL || 'http://localhost:8080';

  return {
    define: {
      __APP_VERSION__: JSON.stringify(appVersion)
    },
    base: env.VITE_BASE_URL || '/',
    server: {
      host: true,
      port: Number(env.VITE_PORT || 5175),
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true
        }
      }
    },
    preview: {
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true
        }
      }
    },
    resolve: {
      alias: {
        '@': resolvePath('src'),
        '@views': resolvePath('src/views'),
        '@imgs': resolvePath('src/assets/images'),
        '@icons': resolvePath('src/assets/icons'),
        '@utils': resolvePath('src/utils'),
        '@stores': resolvePath('src/store'),
        '@styles': resolvePath('src/assets/styles')
      }
    },
    plugins: [
      vue(),
      tailwindcss(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia', '@vueuse/core'],
        dts: 'src/types/import/auto-imports.d.ts',
        resolvers: [ElementPlusResolver()]
      }),
      Components({
        dts: 'src/types/import/components.d.ts',
        resolvers: [ElementPlusResolver()]
      }),
      ElementPlus({ useSource: true })
    ],
    build: {
      target: 'es2015',
      chunkSizeWarningLimit: 2000,
      minify: 'esbuild'
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `
            @use "@styles/core/el-light.scss" as *;
            @use "@styles/core/mixin.scss" as *;
          `
        }
      }
    }
  };
});
