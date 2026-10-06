# Enchantment Reforged

Minecraft **1.20.1** 的附魔与机制改造模组：重做锋利与力量、铁砧改为消耗经验点数，
新增 26 个附魔（含锋利Plus）、9 组配置页与可自定义的粒子特效；可选兼容矛
[Backported Spears](https://www.mcmod.cn/class/23213.html)。作者：**Summy**。

本仓库同时维护两个加载器版本。两份代码功能一致但**源码独立**——Fabric 用 Yarn 映射、
Forge 用 Mojang 官方映射，无法共享同一份源码。

| 目录 | 平台 | 版本 | 说明 |
| --- | --- | --- | --- |
| [`fabric/`](fabric/README.md) | Fabric Loader 0.18.4 + Fabric API 0.92.7 | `1.3.4` | 原始版本 |
| [`forge/`](forge/README.md) | Forge 47.4.10（ForgeGradle 6 + MixinExtras） | `1.3.4-forge` | 由 Fabric 版移植，含矛兼容与构建期自检 |

## 构建

两个工程都需要 **JDK 17**。

### Fabric

```bash
cd fabric
./gradlew build
# 产物：build/libs/enchantment-reforged-1.3.4.jar
```

### Forge

```bash
cd forge
./gradlew build
# 产物会复制到仓库根目录的 JAR/enchantment-reforged-forge/enchantment-reforged-1.3.4-forge.jar
```

Forge 工程自带两个构建期保障（`gradlew build` 自动执行）：

- `verifyMixinTargets`：校验所有 Mixin 注解里的 Minecraft 类名真实存在、且注入目标都能在
  refmap 里查到映射——用来拦住"dev 环境宽松匹配掩盖了错误、到生产才崩"这类问题。
- 离线依赖：`forge/libs-m2/` 内置了 Mixin 注解处理器，配合 `libs/` 里的矛模组 jar，
  无需访问被墙的仓库也能编译。

开发期验证矛兼容（把矛的生产 jar 反转成 dev 映射放进 `run/mods`）：

```bash
cd forge
./gradlew copySpearDevJar runServer
```

## 许可

MIT（见 [LICENSE](forge/LICENSE)）。
