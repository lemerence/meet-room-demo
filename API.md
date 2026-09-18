# 会议室预约后端接口文档

本文件只收录已实现且相关测试通过的接口，目前覆盖 T01。
后续接口实施完成并通过测试（包括对应端到端验证）后，同步补充本文件。

## 通用说明

- 本地默认地址：`http://127.0.0.1:8080`。
- 启动、数据库配置与初始化方式见 [README.md](README.md)。
- 当前不需要登录、Token 或权限认证。
- 响应采用 JSON；各接口的成功结构、错误结构及 HTTP 状态以下文为准。

## 接口目录

| 任务 | 方法 | 路径 | 功能 |
| --- | --- | --- | --- |
| T01 | GET | `/api/meeting-rooms` | 查询会议室列表 |

## 手动测试入口

应用启动后，在浏览器打开 `http://127.0.0.1:8080/api/meeting-rooms`，
或在 Postman/Apifox 中选择 GET 并填写该地址，无需认证和请求体。
预置数据下预期 HTTP 200，返回 3 间会议室，容量分别为 6、12、20。
根路径 `/` 没有页面，返回 404 属于预期。

当前后台应用容器名为 `meet-room-app`：

```powershell
docker logs --tail 50 meet-room-app
docker stop meet-room-app
docker start meet-room-app
```

空列表及数据库异常场景使用隔离测试库验证，不要为手动测试清空业务库或删除业务表。

## T02：领域规则测试（没有新增 HTTP 接口）

T02 完成的是 Java 领域对象与规则，不包含 Controller、预约表、预约持久化或请求到达时间采集。
因此不能通过 Postman/Apifox 调用创建、取消预约；当前也不公布尚不存在的预约 URL。
创建预约 HTTP 接口属于 T03，取消预约 HTTP 接口属于 T06。

可在项目根目录 PowerShell 执行以下命令自行验证 T02，不需要数据库凭据：

```powershell
docker run --rm --mount "type=bind,source=$($PWD.Path),target=/workspace" --mount type=volume,source=meet-room-maven-cache,target=/root/.m2 -w /workspace meet-room-maven:3.9.16-java21 mvn -o -B -ntp test "-Dtest=ReservationPeriodTest,MeetingRoomCapacityTest,ReservationPolicyTest,ReservationCancellationTest,TimeConfigurationTest"
```

预期：71 项测试通过、0 失败、0 错误、0 跳过，最终显示 `BUILD SUCCESS`。
也可在 IDE 中运行这些测试类，修改测试输入验证边界，不会操作业务库。

| 测试类 | 场景数 | 验证内容 |
| --- | --- | --- |
| ReservationPeriodTest | 31 | 分钟截取、合法日期、时长、午夜例外、重叠和首尾衔接 |
| MeetingRoomCapacityTest | 6 | 人数必填、正整数和容量边界 |
| ReservationPolicyTest | 20 | 主题、员工 ID、创建资格、请求到达时间和冲突契约 |
| ReservationCancellationTest | 13 | 取消资格、重复取消、审计时间及释放时段 |
| TimeConfigurationTest | 1 | 北京时间 Clock |

T02 的领域错误尚未映射为预约 HTTP 响应；不要将单元测试通过理解为预约接口已上线。

## T01：查询会议室列表

### 请求

`GET /api/meeting-rooms`

无路径参数、查询参数及请求体，无必需的自定义请求头。

```http
GET /api/meeting-rooms HTTP/1.1
Host: 127.0.0.1:8080
Accept: application/json
```

PowerShell 示例：

```powershell
Invoke-RestMethod -Method Get -Uri 'http://127.0.0.1:8080/api/meeting-rooms'
```

### 成功响应

HTTP **200 OK**，响应体为数组，不额外包装 `data` 或 `code`。
返回全部会议室，按 ID 升序排列，不分页。

数组元素字段：

| 字段 | JSON 类型 | 含义 |
| --- | --- | --- |
| `id` | 整数 | 会议室 ID，对应 Java Long |
| `name` | 字符串 | 会议室名称 |
| `capacity` | 整数 | 会议室容量，单位为人，值大于零 |

预置数据示例（实际内容以数据库为准）：

```json
[
  {"id": 1, "name": "第一会议室", "capacity": 6},
  {"id": 2, "name": "第二会议室", "capacity": 12},
  {"id": 3, "name": "第三会议室", "capacity": 20}
]
```

无会议室时仍返回 HTTP **200 OK**：

```json
[]
```

### 错误响应

数据库访问失败时返回 HTTP **503 Service Unavailable**，不会返回空列表冒充查询成功。

```json
{
  "code": "DATABASE_UNAVAILABLE",
  "message": "数据库暂时不可用，请稍后重试"
}
```

| 字段 | JSON 类型 | 含义 |
| --- | --- | --- |
| `code` | 字符串 | 稳定错误码，数据库访问失败为 `DATABASE_UNAVAILABLE` |
| `message` | 字符串 | 面向调用方的错误说明，不包含 SQL、凭据或异常堆栈 |

### 行为与范围

- 此接口只查询数据，不修改会议室，也不判断会议室在某个时段是否空闲。
- 当前不提供会议室新增、修改、删除接口。
- 初始化脚本预置会议室；应用启动不会重复初始化或覆盖已有记录。

### 已完成验证

- 真实 HTTP → 应用 → MySQL：预置会议室字段和列表结果正确。
- 无记录返回 `200` 和 `[]`。
- 隔离测试库故障注入返回 `503` 及上述安全错误结构。
- 重复初始化不会重复插入或覆盖已有会议室名称、容量。
- 正式配置启动应用后查询成功，重启应用后响应一致。

自动化用例：`src/test/java/com/example/meetroom/e2e/MeetingRoomHttpTest.java`。
详细验收记录见 README 的 T01 验收记录。

## 文档维护要求

每个后续接口实现并通过测试后，应补充：方法与路径、参数及约束、
成功响应、错误码和 HTTP 状态、请求响应示例、关键业务边界及验证结果。
修改已有接口时同步修订对应条目，保持文档与代码、测试一致；
未实现或未通过测试的接口不得列为已交付接口。
