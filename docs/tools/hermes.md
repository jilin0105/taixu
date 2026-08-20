# Hermes Agent 兼容性记录

- 适配器：`HermesToolInstaller`
- 官方平台状态：Linux/WSL2 支持 x86_64 与 aarch64；Android Termux 为 aarch64 Tier 2
- 安装方式：Hermes 官方 `install.sh`，使用 `--skip-setup --skip-browser --non-interactive`，代码目录固定为 `/opt/taixu/tools/hermes-agent`，数据目录为 `/opt/taixu/data/hermes-agent`
- 安装前提：Manifest 声明 Python `>=3.11`、curl、CA 证书和 Git；安装脚本会继续处理 uv 等其余依赖
- 验证：`hermes --version`
- 本地 Dashboard：绑定 `127.0.0.1:9119`，由 `LocalServiceLauncher` 等待端口就绪后交给 WebView
- 数据策略：默认卸载只移除 `/opt/taixu/tools/hermes-agent` 和命令入口，保留 `/opt/taixu/data/hermes-agent`；用户勾选删除数据时额外移除该目录。
- 最后核对：2026-08-17

来源：[Hermes 官方平台支持](https://github.com/NousResearch/hermes-agent/blob/main/website/docs/getting-started/platform-support.md)、[官方安装脚本](https://github.com/NousResearch/hermes-agent/blob/main/scripts/install.sh)

正式发布前仍需在目标 Android ARM64 设备上确认 `.[termux]` 相关限制、Dashboard Web extra 和上游许可。
