# sage-wiki-Android — Agent 上下文手册

> **这是本仓库最权威的 Agent 指南。任何 AI Agent（Copilot、Cursor、Windsurf、OpenCode、Omnibot 等）在操作本仓库前必须完整阅读本文件。**

---

## ⚖️ 版本号宪法（最高优先级规则）

以下规则具有**最高约束力**，任何 Agent 在修改本仓库代码时必须严格遵守。
在提交代码（commit / push）前，Agent **必须**检查并更新版本号。

### 版本号位置

- 本仓库版本号定义在 `app/build.gradle.kts` 中的 `versionName` 字段
- 格式：`MAJOR.MINOR.PATCH`（语义化版本，SemVer）
- `versionCode` 由版本号计算：`versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`
- 当前版本：**2.0.1**（versionCode = 20001）

### 三级递增规则

#### 条件① — PATCH（小bug修复，累计制）

- 每修复**一个小 bug** → PATCH `+1`
- 同一轮开发修复多个小 bug → **累计计数后一次性递增**
  - 例：本轮修复了 5 个小 bug → PATCH `+5`，如 `2.0.1` → `2.0.6`
- 累计无上限，一直递增，直到条件②触发时**PATCH 归零**

#### 条件② — MINOR（中型bug / 小功能增量）

- 每修复**一个中型 bug** 或添加**一个新功能** → MINOR `+1`
- 触发时 PATCH **归零**：
  - 例：当前 `2.0.5`（已累计 5 个小bug），修复一个中型bug → `2.1.0`
- MINOR 一直累计递增，直到条件③触发时 **MINOR 和 PATCH 都归零**

#### 条件③ — MAJOR（架构重构 / 破坏性变更）

- **架构整体重构**、**破坏性 API 变更**、**技术栈重大升级** → MAJOR `+1`
- 触发时 MINOR 和 PATCH **都归零**：
  - 例：当前 `2.15.30`，架构重构 → `3.0.0`
- **使用原则**：
  - 超大型变更极少发生，不应短时间快速推进大版本号
  - 同一自然月内不应推进两次大版本（如确有必要，需在 commit message 中说明理由）
  - 优先用条件②累积变更量，等积累足够再走大版本

### 同步联动规则

本仓库（sage-wiki-Android APP）与 sage-wiki-plus（后端）是**独立项目，各自独立计数**：
- 两个仓库的版本号**不需要保持数字一致**
- 但当一次改动**同时涉及两个仓库**（如前后端字段对齐、API 签名变更）时，两个仓库的版本号**应当同时升级**
- Agent 在操作本仓库时，如发现改动同时影响后端，必须在两个仓库同步更新版本号

### 执行清单（Agent 每次提交前必须过一遍）

```
□ 本次改动属于哪一级？
  □ 小bug修复 → 数一数改了几个，PATCH += N
  □ 中型bug / 新功能 → MINOR += 1, PATCH = 0
  □ 架构重构 → MAJOR += 1, MINOR = 0, PATCH = 0
□ 是否同时影响 sage-wiki-plus？
  □ 是 → 两个仓库都要更新版本号
  □ 否 → 只更新本仓库
□ versionCode 是否已对应更新？
□ commit message 中是否注明了版本变更原因？
```

---

## 项目概览

- **仓库**：sage-wiki-Android
- **类型**：sage-wiki 知识库系统的 Android 客户端
- **技术栈**：Kotlin 2.0.20 + Jetpack Compose + Material 3
- **包名**：`com.sagewiki.android`
- **编译**：`./gradlew assembleDebug`（CI 自动执行）
- **CI**：push 到 master 自动构建 APK 并创建 GitHub Release
- **版本号唯一来源**：`app/build.gradle.kts` → `versionName`
- **API 对接**：连接 sage-wiki-plus 后端（默认端口 8082）

## CI 流程

1. push 到 master → 触发 Build APK workflow
2. 自动提取 versionName → 构建 debug APK
3. 自动创建 GitHub Release（tag 格式 `v{versionName}`）
4. 版本号校验步骤会检查版本号是否合法

## 项目结构

```
app/
├── build.gradle.kts          ← 版本号定义在此
├── src/main/java/com/sagewiki/android/
│   ├── MainActivity.kt
│   ├── ui/                    ← Compose UI
│   ├── network/                ← Retrofit 网络层
│   └── data/                   ← 数据模型
└── src/main/res/              ← 资源文件
```

## 编码规范

- 语言：Kotlin，JVM Target 17
- UI：Jetpack Compose + Material 3
- 网络：Retrofit 2.11.0 + OkHttp 4.12.0 + Gson 2.11.0
- 导航：Navigation Compose 2.8.1
- CI 使用 debug signing key（便于自动构建）

---

*本文件由 Agent 上下文系统维护，最后更新：2026-07-25*
