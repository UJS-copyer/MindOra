# Design 05: Admin Console and Operations

## Purpose

The admin console is the control center of the platform.

It manages content, knowledge synchronization, RAG operations, users,
messages, site settings, and operational health.

The first version should prioritize practical observability and operational
control over decorative analytics dashboards.

## Navigation Structure

The admin console should avoid too many low-frequency standalone pages.

Recommended primary navigation:

- Dashboard
- Content Management
- Knowledge Base
- Intelligent Chat
- User Interaction
- Site Settings
- System Operations

## Dashboard

The dashboard should prioritize operations and system health.

First-version sections:

- service health
- task overview
- recent failures
- recent synchronization
- RAG runtime status

### Service Health

Show status for:

- MySQL
- Redis
- Milvus
- RocketMQ
- Chat Model
- Embedding Model
- Rerank Service
- Local Storage

### Task Overview

Show:

- pending sync tasks
- pending chunking tasks
- pending vectorization tasks
- failed indexing tasks
- today's task count

### Recent Failures

Show:

- recent failed tasks
- failure reason
- retry entry

### Recent Synchronization

Show:

- latest Gitee sync time
- added count
- updated count
- deleted count
- archived count

### RAG Runtime

Show:

- today's chat count
- average latency
- fallback count
- model error count

## Content Management

Content Management combines blog content and asset management.

Subsections:

- Articles
- Categories and Tags
- Batch Import
- File Assets

## Article Management

The blog management system should provide complete content capabilities.

First-version capabilities:

- article list
- article creation
- article editing
- draft management
- publishing and unpublishing
- batch import
- category management
- tag management
- reading statistics
- SEO metadata
- public visibility management
- knowledge ingestion status
- index rebuild entry

Article list filters:

- draft
- published
- unpublished
- imported
- indexed
- index failed
- category
- tag

## Markdown Editor

The first-version online Markdown editor should use:

```text
md-editor-v3
```

Reasons:

- It fits the Vue 3 frontend stack.
- It supports Markdown editing and preview.
- It supports image upload and pasted image upload.
- It can integrate with the platform asset module.
- It supports technical writing needs such as Mermaid and KaTeX.
- It is lightweight enough for the first version.

Editor strategy:

- Markdown is the primary editing mode.
- Split preview is supported.
- Image upload should go through the asset module.
- Pasted images should also go through the asset module.
- Frontend article rendering should not be tightly coupled to the editor
  implementation.

Future alternatives:

- Vditor, if stronger WYSIWYG or Typora-like editing is needed.
- Milkdown, if a more customizable editor framework is needed.

## File Assets

The first-version asset page should stay lightweight.

Capabilities:

- asset list
- upload
- delete
- image preview
- copy URL
- view basic metadata
- filter by asset purpose
- filter by source type
- filter by source ID

Useful filters:

- asset type
- source type
- source ID
- visibility
- MIME type
- created time

Asset types:

- knowledge image
- blog cover
- blog inline image
- site avatar
- site background
- site banner
- configuration image

Source types:

- Gitee Markdown
- Blog Article
- Site Config
- Admin Upload

Delete behavior:

- Assets with no references can be deleted.
- Assets with references should show the reference source.
- Direct deletion of referenced assets should be blocked by default.

## Knowledge Base

The Knowledge Base section should combine data source, sync, document, version,
chunking, and indexing management.

Subsections:

- Data Source and Sync
- Documents and Versions
- Chunking and Indexing

### Data Source and Sync

First-version capabilities:

- Gitee repository URL
- branch
- sync path
- authentication token
- default sync mode
- scheduled sync configuration
- enable or disable data source
- manual sync
- incremental sync
- full sync
- sync logs
- failed task retry

### Documents and Versions

Show:

- source path
- title
- visibility
- status
- current version
- chunking state
- vectorization state
- index state
- version history
- content hash
- sync time
- change type

### Chunking and Indexing

Show:

- chunk list
- chunk index
- heading path
- chunk length
- embedding status
- Milvus write status
- index update time
- failure reason

Operations:

- rebuild one document index
- batch rebuild indexes
- retry failed indexing task

