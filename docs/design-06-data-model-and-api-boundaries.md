# Design 06: Data Model and API Boundaries

## Purpose

This document defines the logical data model and module API boundaries.

It does not provide full SQL DDL. The goal is to clarify entities, ownership,
key fields, states, relationships, index suggestions, API boundaries, and event
boundaries before implementation.

## Modeling Level

This design uses a logical model.

It includes:

- entities
- module ownership
- key fields
- state enums
- relationship rules
- index suggestions
- facade/query/command boundaries
- domain event boundaries

It does not lock down:

- full SQL DDL
- exact column types and lengths
- all index names
- all foreign key constraints

Implementation can refine those details later.

## ID Strategy

Business entity IDs and log IDs should be different.

### Business Entity IDs

Use UUID for long-lived business entities.

Examples:

- `user`
- `auth_account`
- `role`
- `permission`
- `blog_article`
- `category`
- `tag`
- `asset`
- `data_source`
- `knowledge_document`
- `knowledge_document_version`
- `knowledge_chunk`
- `conversation`
- `chat_message`
- `system_config`

Public article access should still use slugs:

```text
/blog/{slug}
```

### Log and Flow IDs

Use `BIGINT AUTO_INCREMENT` in the first version for high-volume logs and flow
records.

Future distributed deployment may replace these with Snowflake IDs.

Examples:

- `sync_task_log`
- `retrieval_log`
- `operation_log`
- `error_log`
- `model_call_log`
- `asset_process_log`

### Trace IDs

Use UUID-style IDs for request and task tracing.

Examples:

- `trace_id`
- `request_id`
- `task_trace_id`

These IDs connect logs across modules and asynchronous task chains.

## Relationship Constraints

Use database foreign keys for core business relationships where consistency is
important.

Do not force database foreign keys on high-volume logs, task records, or vector
index records.

### Suitable for Foreign Keys

- `blog_article -> category`
- `blog_article_tag -> blog_article`
- `blog_article_tag -> tag`
- `knowledge_document_version -> knowledge_document`
- `knowledge_chunk -> knowledge_document_version`
- `chat_message -> conversation`
- `message -> user`, optional

### Avoid Foreign Keys

- `sync_task`
- `task_log`
- `retrieval_log`
- `operation_log`
- `asset_reference`
- `vector_index_record`
- `failed_job`

These records should use logical references such as IDs and trace IDs.

## State Field Strategy

Use string enums stored in the database and backend enum constraints in code.

Enum values should use lowercase snake_case.

Examples:

```text
visibility:
- public
- private

article_status:
- draft
- published
- unpublished
- archived

document_status:
- active
- archived
- deleted

index_status:
- pending
- chunked
- vectorizing
- indexed
- failed

task_status:
- pending
- running
- success
- failed
- canceled
```

## Module Entity Ownership

### User and Permission Module

Entities:

- `user`
- `auth_account`
- `role`
- `permission`
- `user_role`
- `role_permission`

The first version should use lightweight RBAC.

Initial roles:

- `super_admin`
- `user`

Reserved roles:

- `admin`
- `editor`
- `viewer`

Example permissions:

- `blog:read`
- `blog:write`
- `blog:publish`
- `kb:read`
- `kb:sync`
- `kb:index`
- `rag:read`
- `rag:config`
- `asset:manage`
- `user:manage`
- `message:review`
- `system:operate`

### Blog Module

Entities:

- `blog_article`
- `category`
- `tag`
- `blog_article_tag`

The blog module owns content creation and display-oriented fields.

Key fields:

- title
- slug
- body
- summary
- cover asset ID
- category ID
- tags
- SEO metadata
- publish status
- visibility
- reading count
- index status

### Knowledge Module

Entities:

- `data_source`
- `sync_task`
- `knowledge_document`
- `knowledge_document_version`
- `knowledge_chunk`

The knowledge module owns ingestion, parsing, versioning, chunking, and indexing
state.

`sync_task` is a task state and flow record. It belongs to the knowledge
workflow, but it follows task and log retention rules rather than normal
business soft-delete rules.

Key fields:

- source type
- source ID
- source path
- content hash
- current version ID
- visibility
- document status
- chunking status
- vectorization status
- index status

### Asset Module

Entities:

- `asset`
- `asset_reference`

The asset module owns file metadata and references. Business modules should not
write files directly.

Key fields:

- storage path
- public URL
- MIME type
- file size
- content hash
- asset type
- source type
- source ID
- visibility
- reference count or reference records

### RAG Module

Entities:

- `conversation`
- `chat_message`
- `retrieval_log`
- `model_call_log`

