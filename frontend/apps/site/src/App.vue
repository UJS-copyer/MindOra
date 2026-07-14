<script setup lang="ts">
import { createApiClient } from '@mindora/api-client';
import { createStatusBadge } from '@mindora/ui';
import { computed, onMounted, ref } from 'vue';

const api = createApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
});

const loading = ref(true);
const healthStatus = ref('checking');
const message = ref('Checking service health');

const badge = computed(() => createStatusBadge(healthStatus.value === 'up'));

onMounted(async () => {
  try {
    const response = await api.health();
    healthStatus.value = response.data.status;
    message.value = response.message;
  } catch (error) {
    healthStatus.value = 'down';
    message.value = error instanceof Error ? error.message : 'Health check unavailable';
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="site-shell">
    <section class="health-panel" aria-labelledby="health-title">
      <p class="eyebrow">MindOra public site</p>
      <h1 id="health-title">Health status</h1>
      <div class="status-line">
        <span class="status-dot" :class="badge.tone" aria-hidden="true"></span>
        <strong>{{ loading ? 'Checking' : badge.label }}</strong>
      </div>
      <p class="health-message">{{ message }}</p>
      <dl>
        <div>
          <dt>API</dt>
          <dd>{{ healthStatus }}</dd>
        </div>
      </dl>
    </section>
  </main>
</template>
