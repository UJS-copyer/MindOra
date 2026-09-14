参数配置说明与本地环境模板：

# VM 基础设施
MYSQL_HOST="192.168.222.128"
MYSQL_PORT="3306"
MYSQL_DATABASE="mindora"
MYSQL_USER="mindora"
MYSQL_PASSWORD="mindora_password"

REDIS_HOST="192.168.222.128"
REDIS_PORT="6379"
REDIS_PASSWORD=""

ROCKETMQ_NAMESRV_ADDR="192.168.222.128:9876"
MINDORA_VM_HOST="192.168.222.128"

# Stage 2 adapter 开关
MINDORA_KNOWLEDGE_PERSISTENCE="jdbc"
MINDORA_KNOWLEDGE_ASYNC="true"
MINDORA_KNOWLEDGE_DISPATCHER="rocketmq"
MINDORA_KNOWLEDGE_SYNC_TOPIC="mindora-knowledge-sync"
MINDORA_KNOWLEDGE_SYNC_CONSUMER_GROUP="mindora-knowledge-sync-consumer"
MINDORA_GITEE_ENABLED="true"
MINDORA_EMBEDDING_ENABLED="true"
MINDORA_RERANK_ENABLED="true"
MINDORA_VECTOR_ENABLED="true"
GITEE_API_BASE_URL="https://gitee.com/api/v5"

# 设计文档以 Milvus 为长期规划；当前虚拟机和现有配置使用 Qdrant，
# 后端通过 VectorStorePort 保留向 Milvus 替换的边界。

# GitHub OAuth
AUTH_SECRET="your-auth-secret"
AUTH_GITHUB_ID="your-github-client-id"
AUTH_GITHUB_SECRET="your-github-client-secret"
ADMIN_EMAILS="admin@example.com"

# Base LLM model
OPENAI_API_KEY="your-openai-api-key"
OPENAI_BASE_URL="https://walkai.top/v1"
OPENAI_CHAT_MODEL="gpt-5.4-mini"

# Embedding model (文档: https://help.aliyun.com/zh/model-studio/dashscopeembedding-in-llamaindex)
EMBEDDING_API_KEY="your-embedding-api-key"
EMBEDDING_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
EMBEDDING_MODEL="text-embedding-v4"
EMBEDDING_DIMENSIONS="1024"

# Rerank model (文档: https://api-docs.siliconflow.cn/docs/api/rerank-post)
RERANK_MODEL_API_KEY="your-rerank-api-key"
RERANK_PROVIDER="siliconflow"
RERANK_BASE_URL="https://api.siliconflow.cn/v1"
# POST /rerank
RERANK_MODEL="BAAI/bge-reranker-v2-m3"

# Qdrant 向量库配置
QDRANT_URL="https://your-cluster-id.cloud.qdrant.io"
QDRANT_API_KEY="your-qdrant-api-key"
QDRANT_COLLECTION="mindora_chunk"
QDRANT_VECTOR_NAME="dense"

GITEE_DEFAULT_REPO_URL="https://gitee.com/your-username/your-repo"
GITEE_DEFAULT_BRANCH="master"
SYNC_CRON_SECRET="your-cron-secret"
GITEE_TOKEN="your-gitee-token"
