# SDK Spike 结论（Windows x64，Java Desktop SDK 1.0.2）

日期：2026-11-01。测试 Key 仅通过 `XLITE_TEST_API_KEY` 环境变量注入，未写入源码或本文件。

## 已确认

1. **登录 token**：`XLDownloadAPI.getLoginToken(key, 86400, ["xtask.p2sp","xindex.query"])` 成功。错误 Key 返回 `code=4002`，消息“无效的 API key”。
2. **App ID**：SDK `init()` 的第一个参数必须是 JWT `aud` 声明的 **Base64 字符串本身**，而不是解码后的明文。
   - 测试 token 的 `aud` 为 `eGwtNm1WdjEyNTg0AAAAAygxAAA=`；Base64 解码后是 `xl-6mVv12584\0...` 二进制。
   - 传 `xl-6mVv12584` → `init=9116 (APPNAME_APPKEY_ERROR)`。
   - 传 `eGwtNm1WdjEyNTg0AAAAAygxAAA=` → `init=0`、`login=0`（session 241 字符）。
   - 官方 C++ 示例也直接使用该 Base64 字符串作为 `app_id`。`AppIdResolver` 已按此修正，并保留设置页高级 App ID 覆盖。
3. **SDK 版本**：`getVersion() = 12.0122.960.7-faf4e5b7d6bcbbd2f275e624b38faa202fc1a195`（主包 1.0.2）。
4. **下载**：官方测试文件（https://down.sandai.net/thunder11/XunLeiWebSetup25.0.90.1592xl11.exe，15,236,256 字节）与 Cloudflare 1 MiB 测试文件均完成，`stateCode=8`，落盘大小与 `downloadedSize` 一致。
5. **不限速**：`setDownloadSpeedLimit(-1)` 为不限速；传 `0` 会被当作最小限速 32 KB/s（实测稳定在 32–39 KB/s）。`XunleiEngine` 已把 `0` 映射为 `-1`。
6. **任务持久化**：同一 `cfgPath` 下，进程退出后 `getFinishedTasks()` 仍返回已完成任务（taskId 59808461），ID 稳定。`getUnfinishedTasks()` 可用于恢复未完成任务。
7. **任务信息**：`getTaskInfo(id, name)` 返回 JSON 对象字符串，而非裸值：
   - `url` → `{"url":"https://..."}`
   - `save_path` → `{"save_path":"D:\\..."}`
   - `save_name` → `{"save_name":"..."}`
   `XunleiEngine.taskInfoValue()` 负责解析，导入 SDK 任务时必须使用解析后的值。
8. **删除**：`deleteTask(id, true)` 可删除任务与文件；清理 3 个测试任务均返回 0。

## 主机兼容性观察

- 同一 URL 用 curl 可访问，但 SDK 对 `mirrors.tuna.tsinghua.edu.cn` 持续 60 秒无数据，最终 `111136 (ERROR_NO_DATA_PIPE)`；官方下载站与 Cloudflare 正常。
- 结论：错误 111136 可能由目标主机/网络链路引起，UI 提示应建议重试或换源，不宣称所有 HTTP(S) 主机都可下载。

## 尚未实测

- 未完成任务的跨进程恢复与继续（`desired=RUNNING`）——需要时用同一 cfg 启动长任务后强制结束再校验；SDK 提供 `saveTasks=true` 与 `getUnfinishedTasks`。
- 失败任务 `startTask` 重试语义（引擎在失败时回退为同 URL/目录/名称重建任务）。
- 并发队列状态（3=start_waiting，4=start_pending）在 1/3 并发下的推进。
- 动态链接加速对文件一致性的官方警告（已默认开启，设置页提示）。
