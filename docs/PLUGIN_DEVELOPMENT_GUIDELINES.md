# TaiXu 插件开发准则

本文档定义 TaiXu 插件清单、离线插件包和运行时安装脚本的统一约定。

## 1. 插件包格式

离线插件使用 ZIP 容器，文件扩展名为 `.txplugin`：

```text
my-plugin.txplugin
├── manifest.json
└── payload/
    ├── archives/       # SDK、Gradle、Flutter、NDK 等大文件
    ├── scripts/        # 安装时需要执行的脚本或配置
    └── bin/            # 可直接部署的 ARM64 可执行文件
```

包内只允许 `manifest.json` 和 `payload/`，路径不得使用绝对路径或 `..`。导入后包保存在应用私有目录，安装时复制到当前发行版的 `/opt/taixu/imports/<id>`。

## 2. manifest.json

最小示例：

```json
{
  "schemaVersion": 1,
  "id": "flutter-offline",
  "name": "Flutter Offline",
  "description": "ARM64 Flutter SDK 离线插件",
  "version": "3.47.1",
  "publisher": "Example",
  "category": "DEVELOPER",
  "launchType": "command",
  "architectures": ["ARM64"],
  "source": "LOCAL",
  "offlineOnly": true,
  "installMethod": "LOCAL_PACKAGE",
  "installSteps": [
    "test -f \"$TAIXU_PLUGIN_PAYLOAD/archives/flutter.tar.xz\"",
    "mkdir -p /opt/flutter && tar -xJf \"$TAIXU_PLUGIN_PAYLOAD/archives/flutter.tar.xz\" -C /opt",
    "ln -sf /opt/flutter/bin/flutter /opt/taixu/bin/flutter"
  ],
  "commandLinks": ["flutter"],
  "verifyCommand": "flutter --version"
}
```

`id` 只能使用小写字母、数字和连字符；`architectures` 必须包含 `ARM64`。在线插件使用 `source=REMOTE`、`installMethod=SCRIPT`。本地插件由导入器强制改写为 `source=LOCAL`、`offlineOnly=true`、`installMethod=LOCAL_PACKAGE`。

## 3. 依赖与离线策略

- 大型依赖必须放进 `payload/`，不能在安装脚本中再次下载。
- 离线插件安装时不会调用 RuntimeDependency 下载器；`installSteps` 应只读取 `$TAIXU_PLUGIN_PAYLOAD`。
- Android、Flutter、Gradle、NDK 等依赖必须提供 ARM64 版本，并在安装脚本中校验压缩包摘要、目录结构和关键可执行文件。
- 不要把 x86_64 主机二进制伪装成 ARM64；必要时用 ELF 架构检查。
- 安装脚本应幂等：重复执行不会破坏已有安装，也不会覆盖用户数据目录。

## 4. 安装脚本安全规范

- 所有路径使用固定目录或环境变量，禁止把用户输入直接拼接到 Shell 命令。
- 独立脚本必须以 `#!/bin/sh` 开头并使用 LF 换行；清单中调用脚本时统一写 `/bin/sh "$TAIXU_PLUGIN_PAYLOAD/..."`，不要依赖 `PATH` 中的相对 `sh`。
- 可执行文件放在插件目录的 `bin/`，通过 `commandLinks` 暴露到 `/opt/taixu/bin`。
- 服务插件必须声明 `servicePort` 和 `servicePath`，只监听必要地址。
- 插件只申请实际需要的 `permissions`：`NETWORK`、`WORKSPACE_READ`、`WORKSPACE_WRITE`、`LOCAL_WEB`。
- 禁止修改 RootFS 外部路径、系统级 Android 文件或其他插件目录。

## 5. 验证与发布清单

发布前至少验证：

1. 在 ARM64 Debian/Ubuntu PRoot 中全新安装和重复安装。
2. 断网时安装仍能完成，且不会访问包管理器或远程 URL。
3. `verifyCommand`、命令链接和服务启动均成功。
4. 恶意 ZIP 路径、缺少 `manifest.json`、错误架构和损坏归档会被拒绝。
5. 安装、验证、卸载日志能准确说明失败步骤。

## 6. 版本与兼容性

`schemaVersion` 当前为 `1`。破坏性清单变更必须递增 Schema；普通资源更新递增 `version`，并保持 `id` 不变。插件不得依赖未在本准则或 TaiXu API 中声明的内部路径。

## 7. 从零制作一个插件

下面以 `hello-arm64` 为例。这个插件把 `hello` 脚本安装到插件目录，并通过 TaiXu 的命令链接暴露出来。

### 7.1 创建工作目录

