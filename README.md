# meet-room-demo

会议室预约后端。当前包含 T00 工程基础、T01 会议室列表查询和 T02 预约领域规则；尚无预约 HTTP 接口或预约持久化。

完整接口契约见根目录 [API.md](API.md)，后续接口实现并通过测试后同步更新。

## 环境与结构

- Java 21、Spring Boot 3.3.5、MyBatis-Plus 3.5.9。
- MySQL 8.4.11、Docker Maven 3.9.16（Java 21）。
- 根包 `com.example.meetroom`：`interfaces`、`application`、`domain`、`infrastructure`。
- `src/test` 为测试，`sql` 为后续业务初始化脚本；开发约束见 [AGENTS.md](AGENTS.md)。

## 本地配置

以下命令在项目根目录的 PowerShell 执行，需要 Docker Desktop 正常运行。

```powershell
docker start meet-room-mysql
```

现有 MySQL 仅映射 `127.0.0.1:3306`，数据卷为 `meet-room-mysql-data`。
业务库与账号是 `meet_room`；隔离测试库与账号是 `meet_room_test`。
测试账号仅授予测试库权限，不能访问业务库。

本机已有 `.env.local` 和 `.env.test`，它们包含本地密码且已被 Git 忽略。
不要打印、提交或分享这两个文件。`.env.example` 仅展示配置键，不包含密码。
Spring 不会自动加载 `.env` 文件，下面的 Docker 命令通过 `--env-file` 传入。

| 配置 | 用途 |
| --- | --- |
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | 应用连接配置 |
| `TEST_DB_HOST`、`TEST_DB_PORT`、`TEST_DB_PASSWORD` | 测试连接配置；不回退到业务凭据 |

Docker 内使用 `host.docker.internal:3306`；本机 Java/IDE 运行时将主机改为 `127.0.0.1`，并显式配置环境变量。
业务与测试 JDBC URL 均指定 `connectionTimeZone=Asia/Shanghai`。

新环境需由数据库管理员创建 `meet_room` 和 `meet_room_test` 两个库，分别创建同名账号，
只授予各自库的权限，再按示例建立本地环境文件。应用不自动建库或初始化业务表。

## 初始化会议室

在现有本地 MySQL 容器的业务库执行以下命令（不会清表或覆盖已有会议室）：

```powershell
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
foreach ($file in @('sql/schema.sql', 'sql/data.sql')) {
    Get-Content -Raw -Encoding utf8 $file | docker exec -i meet-room-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) { throw "初始化失败：$file" }
}
```

预置：第一会议室（6 人）、第二会议室（12 人）、第三会议室（20 人）。
重复执行只补入缺少的预置 ID，不覆盖已有记录。应用重启不执行这些 SQL。
上述命令使用当前开发容器已有的应用账号环境变量，不输出密码。

## 构建与测试

本机 Maven 镜像：`meet-room-maven:3.9.16-java21`。命名卷 `meet-room-maven-cache` 缓存已批准的依赖。

```powershell
docker run --rm --mount "type=bind,source=$($PWD.Path),target=/workspace" --mount type=volume,source=meet-room-maven-cache,target=/root/.m2 -w /workspace --env-file .env.test meet-room-maven:3.9.16-java21 mvn -B -ntp clean package
```

只运行测试：将最后的 `clean package` 改为 `test`。
测试需要真实 MySQL，缺少测试凭据或连接失败时应失败，不静默跳过。
测试通过随机端口启动真实应用，验证数据库连接、实际目标库、MyBatis 集成和测试账号隔离。
T01 的 `MeetingRoomHttpTest` 通过真实 HTTP 访问 MySQL；在核对库名和账号后，
仅创建、清理隔离测试库中的 `meeting_room` 表。测试包含故障注入，不得在业务库运行。
不要对同一测试库同时运行多份测试。

检查关键依赖版本：

```powershell
docker run --rm --mount "type=bind,source=$($PWD.Path),target=/workspace" --mount type=volume,source=meet-room-maven-cache,target=/root/.m2 -w /workspace meet-room-maven:3.9.16-java21 mvn -B -ntp dependency:tree "-Dincludes=org.springframework.boot:*,org.mybatis:*,com.baomidou:*,com.mysql:*"
```

## 启动与停止

