# ET虚拟机 v3.2.0

**ET协会出品 · 版权所有 © ET**

纯 Java 原生实现的安卓虚拟机。不再依赖网页 / WebView 内核，一切功能真实可用。

- 包名：`com.et.vm`
- 版本：3.2.0（versionCode 320）
- 目标 SDK：34（Android 13 / 14 权限兼容）
- 最小系统：Android 7.0（API 24）
- 官网：https://ETQWFD.github.io/ETVM/

---

## 一、新版本亮点（v3.2.0）

| 项目 | 说明 |
|---|---|
| ROM 商店重构 | **下载实时进度**（进度条 + 百分比 + 已下载/总大小 + 速度，~0.7s 刷新，失败明确报 HTTP 码）；**Android 4.4 → 16 全系目录**，32/64 位标注 |
| 内置双系统 | 随包内置 **Android 4.4 KitKat** + **ET-OS 7.0**（各 ≤6MB、已安装好、不可删除）；创建虚拟机【系统来源】直接选用，**创建即用** |
| 崩溃修复 | 进入首页偶发崩溃（child already has a parent）根治：屏幕切换防重复挂载；内置镜像改后台线程解包 |
| 直进首页 | 去掉欢迎动画，打开 App 直接进入首页（手机版/电脑版一致），启动飞快 |
| 三语言 | 设置内切换 中文 / English / 日本語 |
| 多主题 | 5 套暗色主题：深空黑 / 深海蓝 / 翡翠绿 / 暗夜紫 / 落日橙 |
| 检查更新 | 检测 GitHub 最新 Release → 询问 → 软件内下载 → 申请安装权限 → 直接安装（安卓）；电脑版下载便携包 |
| Windows 版 | 电脑版 ETVM.exe（纯 Java Swing 原生，内置 Java 运行环境，hash.txt 防注入），商店/向导/进度与安卓完全一致 |
| 原生界面 | 欢迎页 / 首页 / 创建向导 / 设置 / 开机 / 桌面 / 应用中心 / ROM 商店 / 连接储存 / 开发者 / 关于，全部 Java 原生实现，无 WebView |
| ROM 商店 | 原生读取 assets 目录 + 实时源检查，**列表刷新不再失败**；4~9 官方镜像（SourceForge 直链）+ 10~16 整理中 + 2 款内置 |
| 实时检测 | 纯 Java 引擎解析 PE / ISO / ZIP，检测 ROM 位数（32 / 64）并判定匹配；APK 导入自动检测 ABI 兼容 |
| 连接储存 | 真机 → 虚拟机单向共享文件夹，虚拟机内可查看、复制文件（无法反向删除真机文件） |
| 防注入 | 运行时校验 APK 签名 SHA-256，被重打包 / 注入即进入受限模式；开发者页展示授权码与签名状态 |
| 悬浮窗 | 桌面悬浮控制球，可拖动；面板提供 主页 / 返回 / 菜单 / 音量 按键 |
| 帧率 | 刷新频率最高 120 帧，默认 60 帧，可自定义 |
| 增强组件 | 创建时可勾选 安装 Google 三件套 / Xposed 框架 / Root 工具 |
| 系统设置 | 可设置虚拟机版本号、开机动画（自定义图片）、机器名等 |

## 二、安装

### 安卓版

1. 从官网 / Release 下载 `ET虚拟机-v3.2.0.apk`。
2. 安装时允许「安装未知来源应用」。
3. 打开 App，欢迎页会展示设备检测与防注入状态。

### Windows 电脑版