```text
hello-arm64/
├── manifest.json
└── payload/
    └── bin/
        └── hello
```

`payload/bin/hello` 内容：

```sh
#!/bin/sh
echo "hello from TaiXu"
```

在 Linux/macOS 上执行 `chmod +x payload/bin/hello`。Windows 上不能依赖 ZIP 保存 Unix 执行位，因此安装步骤必须再次执行 `chmod +x`。

### 7.2 编写清单

`manifest.json`：

```json
{
  "schemaVersion": 1,
  "id": "hello-arm64",
  "name": "Hello ARM64",
  "description": "TaiXu 本地插件示例",
  "version": "1.0.0",
  "publisher": "Your Name",
  "category": "DEVELOPER",
  "launchType": "command",
  "architectures": ["ARM64"],
  "permissions": [],
  "updateStrategy": "REINSTALL",
  "source": "LOCAL",
  "offlineOnly": true,
  "installMethod": "LOCAL_PACKAGE",
  "installSteps": [
    "test -f \"$TAIXU_PLUGIN_PAYLOAD/bin/hello\"",
    "mkdir -p \"$TAIXU_TOOL_DIR/bin\"",
    "cp \"$TAIXU_PLUGIN_PAYLOAD/bin/hello\" \"$TAIXU_TOOL_DIR/bin/hello\"",
    "chmod +x \"$TAIXU_TOOL_DIR/bin/hello\""
  ],
  "uninstallSteps": [
    "rm -f \"$TAIXU_TOOL_DIR/bin/hello\""
  ],
  "launchCommand": "hello",
  "verifyCommand": "hello",
  "commandLinks": ["hello"]
}
```

关键字段的作用：

| 字段 | 说明 |
| --- | --- |
| `id` | 稳定唯一标识，只能是小写字母、数字和连字符，长度 2~64。发布后不要修改。 |
| `version` | 插件版本；同一 `id` 导入新版本时会替换旧版本。 |
| `launchType` | `command` 适合命令行工具；交互式终端用 `pty`；后台 Web 服务用 `web` 或 `service`。 |
| `architectures` | 必须包含 `ARM64`，因为当前应用只支持 arm64-v8a。 |
| `installSteps` | 按顺序拼接成 Shell 脚本，在 PRoot Linux 中执行。 |
| `uninstallSteps` | 删除插件创建的文件；用户数据应放在 `$TAIXU_TOOL_DATA`，不要在这里无条件删除。 |
| `launchCommand` | 用户从工具中心启动时执行的命令。 |
| `verifyCommand` | 安装完成后的自检命令，退出码为 0 才算通过。 |
| `commandLinks` | 要暴露到 `/opt/taixu/bin` 的命令名列表。 |

安装器会自动提供以下环境变量：

```text
$TAIXU_TOOL_ID       当前插件 ID
$TAIXU_TOOL_DIR      /opt/taixu/tools/<id>
$TAIXU_TOOL_DATA     /opt/taixu/data/<id>
$TAIXU_PLUGIN_PAYLOAD /opt/taixu/imports/<id>（仅本地插件）
```

### 7.3 打包为 `.txplugin`

Windows PowerShell：

```powershell
Compress-Archive -Path .\hello-arm64\manifest.json, .\hello-arm64\payload `
  -DestinationPath .\hello-arm64.txplugin -Force
```

Linux/macOS：

```sh
cd hello-arm64
zip -r ../hello-arm64.txplugin manifest.json payload
```

打包后检查 ZIP 根目录，必须直接看到 `manifest.json` 和 `payload/`，不能多一层 `hello-arm64/` 目录：

```sh
unzip -l hello-arm64.txplugin
```

### 7.4 在 TaiXu 中导入和安装

1. 打开“设置 → 插件与工具中心”。
2. 点击右上角的文件导入按钮，选择 `.txplugin`。
3. 导入成功后，工具会出现在工具列表中；如果与在线 Registry 中的 `id` 相同，本地版本优先显示。
4. 点击“安装”。安装器会先把 `payload/` 复制到当前发行版的 `/opt/taixu/imports/<id>`，再执行 `installSteps`。
5. 安装结束后运行 `verifyCommand`，确认状态变为已安装。

### 7.5 在终端中验证

```sh
command -v hello
hello
test -x /opt/taixu/tools/hello-arm64/bin/hello
```

在工具详情页还应验证“启动终端”“卸载”两个操作。卸载后检查：

```sh
test ! -e /opt/taixu/tools/hello-arm64/bin/hello
```

## 8. 制作带大型依赖的离线插件

以 Flutter/Android/Gradle 为例，推荐把归档放入 `payload/archives/`，而不是把二进制散落在 payload 根目录：

```text
flutter-offline/
├── manifest.json
└── payload/
    ├── archives/
    │   ├── flutter-arm64.tar.xz
    │   ├── gradle-8.14.2-bin.zip
    │   └── android-platform-34.zip
    └── scripts/
        └── install-flutter.sh