The RAG module owns chat conversations, messages, retrieval records, and model
call logs.

Key fields:

- user ID or anonymous ID
- conversation status
- message role
- message content
- cited article IDs
- retrieved chunk IDs
- vector scores
- rerank scores
- model name
- latency
- fallback status

### User Interaction Module

Entities:

- `message`

The message module owns public guestbook or message content.

Key fields:

- user ID or guest identity
- content
- status
- IP
- user agent
- reviewed by
- reviewed at

### Site Settings Module

Entities may be structural or stored in `system_config`.

Possible structural entities:

- `navigation_menu`
- `homepage_section`
- `site_link`

Images should reference assets by ID.

Examples:

- `avatar_asset_id`
- `background_asset_id`
- `banner_asset_id`

### System Module

Entities:

- `system_config`
- `operation_log`
- `error_log`
- `task_log`

The system module owns configuration, operation logs, and runtime logs.

## Configuration Model

The first version should use a unified key-value configuration table.

Suggested fields:

- `id`
- `config_key`
- `config_value`
- `value_type`
- `group_key`
- `description`
- `editable`
- `sensitive`
- `created_at`
- `updated_at`

Suitable configuration:

- RAG strategy switches
- model configuration
- sync strategy
- site settings
- navigation configuration
- guest chat limits
- log switches

Sensitive values such as API keys should be encrypted at rest or masked in the
admin UI.

Deployment-level settings should not be dynamically edited from the admin UI.

Examples:

- local storage root path
- public storage URL prefix
- max file size
- allowed MIME types

These should come from `application.yml`, environment variables, or Docker
Compose configuration, and may be displayed as read-only runtime information.

## Soft Delete Strategy

Core business entities should use soft delete or archive states.

Logs and high-volume task records should use retention and cleanup policies
instead of normal soft delete.

### Common Soft Delete Fields

Recommended fields:

- `deleted`
- `deleted_at`
- `deleted_by`
- `delete_reason`

Knowledge entities may also use:

- `archived`
- `archived_at`
- `archive_reason`

### User Module

Soft delete:

- `user`
- `auth_account`
- `user_role`

Reason:

- users are linked to messages, chat conversations, and operation logs
- account deletion should not break historical records
- third-party account bindings need traceable inactive states

### Permission Module

Soft delete:

- `role`
- `permission`
- `role_permission`

Reason:

- permission changes affect backend security
- deleted roles or permissions should remain auditable
- operation logs need historical context

### Blog Module

Soft delete:

- `blog_article`
- `category`
- `tag`
- `blog_article_tag`

Reason:

- published articles may be cited, indexed, or logged
- categories and tags may have historical references
- deleting an article should first remove it from public display and public RAG

Article deletion behavior:

```text
blog_article deleted
-> hidden from public site
-> removed from public RAG
-> history and operation records retained
```

### Knowledge Module

Soft delete or archive:

- `data_source`
- `knowledge_document`
- `knowledge_document_version`
- `knowledge_chunk`

Reason:

- source documents, versions, and chunks are required for RAG traceability
- file delete and move handling requires history
- Qdrant vectors may be deleted or marked inactive, while MySQL records remain

Suggested semantics:

- `knowledge_document` uses archived state
- `knowledge_chunk` uses inactive or archived state

### Asset Module

Soft delete:

- `asset`
- `asset_reference`

Reason:

- assets may be referenced by articles, site settings, or knowledge documents
- referenced assets should not be physically deleted immediately
- physical cleanup should be handled by controlled tasks

Rules:

```text
Referenced asset
-> block deletion by default or mark for cleanup

Unreferenced asset
-> allow soft delete
-> physical file cleanup by controlled task
```

### RAG Module

Soft delete:

- `conversation`
- `chat_message`

Reason:

- users may delete conversations
- backend may still need audit and quality analysis
- messages may be linked to retrieval logs

Do not normally soft delete:

- `retrieval_log`
- `model_call_log`

These are logs and should be managed by retention policy.

### Message Module

Soft delete:

- `message`

Reason:

- messages have review, hide, and delete states
- admin should retain moderation history

Suggested states:

- `pending`
- `approved`
- `hidden`
- `deleted`

### Site Settings Module

Soft delete if structural tables exist:

- `navigation_menu`
- `homepage_section`
- `site_link`

If site settings are only stored in `system_config`, use versioned updates and
configuration change logs instead of ordinary soft delete.

### System Config Module

Do not use ordinary soft delete for `system_config`.

Recommended fields:

- `enabled`
- `editable`
- `version`

Changes should be recorded in configuration change logs or operation logs.

