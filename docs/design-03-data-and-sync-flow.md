# Design 03: Data and Synchronization Flow

## Purpose

This document defines how content enters the platform, how it becomes knowledge
base data, how it is chunked and indexed, and how related assets are stored.

The core loop is:

```text
Content creation -> Structured chunking -> Retrieval index -> Intelligent Q&A
```

## Content Sources

The first version has two main content sources:

- Gitee repository synchronization
- Admin-managed blog articles

The architecture should still allow future data sources through adapters.

Future possible sources:

- local directory
- GitHub repository
- Feishu
- Yuque
- Notion
- other document platforms

## Visibility Model

The first version uses two visibility levels:

- `public`: available for public site display and public RAG
- `private`: available only for admin-side management or private use

Public RAG must only use public content. Private content must not be exposed to
public users.

## Synchronization Modes

The admin console should support both incremental and full synchronization.

Default behavior:

- incremental synchronization by default
- full synchronization available manually
- full rebuild available for recovery or major configuration changes

The sync mode should be selectable in the admin console.

## Synchronization Triggers

The first version should support:

- manual synchronization
- scheduled synchronization

The system should reserve a boundary for future Gitee webhook support.

Recommended behavior:

```text
Manual sync
  -> Admin clicks sync
  -> Admin selects incremental or full

Scheduled sync
  -> Admin configures schedule
  -> Default mode is incremental

Webhook sync
  -> Reserved for future implementation
```

## Blog Article to Knowledge Document

Blog articles and knowledge base documents should be separated but linked.

### Blog Article

The blog article model is for creation and public display.

It owns:

- title
- body
- cover image
- summary
- category
- tags
- SEO metadata
- reading statistics
- publish state

### Knowledge Document

The knowledge document model is for retrieval and Q&A.

It owns:

- source type
- source ID
- source path
- current version
- visibility
- content snapshot
- parsing status
- chunking status
- vectorization status
- index status

When a blog article is published, the blog module publishes an event. The
knowledge module consumes the event and creates or updates the corresponding
knowledge document.

```text
Blog Article published
-> ArticlePublishedEvent
-> Knowledge Document created or updated
-> Content chunked
-> Embedding generated
-> Milvus updated
-> RAG can retrieve the content
```

## Publish-to-Knowledge Strategy

Publishing an article should trigger knowledge ingestion automatically, but the
actual processing may be delayed or batched.

Required capabilities:

- publish event generation
- queue-based processing
- delayed processing
- batch processing
- failed task retry
- manual re-index for one article
- batch re-index

RocketMQ should be used for the asynchronous processing chain.

## Version Strategy

The system should keep document version records, while the RAG index should use
only the latest valid version.

### Knowledge Document

Represents the current identity and state of a document.

Fields include:

- current version ID
- current visibility
- current index status
- current source path
- current source hash

### Knowledge Document Version

Represents a snapshot created during sync or publishing.

Fields include:

- document ID
- source type
- source ID
- source path
- content snapshot
- metadata snapshot
- content hash
- created time

### Vector Index

Milvus should only contain chunks for the latest valid version.

When a document changes:

```text
new version created
-> old chunks removed or marked inactive
-> new chunks generated
-> new embeddings written
-> document index status updated
```

## Chunking Strategy

The first version should use a globally configurable chunking strategy.

Admin-configurable options:

- chunk strategy
- chunk size
- chunk overlap
- maximum chunk size
- minimum chunk size
- whether to preserve heading hierarchy
- whether to include metadata in chunks

When chunking configuration changes, the admin console should indicate that
affected documents may need re-indexing.

## Frontmatter Handling

Frontmatter is optional.

Current local documents and synchronized repository documents may not contain
frontmatter, so the sync process must work without it.

Rules:

- Markdown without frontmatter must still sync normally.
- Frontmatter can provide initial values if present.
- Admin-side state is the final source of truth for publishing and visibility.
- Frontmatter must not be required for ingestion.

When frontmatter is missing:

- title defaults to the file name
- slug may be generated from the file name
- first Markdown heading may be recorded as auxiliary metadata
- summary defaults to empty
- tags default to empty
- category defaults to empty
- visibility defaults to `private`
- publish state defaults to imported or draft

When frontmatter exists, it can provide initial metadata such as:

- title
- tags
- category
- summary
- visibility
- publish state

The admin console may override these values.

## File and Asset Storage

File storage should be treated as a general asset system, not only as Markdown
image handling.

First-version default storage:

- local file storage

Future storage adapters may include:

- MinIO
- Aliyun OSS
- Tencent COS
- S3-compatible storage

Suggested storage areas:

```text
storage/
├─ knowledge-assets/
├─ blog-assets/
├─ site-assets/
└─ temp/
```

Message assets are not included in the first version because the message system
is text-only. A message asset area may be added later if message attachments are
introduced.

First-version managed asset types:

- Obsidian/Gitee image attachments
- blog cover images
- blog article images
- homepage avatar
- homepage background images
- banner images
- website theme or background images
- admin-uploaded configuration images

The first version of the message system supports text-only messages. Message
attachments are not included in the first version.

## Asset Metadata

File binaries should not be stored in MySQL.

MySQL should store metadata only:

- asset ID
- asset type or bucket
- original name
- storage path
- public URL
- MIME type
- file size
- content hash
- source type
- source ID
- visibility
- created time

## Markdown Image Handling

The first version should support Markdown image synchronization.

Rules:

- Only process images actually referenced by Markdown.
- Do not upload every file in an `attachments` directory by default.
- Support Obsidian-style attachment paths where images are stored in a sibling
  or same-directory `attachments` folder.
- Compute file hashes and skip unchanged images.
- Process image uploads asynchronously.
- Rewrite local image references to public or internal asset URLs.

Example flow:

```text
Markdown synced
-> image references extracted
-> referenced files resolved from attachments folder
-> file hash computed
-> unchanged assets skipped
-> changed assets stored
-> Markdown references rewritten
```

## Delete and Move Handling

The system should support move detection and delete archiving.

When a file path changes:

```text
sync detects new path
-> compare content hash
-> if hash matches an existing document, treat it as move or rename
-> update source path
-> avoid creating duplicate document
```

When a source file disappears:

```text
source missing
-> mark document as archived or deleted
-> remove from public display
-> remove or disable vector chunks in Milvus
-> keep history and sync logs
```

Archived documents should not participate in public display or RAG retrieval.

## Indexing State

The admin console should expose indexing and synchronization state.

Useful states:

- imported
- parsed
- chunked
- vectorizing
- indexed
- failed
- archived

The admin console should support:

- viewing sync history
- viewing document versions
- viewing chunk results
- viewing vectorization status
- retrying failed tasks
- rebuilding one document index
- rebuilding indexes in batch
- running full re-index

## Confirmed Decisions

- Visibility model: public/private
- Sync modes: incremental and full, default incremental
- Sync triggers: manual and scheduled, webhook reserved
- Article publishing: automatically triggers ingestion, with delay and batching
- Blog and knowledge documents: separated but linked
- Version strategy: keep versions, index latest valid version only
- Chunking: global configurable strategy
- Frontmatter: optional, admin state is final
- Missing frontmatter title rule: default title from file name
- Asset storage: local file storage first
- Markdown images: supported through reference-driven processing
- Message attachments: not included in first version
- Delete/move strategy: move detection and delete archiving
