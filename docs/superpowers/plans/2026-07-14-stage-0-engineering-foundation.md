# Stage 0 Engineering Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Implementation status, 2026-07-14:** Stage 0 has been implemented on branch
> `feature/stage-0-foundation` using Maven for the backend. The original Gradle
> paths below were superseded by the Maven module layout recorded in this file.

**Goal:** Create a runnable engineering foundation for the personal knowledge management platform.

**Architecture:** Build a Spring Boot 3 modular monolith under `backend/` and a Vue 3 + Vite npm workspace under `frontend/`. Keep module boundaries explicit with `api`, `application`, `domain`, `infrastructure`, and `web` packages, and expose only stable API/facade surfaces across modules.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security, JUnit 5, Maven, Vue 3, Vite, TypeScript, Vitest, ESLint, Prettier, Docker Compose, MySQL, Redis, Qdrant, RocketMQ.

**Current verification commands:**

```powershell
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml test
npm test --workspace @mindora/api-client
npm run typecheck
npm run lint
npm run format:check
npm run build --workspaces --if-present
```

**Implemented backend module layout:**

```text
backend/pom.xml
backend/mindora-common/pom.xml
backend/mindora-user/pom.xml
backend/mindora-blog/pom.xml
backend/mindora-knowledge/pom.xml
backend/mindora-asset/pom.xml
backend/mindora-rag/pom.xml
backend/mindora-admin/pom.xml
backend/mindora-task/pom.xml
backend/mindora-adapter/pom.xml
backend/mindora-app/pom.xml
```

---

