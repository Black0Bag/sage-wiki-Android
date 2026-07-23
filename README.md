# sage-wiki Android 客户端

sage-wiki 知识库的 Android 原生伴生 App。连接任一兼容 [sage-wiki-plus](https://github.com/blackbag-b/sage-wiki-plus) 协议的后端，管理知识源文件、查看编译产物、浏览知识图谱、监控宿主机状态、配置多模型。

## 版本宪法

- 语义化版本 **MAJOR.MINOR.PATCH**。
- **唯一版本号来源**：`app/build.gradle.kts` 中的 `versionName`。
- CI 构建直接从 gradle 属性提取版本号，不走硬编码。
- Release tag 格式：`v1.1.0`（对齐 semver，不用日期+hash）。
- 每次 Release 自动附带简体中文更新日志。

## 功能

| 屏幕 | 功能 |
|------|------|
| Dashboard（状态） | 知识库统计（entries/vectors/entities/relations/sources）+ 宿主机状态（CPU/内存/磁盘/温度/负载/Go运行时） |
| Library（文件） | 源文件列表（上传/删除/预览）+ 编译产物列表 + 知识图谱浏览 |
| Settings（配置） | 多服务器管理 + 5 模型角色分类配置（chat/summary/extract/image/embedding）+ 模型连通性测试 |
| About（关于） | 版本号 + GitHub Release 更新检查 |

## 版本历史

### v1.1.0 (2026-07-23)
- **重构**：3 Tab → Dashboard / Library / Settings
- **新增** Dashboard：知识库状态 + 宿主机状态 2秒自动刷新
- **新增** Library：源文件/编译产物/知识图谱三标签页
- **新增** Settings：多服务器配置 + 5 模型角色分类 + 模型测试
- **新增** About 关于页：版本号 + GitHub Release 更新检查
- **接口升级**：`/api/status` `/api/sysinfo` `/api/models/test` `/api/tree` `/api/graph` `/api/manifest`

### v1.0.1 (2026-07-15)
- 源文件管理功能：上传、删除、预览、分享到 Memos
- TXT 预览降级支持
- 文件图标按扩展名分发

### v1.0.0 (2026-07-13)
- 初始版本：基本源文件浏览、设置页面