## Intelligent Chat

The Intelligent Chat section should combine RAG chat operations and strategy
management.

Subsections:

- Conversations and Logs
- Retrieval Analysis
- Strategy Configuration

### Conversations and Logs

Show:

- conversation list
- user or visitor identity
- created time
- last question time
- message count
- status
- message details
- delete or hide conversation

### Retrieval Analysis

Show:

- question
- cited articles
- retrieved chunk IDs
- vector scores
- rerank scores
- selected topK
- retrieval topN
- fallback status
- latency
- error information

### Strategy Configuration

Manage:

- Chat Model
- Embedding Model
- Rerank Model
- retrieval topN
- rerank topK
- chat context turns
- max history tokens
- guest chat switch
- strict grounding switch
- streaming output switch
- log switches

## User Interaction

The User Interaction section covers users and messages.

First-version capabilities:

- user list
- email user identity
- GitHub user identity
- user status
- registration time
- latest login time
- login count
- chat count
- message count
- latest visit time
- message list
- message content
- user or visitor source
- submitted time
- message status
- approve message
- hide message
- delete message

Reserved future capabilities:

- user banning
- rate limiting
- IP blacklist
- sensitive word filtering
- risk rules

The first version may reserve fields or API boundaries such as:

- `user_status`
- `risk_level`
- `ban_until`
- `rate_limit_profile`

## Site Settings

Site Settings should combine personal homepage, navigation, SEO, and visual
resources.

Suggested groups:

- Basic Info
- Personal Homepage
- Navigation Menu
- SEO
- Visual Resources

### Basic Info

Manage:

- site name
- site description
- keywords
- ICP record or filing information
- social links

### Personal Homepage

Manage:

- name or nickname
- bio
- avatar
- background image
- banner
- research or technical direction
- project showcase

### Navigation Menu

Manage:

- menu name
- route or link
- sort order
- visibility

### SEO

Manage:

- default title
- default description
- default keywords
- OpenGraph information

### Visual Resources

Manage:

- homepage background image
- banner image
- avatar resource
- theme resource

Images should reference assets through IDs such as:

- `avatar_asset_id`
- `background_asset_id`
- `banner_asset_id`

## System Operations

System Operations should cover health checks, task queue management, and logs.

### Service Status

Show:

- MySQL
- Redis
- Milvus
- RocketMQ
- Chat Model
- Embedding Model
- Rerank Service
- Local Storage

### Task Queue

Manage:

- Gitee sync tasks
- article ingestion tasks
- image asset processing tasks
- chunking tasks
- vectorization tasks
- index rebuild tasks
- retry failed tasks
- cancel tasks
- manually trigger tasks

### Logs

Show:

- error logs
- sync logs
- chat logs
- retrieval logs
- fallback logs
- operation logs

High-risk operations must require confirmation.

Examples:

- full synchronization
- full index rebuild
- batch asset soft-delete or cleanup request
- cancel running task
- clear logs
- retry many failed tasks

Operation logs should record:

- operator ID
- operation type
- target type
- target ID
- before state
- after state
- created time
- IP
- user agent

## Configuration Boundary

Business strategy and model-related configuration can be managed in the admin
console.

Deployment-level or security-sensitive configuration should not be dynamically
modified from the admin console.

Do not dynamically manage these in the admin UI:

- local storage root path
- public URL prefix for storage
- file size limit
- allowed MIME types

These should come from:

- `application.yml`
- environment variables
- Docker Compose environment configuration

The admin console may show them as read-only runtime information.

## Confirmed Decisions

- Dashboard focuses on operations and system health.
- Blog editor uses Markdown-first dual mode.
- First-version editor is `md-editor-v3`.
- Knowledge Base combines data source, sync, documents, versions, chunking, and
  indexing.
- File Assets stays lightweight but supports filtering by usage and source.
- RAG management is split into conversations, retrieval analysis, and strategy.
- System settings are grouped by module and configuration type.
- Storage path and similar deployment-level settings are not dynamically
  editable from the admin console.
- User Interaction includes basic user management, message moderation, and user
  behavior statistics.
- System Operations includes health checks, task queue management, and logs.