### Task 1: Backend Test and Module Skeleton

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/mindora-app/pom.xml`
- Create: `backend/mindora-common/pom.xml`
- Create: `backend/mindora-user/pom.xml`
- Create: `backend/mindora-blog/pom.xml`
- Create: `backend/mindora-knowledge/pom.xml`
- Create: `backend/mindora-asset/pom.xml`
- Create: `backend/mindora-rag/pom.xml`
- Create: `backend/mindora-admin/pom.xml`
- Create: `backend/mindora-task/pom.xml`
- Create: `backend/mindora-adapter/pom.xml`
- Test: `backend/mindora-common/src/test/java/com/mindora/common/api/ApiResponseTest.java`
- Test: `backend/mindora-user/src/test/java/com/mindora/user/application/AuthServiceTest.java`
- Test: `backend/mindora-app/src/test/java/com/mindora/app/web/HealthControllerTest.java`

- [ ] **Step 1: Write failing backend tests**

```java
// backend/mindora-common/src/test/java/com/mindora/common/api/ApiResponseTest.java
package com.mindora.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ApiResponseTest {
    @Test
    void successIncludesStandardEnvelopeAndTraceId() {
        ApiResponse<String> response = ApiResponse.success("pong", "trace-1");

        assertEquals("success", response.code());
        assertEquals("OK", response.message());
        assertEquals("pong", response.data());
        assertEquals("trace-1", response.traceId());
        assertNotNull(response.createdAt());
    }
}
```

```java
// backend/mindora-user/src/test/java/com/mindora/user/application/AuthServiceTest.java
package com.mindora.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindora.common.exception.BusinessException;
import com.mindora.user.domain.AuthResult;
import com.mindora.user.domain.RoleName;
import com.mindora.user.infrastructure.InMemoryUserRepository;
import com.mindora.user.infrastructure.Sha256PasswordHasher;
import com.mindora.user.infrastructure.SimpleJwtTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    @Test
    void registersEmailUserWithDefaultUserRoleAndCanLogin() {
        AuthService authService = new AuthService(
                new InMemoryUserRepository(),
                new Sha256PasswordHasher(),
                new SimpleJwtTokenService("stage0-secret", Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC)));

        AuthResult registered = authService.register("reader@example.com", "Password123!");
        AuthResult loggedIn = authService.login("reader@example.com", "Password123!");

        assertEquals("reader@example.com", registered.user().email());
        assertTrue(registered.user().roles().contains(RoleName.USER));
        assertTrue(loggedIn.accessToken().startsWith("Bearer "));
    }

    @Test
    void rejectsDuplicateEmailRegistration() {
        AuthService authService = new AuthService(
                new InMemoryUserRepository(),
                new Sha256PasswordHasher(),
                new SimpleJwtTokenService("stage0-secret", Clock.systemUTC()));

        authService.register("reader@example.com", "Password123!");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.register("reader@example.com", "Password123!"));
        assertEquals("user_email_exists", exception.code());
    }
}
```

```java
// backend/mindora-app/src/test/java/com/mindora/app/web/HealthControllerTest.java
package com.mindora.app.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicHealthEndpointReturnsStandardResponse() throws Exception {
        mockMvc.perform(get("/api/v1/public/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("success")))
                .andExpect(jsonPath("$.data.status", is("up")));
    }
}
```

- [ ] **Step 2: Run backend tests and verify RED**

Run: `cd backend && mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml test`

Expected: fails because `ApiResponse`, `AuthService`, `MindOraApplication`, and controllers do not exist yet.

- [ ] **Step 3: Implement backend minimal code**

Create the Maven multi-module project, common response/exception primitives, basic auth application service, signed token service, and public health endpoint.

- [ ] **Step 4: Run backend tests and verify GREEN**

Run: `cd backend && mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml test`

Expected: all backend tests pass.

### Task 2: Frontend Monorepo and API Client

**Files:**
- Create: `package.json`
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.base.json`
- Create: `frontend/apps/site/package.json`
- Create: `frontend/apps/site/src/main.ts`
- Create: `frontend/apps/site/src/App.vue`
- Create: `frontend/apps/admin/package.json`
- Create: `frontend/apps/admin/src/main.ts`
- Create: `frontend/apps/admin/src/App.vue`
- Create: `frontend/packages/api-client/package.json`
- Create: `frontend/packages/api-client/src/index.ts`
- Create: `frontend/packages/api-client/src/index.test.ts`
- Create: `frontend/packages/types/package.json`
- Create: `frontend/packages/types/src/index.ts`
- Create: `frontend/packages/utils/package.json`
- Create: `frontend/packages/utils/src/index.ts`
- Create: `frontend/packages/ui/package.json`
- Create: `frontend/packages/ui/src/index.ts`
- Create: `eslint.config.js`
- Create: `.prettierrc`

- [ ] **Step 1: Write failing frontend test**

```ts
// frontend/packages/api-client/src/index.test.ts
import { describe, expect, it, vi } from 'vitest';
import { createApiClient } from './index';

describe('createApiClient', () => {
  it('prefixes requests with the configured base URL and returns response data', async () => {
    const fetcher = vi.fn(async () => ({
      ok: true,
      json: async () => ({ code: 'success', message: 'OK', data: { status: 'up' }, traceId: 'trace-1' }),
    }));

    const client = createApiClient({ baseUrl: 'http://localhost:8080', fetcher });
    const result = await client.get('/api/v1/public/health');

    expect(fetcher).toHaveBeenCalledWith('http://localhost:8080/api/v1/public/health', {
      headers: { Accept: 'application/json' },
      method: 'GET',
    });
    expect(result.data.status).toBe('up');
  });
});
```

- [ ] **Step 2: Run frontend test and verify RED**

Run: `npm test --workspace @mindora/api-client`

Expected: fails because `createApiClient` does not exist yet.

- [ ] **Step 3: Implement frontend minimal code**

Create npm workspaces, Vite apps for `site` and `admin`, shared packages, API client, TypeScript config, ESLint, and Prettier config.

- [ ] **Step 4: Run frontend verification and verify GREEN**

Run:

```powershell
npm test --workspace @mindora/api-client
npm run typecheck
npm run lint
npm run format:check
```

Expected: all commands pass.

### Task 3: Local Infrastructure and Documentation

**Files:**
- Create: `compose.yaml`
- Create: `.env.example`
- Create: `backend/mindora-app/src/main/resources/application.yml`
- Create: `README.md`
- Create: `.gitignore`

- [ ] **Step 1: Add local infrastructure definitions**

Create Compose services for MySQL, Redis, Qdrant, RocketMQ name server, RocketMQ broker, backend, site, and admin. Add health checks for MySQL, Redis, Qdrant, and RocketMQ.

- [ ] **Step 2: Add environment template**

Define non-secret defaults and placeholder secrets in `.env.example`.

- [ ] **Step 3: Add startup guide**

Document local prerequisites, backend commands, frontend commands, and Docker Compose commands. The Windows development machine has Maven and Node.js available, but Docker is hosted on the Ubuntu VM.

- [ ] **Step 4: Verify documentation and config presence**

Run:

```powershell
Test-Path README.md
Test-Path compose.yaml
Test-Path .env.example
Test-Path backend/mindora-app/src/main/resources/application.yml
```

Expected: all commands return `True`.

### Task 4: Stage 0 Review

**Files:**
- Modify: `docs/superpowers/plans/2026-07-14-stage-0-engineering-foundation.md`

- [ ] **Step 1: Review deliverables against Stage 0 scope**

Check that the implementation covers:

```text
Spring Boot modular monolith
Vue 3 + Vite monorepo
Docker Compose infrastructure
MySQL/Redis/Qdrant/RocketMQ definitions
Spring Security + signed token baseline
basic RBAC roles
unified response model
unified exception handling
logging/configuration baseline
module boundary conventions
JUnit/Spring Boot Test
frontend ESLint/Prettier/TypeScript/Vitest
environment template
README startup guide
```

- [ ] **Step 2: Run final available verification**

Run all commands that are executable in the local environment. Record that local Maven/frontend verification passed, Docker is not installed on Windows, and the read-only VM check found MySQL, Redis, Qdrant, and RocketMQ NameServer healthy while the existing RocketMQ broker deployment is restarting with exit code 253.

### Current Stage 0 Verification Record

- Local backend Maven reactor tests: passed, including auth register/login MockMvc coverage.
- Local frontend API client, typecheck, lint, format, and build checks: passed before the current API-client regression test was added; rerun before the final commit.
- VM read-only checks on 2026-07-14: MySQL `mysqld is alive`, Redis `PONG`, Qdrant `healthz check passed`, RocketMQ NameServer booted.
- VM deployment gap: `mindora-rmqbroker` is running `apache/rocketmq:4.9.6` with the legacy `broker.conf` command and is restarting with exit code `253`; local `compose.yaml` defines RocketMQ `5.3.2`, so the deployment must be reconciled before claiming the remote broker is healthy.
