# Stage 1 Content and Admin Implementation Plan

> This plan follows the existing Stage 1 roadmap and is executed with
> test-driven development. Each production behavior must have a focused test
> that was observed failing before implementation.

## Goal

Deliver the minimum content loop:

```text
Admin login -> create/edit draft -> publish -> public list/detail
```

The implementation keeps the blog, admin, and asset responsibilities separate
so Markdown upload handling and knowledge ingestion can be added later.

## Work Split

### Task 1: Backend blog and admin boundary

- Article, category, and tag domain/application services.
- Draft, published, and unpublished state transitions.
- Public list/detail queries with category/tag filters.
- Read-count increment.
- Admin command and public query controller contracts.
- Asset cover-image ID boundary without coupling blog code to file storage.
- JUnit tests for each behavior and controller contract.

### Task 2: Frontend admin and public loop

- API client methods and shared types.
- Admin login, token storage, and shell navigation.
- Article list and editor with Markdown-first boundary.
- Draft/publish/unpublish actions.
- Category/tag fields and cover asset entry point.
- Public homepage, list, detail, filters, and read count.
- Vitest tests for client methods and state/formatting behavior.

### Task 3: Integration and release checks

- Cherry-pick the two isolated worker commits.
- Resolve only genuine cross-slice conflicts.
- Run backend tests and frontend test/typecheck/lint/format/build checks.
- Run a final spec review and code-quality review.
- Confirm `git status` is clean before the Stage 1 commit.

## Non-goals

- RAG chat and knowledge indexing.
- Advanced SEO, comments, analytics, or complete site configuration.
- Physical asset cleanup or database migration rollout.