1. 下载 `ETVM-Windows-x64.zip`（约 42MB，内置 Java 运行环境）。
2. 解压后双击 `ETVM.exe` 即可运行，无需安装 Java。
3. 数据目录：`C:\Users\<用户名>\ETVM-data\`（shared 共享文件夹 / vm 存储 / roms / vms.json / config.properties）。
4. `hash.txt` 与本程序实时校验完整性，被修改/注入会拒绝运行。

## 三、权限说明

> Windows 电脑版无需系统权限，直接可用。

创建虚拟机向导第 3 步可逐项申请并实时查看授权状态：

| 权限 | 用途 |
|---|---|
| 存储 / 媒体（READ_MEDIA_*、READ/WRITE_EXTERNAL_STORAGE） | 读取 ROM、导入 APK、连接储存共享文件 |
| 麦克风（RECORD_AUDIO） | 虚拟机内录音类应用 |
| 悬浮窗（SYSTEM_ALERT_WINDOW） | 桌面悬浮控制球（需跳转系统设置手动开启） |
| 安装应用（REQUEST_INSTALL_PACKAGES） | 从真机导出 APK 安装进虚拟机 |
| 通知（POST_NOTIFICATIONS） | ROM 下载完成等系统通知 |

## 四、使用流程

1. **首页** → 点击「创建虚拟机」。
2. **向导**：
   - 第 1 步：名称、系统位数（32 / 64）、系统版本（内置 ET-OS 7.0 / 商店 ROM / 自定义）。
   - 第 2 步：上传自己的 ROM 镜像（支持 .zip / .img / .iso，仅支持安卓），上传后**实时检测位数与匹配度**；也可从 ROM 商店选择。
   - 第 3 步：权限申请（逐项授权，实时状态）。
   - 第 4 步：帧率（默认 60，最高 120）、开机动画（自定义图片）、增强组件勾选、虚拟机版本号 → 点击「正式创建」。
3. **创建完成**：返回首页，下方出现虚拟机卡片。
4. **点击启动**：首次启动会安装 ROM（内置 ET-OS 7.0 即装即用），之后直接进入系统桌面。
5. **桌面使用**：
   - 系统自带：ET 浏览器 / 连接储存 / 系统设置 / 相机 / 相册。
   - 应用中心：从真机导出 APK，自动检测位数兼容性后安装。
   - 连接储存：真机发送文件到共享目录，虚拟机内查看 / 复制。
   - 悬浮窗：右下角悬浮球控制（主页 / 返回 / 菜单 / 音量）。
6. **设置**（首页卡片上的「设置」）：刷新频率、开机动画、版本号、增强组件开关。

## 五、ROM 商店

- 内置镜像（SourceForge 官方源，32 位，均 ≤400MB）：
  - Android 4.4 KitKat（android-x86 4.4-r1）343MB
  - Android 5.1 Lollipop（android-x86 5.1-rc1）358MB
- 下载完成后点「使用此 ROM」→ 自动带入创建向导并预置位数。
- 「刷新列表」为原生读取，**不会失败**。

## 六、连接储存（单向共享）

- 共享目录：`Android/data/com.et.vm/files/shared/`（真机侧）
- 真机把文件放入该目录 → 虚拟机内「连接储存」即可查看 / 复制。
- 虚拟机**只能读取与复制**，不能删除 / 覆盖真机文件。

## 七、防注入与密钥

详见 [`SECURITY.md`](SECURITY.md)。

- 运行时签名校验：APK 签名 SHA-256 与内置白名单比对。
- 授权码：`ET-XXXX-XXXX-XXXX-XXXX`（SHA-256 派生）。
- 签名证书：`CN=ET, OU=ET协会, O=ET, L=Meizhou, ST=Guangdong, C=CN`。

## 八、常见问题

| 问题 | 处理 |
|---|---|
| 列表刷新失败 | v3.0.0 已修复（原生读取 assets），点击刷新即可 |
| 权限弹不出来 | Android 13+ 请逐项在向导内申请；悬浮窗需手动开启（App 一键跳转设置） |
| 无法启动虚拟机 | v3.0.0 原生重写已移除 WebView 依赖；确认已授予存储权限 |
| 微信打不开官网 | 官网为 GitHub Pages HTTPS（*.github.io），微信内可直接打开；异常时用系统浏览器打开一次 |
| 上传 Windows 镜像 | 检测引擎会识别并拒绝，仅支持安卓系统 |
| ROM 大于 400MB | 商店镜像均 ≤400MB；自上传镜像不限大小，但建议精简 |

## 九、构建（开发者）

```bash
# 依赖：Android SDK build-tools 34、JDK 17、python3、launch4j
bash build.sh          # 安卓 APK：ET虚拟机-v3.2.0.apk
javac -encoding UTF-8 -cp libs/json.jar -d pc-build src-pc/com/et/vm/ETVMPC.java src/com/et/vm/RomDetect.java src/com/et/vm/License.java
jar cfm pc-dist/ETVM-pc.jar pc-manifest.mf -C pc-build .
java -jar launch4j.jar pc-dist/l4j.xml   # 生成 ETVM.exe
# JVM 单测（10 项）：javac -cp build/jvmtest -d build/jvmtest test/jvm/UnitTest.java && java -cp build/jvmtest UnitTest
```

## 十、版权

本项目由 **ET协会** 出品，版权归 **ET** 所有。禁止未授权重打包、二次分发或用于不合规用途。
