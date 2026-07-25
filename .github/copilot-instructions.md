# Copilot Instructions — sage-wiki-Android

> **必须在开始任何工作前阅读 `.github/AGENT_CONTEXT.md`。**
>
> 该文件包含本仓库的版本号宪法（最高优先级规则），任何代码修改在提交前必须遵守。

## 关键规则摘要

1. 版本号定义在 `app/build.gradle.kts` → `versionName`，格式 `MAJOR.MINOR.PATCH`
2. 小bug修复 → PATCH +N（累计制，无上限）
3. 中型bug/新功能 → MINOR +1，PATCH 归零
4. 架构重构 → MAJOR +1，MINOR 和 PATCH 都归零
5. versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
6. 同时影响 sage-wiki-plus 时，两个仓库同步升级版本号
7. 每次 commit / push 前必须检查版本号是否已正确更新

完整规则见 `.github/AGENT_CONTEXT.md`。
