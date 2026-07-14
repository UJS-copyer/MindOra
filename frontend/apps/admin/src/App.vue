<script setup lang="ts">
import { ref } from 'vue';

const email = ref('');
const password = ref('');
const status = ref('Ready');

function submitLogin() {
  status.value =
    email.value && password.value ? 'Authentication service pending' : 'Enter credentials';
}
</script>

<template>
  <main class="admin-shell">
    <section class="login-shell" aria-labelledby="admin-title">
      <p class="eyebrow">MindOra admin</p>
      <h1 id="admin-title">Sign in</h1>
      <form @submit.prevent="submitLogin">
        <label>
          Email
          <input v-model="email" autocomplete="email" name="email" type="email" />
        </label>
        <label>
          Password
          <input
            v-model="password"
            autocomplete="current-password"
            name="password"
            type="password"
          />
        </label>
        <button type="submit">Sign in</button>
      </form>
      <p class="status" role="status">{{ status }}</p>
    </section>
  </main>
</template>