```

安装脚本应先检查文件存在，再校验摘要，最后解压到固定目录：

```sh
set -eu
archive="$TAIXU_PLUGIN_PAYLOAD/archives/flutter-arm64.tar.xz"
test -s "$archive"
echo "<SHA256>  $archive" | sha256sum -c -
rm -rf /opt/flutter.staging
mkdir -p /opt/flutter.staging
tar -xJf "$archive" -C /opt/flutter.staging
test -x /opt/flutter.staging/flutter/bin/flutter
rm -rf /opt/flutter
mv /opt/flutter.staging/flutter /opt/flutter
ln -sf /opt/flutter/bin/flutter /opt/taixu/bin/flutter
```

注意：

- `offlineOnly=true` 时，安装器不会调用 Node/Python/Curl 等在线 Runtime 依赖获取逻辑；归档和运行时必须全部随包提供，或依赖已经存在于 RootFS。
- Flutter、Android SDK、Gradle、NDK 的主机工具必须是 Linux ARM64 可运行版本；Google 官方常见的 x86_64 工具不能直接使用。
- 对 ELF 文件执行 `readelf -h file | grep -E 'Machine|AArch64'`，对脚本和 JAR 分别检查可执行位和文件完整性。
- 不要把大型归档复制到 `$TAIXU_TOOL_DATA`；该目录用于用户数据，卸载时默认保留。

## 9. Web 服务插件示例

Web 服务需要额外声明端口和服务路径：

```json
{
  "id": "my-dashboard",
  "name": "My Dashboard",
  "description": "本地 Web 服务",
  "version": "1.0.0",
  "launchType": "web",
  "servicePort": 8787,
  "servicePath": "/",
  "architectures": ["ARM64"],
  "permissions": ["LOCAL_WEB", "WORKSPACE_READ"],
  "source": "LOCAL",
  "offlineOnly": true,
  "installMethod": "LOCAL_PACKAGE",
  "installSteps": ["cp \"$TAIXU_PLUGIN_PAYLOAD/bin/dashboard\" \"$TAIXU_TOOL_DIR/bin/dashboard\"", "chmod +x \"$TAIXU_TOOL_DIR/bin/dashboard\""],
  "launchCommand": "dashboard --host 0.0.0.0 --port 8787",
  "verifyCommand": "dashboard --version",
  "commandLinks": ["dashboard"]
}
```

只申请实际需要的权限；不要为了方便声明 `NETWORK` 或 `WORKSPACE_WRITE`。服务必须支持非交互启动，并从环境变量或固定参数读取端口，避免启动后等待输入。

## 10. 常见错误

### 导入后列表没有插件

检查 ZIP 根目录是否正确、`manifest.json` 是否是有效 JSON、`id` 是否符合正则、`architectures` 是否包含 `ARM64`。

### 提示 payload 不存在

确认 `payload/` 在 ZIP 内且没有多余目录层级；安装脚本引用的是 `$TAIXU_PLUGIN_PAYLOAD/...`，不是 Android 主机路径。

### 安装成功但命令找不到

确认 `commandLinks` 与实际生成的 `$TAIXU_TOOL_DIR/bin/<name>` 一致，并在脚本中执行 `chmod +x`。验证命令应使用命令链接名。

### ARM64 程序无法启动

用 `readelf -h` 检查架构；不要将 Android APK 内的 x86_64 工具或桌面 Linux x86_64 SDK 直接放进插件包。

### 重复安装破坏用户数据

将缓存、配置、模型和项目数据写入 `$TAIXU_TOOL_DATA`，安装目录 `$TAIXU_TOOL_DIR` 只放可重建的程序文件。升级时使用临时目录解压，校验通过后再原子替换。

## 11. 发布前最终检查

```text
[ ] zip 列表只有 manifest.json 与 payload/
[ ] manifest.json 可以被标准 JSON 解析器读取
[ ] id、version、ARM64、launchType、权限字段正确
[ ] 所有大文件均有 SHA-256 校验
[ ] 断网可完成安装、验证和启动
[ ] 全新安装、重复安装、升级、卸载均测试过
[ ] 命令链接、PTY/服务启动和日志均正常
[ ] 没有 ../、绝对路径、RootFS 外路径或未声明权限
```