### Task and Log Modules

No normal soft delete.

Use retention, archive, and controlled cleanup policies for:

- `sync_task`
- `task_log`
- `operation_log`
- `retrieval_log`
- `error_log`
- `model_call_log`
- `asset_process_log`
- `temporary_upload`

Cleanup operations must be confirmed and recorded in operation logs.

## API Boundary Level

Design 06 defines API boundaries at the facade/query/command level.

It does not define exact Java method signatures or DTO fields.

## Module API Boundaries

### Blog API

Exposed capabilities:

- get published article
- get article for knowledge ingestion
- update article index status
- list public articles for citation
- publish article
- unpublish article

Forbidden:

- knowledge module directly querying `blog_article` tables
- RAG module directly querying blog mappers

### Knowledge API

Exposed capabilities:

- create or update document from source
- get document for retrieval
- get current document version
- mark document archived
- list chunks for debug
- update index status

Forbidden:

- blog module directly writing `knowledge_document`
- RAG module directly changing knowledge document state

### Asset API

Exposed capabilities:

- create asset
- resolve asset URL
- list asset references
- attach reference
- detach reference
- check whether asset can be deleted

Forbidden:

- any module writing files directly
- any module bypassing asset metadata records

### RAG API

Exposed capabilities:

- create conversation
- ask question
- stream answer
- get conversation history
- list retrieval logs
- update RAG strategy configuration

Forbidden:

- RAG directly bypassing metadata permission filtering
- RAG directly querying unrelated module infrastructure

### User API

Exposed capabilities:

- get current user
- check permission
- assign role
- update user status
- bind auth account

Forbidden:

- other modules directly mutating user role tables

### Admin API

The admin module should aggregate module APIs.

It should not query or mutate other modules' internal tables directly.

## Domain Events

Cross-module writes should prefer events.

Important events:

- `ArticlePublishedEvent`
- `ArticleUpdatedEvent`
- `ArticleUnpublishedEvent`
- `RepositorySyncedEvent`
- `DocumentVersionCreatedEvent`
- `DocumentChunkedEvent`
- `DocumentIndexedEvent`
- `AssetUploadedEvent`
- `AssetDeletedEvent`
- `ChatCompletedEvent`
- `TaskFailedEvent`

Example flow:

```text
Blog article published
-> ArticlePublishedEvent
-> Knowledge creates or updates Knowledge Document
-> DocumentVersionCreatedEvent
-> Chunking task
-> Vectorization task
-> DocumentIndexedEvent
```

## Storage Responsibilities

### MySQL

Stores:

- business entities
- metadata
- configuration
- task state
- logs
- soft delete and archive state

### Qdrant

Stores:

- vector embeddings
- minimal chunk metadata
- minimal retrieval payloads required for lookup

Qdrant metadata must stay minimal and should not duplicate full business
records.

### Local File Storage

Stores:

- uploaded images
- synchronized Markdown image assets
- site visual assets
- temporary files

File metadata and references live in MySQL.

## Index Suggestions

These are logical index suggestions, not final index names.

Useful indexes:

- `blog_article.slug`
- `blog_article.status`
- `blog_article.visibility`
- `blog_article.category_id`
- `blog_article.published_at`
- `knowledge_document.source_type + source_id`
- `knowledge_document.source_path`
- `knowledge_document.visibility`
- `knowledge_document.status`
- `knowledge_document.current_version_id`
- `knowledge_document_version.document_id`
- `knowledge_document_version.content_hash`
- `knowledge_chunk.document_version_id`
- `knowledge_chunk.index_status`
- `asset.content_hash`
- `asset.source_type + source_id`
- `conversation.user_id`
- `chat_message.conversation_id`
- `retrieval_log.conversation_id`
- `retrieval_log.trace_id`
- `operation_log.operator_id`
- `operation_log.created_at`

## Confirmed Decisions

- Data model design uses logical model granularity.
- Core relationships use foreign keys; logs, tasks, and index records avoid
  strong foreign keys.
- Business entity IDs use UUID.
- Logs and high-volume flow records use BIGINT auto-increment first, with
  Snowflake ID reserved for future distributed deployment.
- Trace IDs use UUID-style IDs.
- State fields use string enums and backend enum constraints.
- API boundaries are defined at facade/query/command level.
- First-version configuration uses a unified key-value table.
- Sensitive config values are encrypted or masked.
- Deployment-level config is not dynamically changed from the admin UI.
- Core business entities use soft delete or archive semantics.
- Logs and task records use retention and cleanup policies.
- Lightweight RBAC is included in the first version.
- Initial roles are `super_admin` and `user`.