```powershell
docker run --rm --name meet-room-app -p 127.0.0.1:8080:8080 --mount "type=bind,source=$($PWD.Path),target=/workspace" --mount type=volume,source=meet-room-maven-cache,target=/root/.m2 -w /workspace --env-file .env.local meet-room-maven:3.9.16-java21 mvn -B -ntp spring-boot:run
```

或在完成打包后，使用同一 Java 21 镜像直接运行 JAR，避免再次解析 Maven 插件：

```powershell
docker run --rm --name meet-room-app -p 127.0.0.1:8080:8080 --mount "type=bind,source=$($PWD.Path),target=/workspace" -w /workspace --env-file .env.local meet-room-maven:3.9.16-java21 java -jar target/meet-room-demo-0.0.1-SNAPSHOT.jar
```

另一个终端执行 `docker stop meet-room-app` 可停止应用，MySQL 不受影响。
根路径 `http://127.0.0.1:8080/` 没有路由，返回 404；会议室接口如下。

## 会议室列表接口

`GET /api/meeting-rooms`，无请求参数，HTTP 200 返回 JSON 数组，按 ID 升序：

```json
[
  {"id": 1, "name": "第一会议室", "capacity": 6},
  {"id": 2, "name": "第二会议室", "capacity": 12},
  {"id": 3, "name": "第三会议室", "capacity": 20}
]
```

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/meeting-rooms
```

没有会议室时返回 `[]`。数据库访问失败返回 HTTP 503：

```json
{"code":"DATABASE_UNAVAILABLE","message":"数据库暂时不可用，请稍后重试"}
```

不会把数据库故障伪装成空列表，也不返回 SQL 或异常堆栈。
本阶段不提供会议室新增、修改、删除接口。

## 后续业务约束

北京时间；预约 `yyyy-MM-dd HH:mm`，审计 `yyyy-MM-dd HH:mm:ss`。
预约秒及更低精度直接截去；时间资格以服务端采集的请求到达时间判断。
T02 已在领域层实现时间解析和预约规则；请求时间过滤器、预约事务锁、预约表及 HTTP 接口尚未实现。

## T02 领域规则与独立单元测试

| 模块 | 职责 |
| --- | --- |
| `ReservationPeriod` | 严格解析合法日期，截去秒和小数，校验时间顺序、4 小时、跨天与区间重叠 |
| `MeetingRoom.validateAttendance` | 正整数人数及容量上限 |
| `ReservationPolicy` | 会议室存在性、请求到达时间资格及跨预约冲突协调 |
| `Reservation` | 主题、员工 ID、人数等自身约束，ACTIVE/CANCELLED 状态与取消审计 |
| `ReservationConflictRepository` | 仅定义同一会议室 ACTIVE 区间冲突查询契约，无数据库实现 |
| `TimeConfiguration` | 提供可注入的北京时间 Clock，领域层本身不读取系统当前时间 |

`ReservationPolicy.create` 接收独立的请求到达时间和实际创建操作时间。
`Reservation.cancel` 同样区分请求到达时间和实际取消操作时间。
请求到达时间不截为分钟；审计时间截到秒。线程等待后不得替换请求时间。
新建领域预约尚未持久化，ID 为 null；ID 分配及数据库重建对象在持久化任务处理。
员工 ID 在领域内作为不透明字符串，仅校验非空，不增加数字格式或员工存在性约束。

所有创建用例应通过 `ReservationPolicy` 协调；`Reservation.newActive` 仅保证对象自身约束，
不能替代会议室容量、请求时点和跨预约冲突检查。
未来数据库实现必须在会议室锁保护的事务内调用冲突检查，当前单元测试不能证明数据库并发安全。

仅运行 T02 单元测试，不需要数据库或任何凭据：

```powershell
docker run --rm --mount "type=bind,source=$($PWD.Path),target=/workspace" --mount type=volume,source=meet-room-maven-cache,target=/root/.m2 -w /workspace meet-room-maven:3.9.16-java21 mvn -o -B -ntp test "-Dtest=ReservationPeriodTest,MeetingRoomCapacityTest,ReservationPolicyTest,ReservationCancellationTest,TimeConfigurationTest"
```

其中 `-o` 使用 T00 已下载的缓存；新环境首次准备依赖时可移除 `-o`，依赖版本仍以 pom.xml 为准。
T02 不新增接口，所以 API.md 仍只收录 T01；不能将领域工厂当作已交付 HTTP 接口。

## T00 验证记录

- `clean package` 成功，3 项数据库集成测试通过，无失败或跳过。
- 业务账号真实 JDBC `SELECT 1` 成功；测试账号连接 `meet_room_test`，无法看到业务库。
- 依赖树确认 Boot 3.3.5、MyBatis-Plus 3.5.9、MyBatis-Spring 3.0.4；无重复 MyBatis Starter。
- Maven 启动及独立 JAR 启动均完成真实 HTTP 检查，根路径返回预期 404；验证容器已停止。
- 本地凭据文件已忽略，待纳入版本控制的文件未包含实际密码。
- T00 时无业务 Mapper；T01 已加入会议室 Mapper。Java 21 测试时仍可能出现 Byte Buddy 动态代理提示。
- T00 时尚未进行业务接口端到端测试；T01 结果见下。业务并发和预约持久化仍属于后续任务。

## T01 验收记录

前置检查：T00 的 3 项测试离线复跑通过；沿用全部既有依赖，未增加或升级依赖。
测试先行：接口实现前，4 项新增 HTTP 测试均因预期状态与 404 不符而失败；实现后全部通过。

| 验收项 | 结果 |
| --- | --- |
| HTTP 返回预置会议室 ID、名称、容量 | 通过；3 条记录，容量分别 6、12、20 |
| 无会议室返回 HTTP 200 和空数组 | 通过；在隔离测试库验证 |
| 重复执行 SQL 不重复插入、不覆盖已有名称和容量 | 通过；测试中先修改已有值再重复初始化 |
| 数据库失败返回安全错误而非空列表 | 通过；隔离库故障注入返回 503 和明确错误码，无 SQL/堆栈 |
| 正式配置启动打包 JAR，执行真实 HTTP 查询 | 通过；`GET /api/meeting-rooms` 返回 200 |
| 应用重启后再次 HTTP 查询 | 通过；响应与重启前完全一致，业务库仍为 3 条会议室 |
| T00 回归及完整打包 | 通过；`mvn -o -B -ntp clean package`，7 项测试，无失败或跳过 |

验证容器已停止，MySQL 保持运行。业务库仅新增会议室表，未添加会议室管理或预约功能。
T01 没有新的复杂纯领域行为，使用真实 HTTP/数据库测试验证查询链路，不添加只测试转发调用的单元测试。
预约规则、预约并发及预约重启持久化在后续任务验证。

## T02 验收记录（2026-09-18）

前置检查：T00/T01 的 7 项测试复跑通过；未新增或升级依赖。
各批规则先写测试，在可编译骨架上观察失败，再实现并通过对应测试；UTC 配置也经固定时刻测试检出后改为北京时间。

| 单元测试模块 | 场景数 | 验收内容 |
| --- | --- | --- |
| ReservationPeriodTest | 31 | 严格日期格式、秒及小数截取、相等/倒序、4 小时边界、午夜例外、重叠及双向首尾衔接 |
| MeetingRoomCapacityTest | 6 | 人数必填、正整数、容量等值与超限 |
| ReservationPolicyTest | 20 | 会议室存在、员工必填、主题空白与长度、人数、未来开始、到达时间与审计分离、冲突契约 |
| ReservationCancellationTest | 13 | 开始前/恰好开始/进行中/结束、等锁跨过开始时间、重复取消优先、审计不改写、记录保留及释放时段 |
| TimeConfigurationTest | 1 | 注入 Clock 的北京时间跨日期转换，保留请求判断所需精度 |

独立单元测试 71 项全部通过，无需数据库凭据。
完整 `mvn -o -B -ntp clean package` 共 78 项测试通过，无失败、错误或跳过，JAR 打包成功。
其中 4 项 T01 真实 HTTP → 应用 → MySQL 测试和 3 项 T00 数据库集成测试均通过回归。

范围确认：未创建预约表、预约数据库 Mapper、创建/取消 HTTP 接口、请求到达时间过滤器或事务锁。
`ReservationConflictRepository` 仅为契约；数据库并发互斥、预约持久化及预约 HTTP 行为尚未验证，属于后续任务。
API.md 不添加未实现接口。本次停止在 T02，不进入 T03。
