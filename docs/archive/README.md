# 归档文档（冻结）

本目录存放**已完成使命**的文档：阶段执行计划、被取代的设计、以及只具历史价值的一次性材料。

## 规则

1. **冻结**：归档文档不再修改。发现错误就在活跃文档里订正并在此追加一行说明。
2. **命名**：`YYYY-MM-<原文件名>`（补日期前缀，便于按时间排序）。
3. **登记**：每次归档必须在下方表格追加一行。
4. **移动方式**：必须用 `git mv` 保留历史，并同步更新所有引用它的文档链接。
5. **索引**：归档文档**不出现在** `docs/README.md` 的活跃索引里，只在这里登记。

## 索引

| 归档文件 | 原位置 | 归档日期 | 为什么归档 | 承接者（活跃文档） |
| --- | --- | --- | --- | --- |
| [2026-10-stage-3.3-rag-generation-layer-plan-cn.md](2026-10-stage-3.3-rag-generation-layer-plan-cn.md) | `docs/stage-3.3-rag-generation-layer-plan-cn.md` | 2026-10-10 | Stage 3.3 生成层六步执行完毕，计划文档使命结束 | 契约与实测见 [design-11](../design-11-rag-answer-generation-layer.md)；交付与验收见 [stage-3.3-task-manifest-cn.md](../stage-3.3-task-manifest-cn.md) |
| [2026-07-14-stage-0-engineering-foundation-plan.md](2026-07-14-stage-0-engineering-foundation-plan.md) | `docs/superpowers/plans/2026-07-14-stage-0-engineering-foundation.md` | 2026-10-10 | Stage 0 已交付，执行计划结束 | [project-status-cn.md](../project-status-cn.md)、[design-07](../design-07-implementation-roadmap.md) |
| [2026-07-14-stage-1-content-and-admin-plan.md](2026-07-14-stage-1-content-and-admin-plan.md) | `docs/superpowers/plans/2026-07-14-stage-1-content-and-admin.md` | 2026-10-10 | Stage 1 已交付，执行计划结束 | [project-status-cn.md](../project-status-cn.md)、[design-08](../design-08-frontend-design-spec-stage-1.md) |
