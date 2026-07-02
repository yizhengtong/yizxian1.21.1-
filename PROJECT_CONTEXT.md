# 项目上下文说明

## 项目信息
- **项目名称**: Yiz xian Mod (yizxianmod)
- **项目路径**: `D:\ZM\yizgzq\yizxian1.21.1`
- **Minecraft 版本**: 1.21.1
- **NeoForge 版本**: 21.1.230
- **Java 版本**: 21

## 前置库依赖
本项目依赖于一个前置库模组：

### 前置库信息
- **库名称**: yizmodqzk
- **库路径**: `D:\ZM\yizgzq\yiz1.21.1`
- **依赖关系**: 当前项目依赖前置库提供的 API、效果、伤害、生命值、UI 框架等核心功能
- **Gradle 配置**: 
  - 仓库: `flatDir { dirs '../yiz1.21.1/build/libs' }`
  - 依赖: `implementation "net.minecraft.client.yiz:yizmodqzk:1.0.0"`

## 重要：游戏启动流程

### ⚠️ 必须遵守的启动规则

**当前项目（yizxian1.21.1）是主项目，启动游戏时必须：**

1. **始终从当前项目启动游戏**
   - 使用 `./gradlew runClient` 从 `yizxian1.21.1` 目录启动
   - 不要从前置库目录启动游戏

2. **前置库只负责以下操作：**
   - ✅ 代码更新和修改
   - ✅ 构建 JAR 文件（`./gradlew build`）
   - ✅ JAR 文件会自动复制到当前项目的 `run/mods/` 目录

3. **自动复制机制**
   - 当前项目已配置 Gradle 任务 `copyYizmodqzkJar`
   - 每次执行 `runClient` 时，会自动从前置库的 `build/libs/` 复制最新的 JAR 到 `run/mods/`
   - 启动前无需手动复制文件

### 开发工作流

#### 修改前置库代码时：
1. 在前置库项目 `D:\ZM\yizgzq\yiz1.21.1` 中进行修改
2. 在前置库目录执行 `./gradlew build` 构建 JAR
3. 回到当前项目执行 `./gradlew runClient` 启动游戏
   - Gradle 会自动复制最新的前置库 JAR 到 `run/mods/`

#### 修改当前项目代码时：
1. 在当前项目 `D:\ZM\yizgzq\yizxian1.21.1` 中进行修改
2. 直接执行 `./gradlew runClient` 启动游戏

### Gradle 任务说明

**当前项目的相关任务：**
- `runClient`: 启动游戏客户端（自动复制前置库 JAR）
- `build`: 构建当前项目的 JAR 文件
- `copyYizmodqzkJar`: 复制前置库 JAR 到 `run/mods/`（自动执行）
- `copyWindowCaptureDll`: 复制 WindowCapture.dll 到 `run/`（自动执行）

**前置库的任务：**
- `build`: 构建 JAR 文件到 `build/libs/`
- **注意**: 前置库不应该执行 `runClient`，游戏必须从当前项目启动

## 特殊配置

### WindowCapture.dll
- 位置: `../yiz1.21.1/native/windowcapture/WindowCapture.dll`
- 自动复制到: `run/WindowCapture.dll`
- 用途: WindowCaptureManager 的 fallback 路径

## 版本管理
- 当前项目版本: 1.0.0
- 前置库版本: 1.0.0
- 两个项目通常需要同步更新

## 相关文件
- `build.gradle`: 主项目构建配置
- `gradle.properties`: 项目属性配置
- `../yiz1.21.1/build.gradle`: 前置库构建配置
- `../yiz1.21.1/gradle.properties`: 前置库属性配置

---

**最后更新**: 2026-06-30
