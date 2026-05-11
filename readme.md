# hm-dianping（黑马点评）

基于黑马程序员 [黑马点评](https://www.bilibili.com/video/BV1NV411u7GE/?spm_id_from=333.337.search-card.all.click) 的 **Spring Boot 后端练习项目**：本地生活 / 商户点评类业务（用户、商户、优惠券、笔记、上传等）。

## 技术栈

| 类别 | 说明 |
|------|------|
| 框架 | Spring Boot 2.7、Spring Web |
| 数据 | MySQL、MyBatis-Plus |
| 缓存 / 会话 | Redis（`StringRedisTemplate`、Hash 存登录态；秒杀使用 **Lua 脚本** 与 **Redis Stream**） |
| 其他 | Hutool、Lombok、AspectJ（`@EnableAspectJAutoProxy`） |
| JDK | 17（见 `pom.xml`） |

## 目录结构

```text
hm-dianping
├── pom.xml                         # Maven 构建配置与依赖声明
├── readme.md                       # 项目说明文档
├── src
│   ├── main
│   │   ├── java/com/hmdp
│   │   │   ├── HmDianPingApplication.java   # Spring Boot 启动类
│   │   │   ├── config                       # MVC、MyBatis-Plus、Redisson、全局异常配置
│   │   │   ├── controller                   # HTTP 接口层
│   │   │   ├── dto                          # 接口入参/响应 DTO
│   │   │   ├── entity                       # 数据库实体，对应 tb_* 表
│   │   │   ├── mapper                       # MyBatis-Plus Mapper
│   │   │   ├── service                      # 业务接口（含 `SeckillOrderConsumer` 异步消费）
│   │   │   ├── service/impl                 # 业务实现
│   │   │   ├── script                       # Redis Lua 封装（如 `SeckillLuaExecutor`）
│   │   │   └── utils                        # Redis 常量、拦截器、ID 生成器、缓存工具等
│   │   └── resources
│   │       ├── application.yaml             # 服务端口、MySQL、Redis、MyBatis-Plus 配置
│   │       ├── db/hmdp.sql                  # 初始化 SQL
│   │       ├── lua/seckill_order.lua        # 秒杀原子预占脚本（库存 + 一人一单 + XADD）
│   │       └── mapper/VoucherMapper.xml     # 优惠券联表查询 SQL
│   └── test/java/com/hmdp                   # SpringBootTest 测试与缓存预热/ID 生成器测试
├── target                          # Maven 构建产物
├── .idea                           # IntelliJ IDEA 配置
└── .vscode                         # VS Code 配置
```

## 功能完成情况（对照当前代码）

### 已实现

- **用户**：手机验证码（Redis）、登录（签发 token 写入 Redis）、**登出（删除 `login:token:{token}`）**、当前用户 `/me`、用户详情扩展信息 `/info/{id}`；请求头 `authorization` + `RefreshInterceptor` / `LoginInterceptor` 双拦截器。
- **商户**：按 id 查询（**逻辑过期** + 互斥锁异步重建，见 `ShopServiceImpl.queryById`）、列表筛选、新增/修改；更新店铺后会删缓存 key。
- **商户类型**：列表查询 + 写入 Redis（具体拼接/命中分支与课程实现可能存在差异，若缓存异常可优先查库逻辑排查）。
- **优惠券**：新增普通券/秒杀券、按店铺查券列表。
- **秒杀下单（Lua + Stream 异步）**：  
  - **同步接口** `POST /voucher-order/seckill/{id}`：预生成 `orderId`，执行 `lua/seckill_order.lua` 原子完成「活动时间内校验 → 一人一单（`seckill:owners:{voucherId}` Set）→ 扣 Redis 库存（`seckill:stock:{voucherId}`）→ `XADD` 写入 `stream.orders`」；成功则 **立即返回 `orderId`**。  
  - **异步落库**：`SeckillOrderConsumer` 以消费组读取 Stream，调用 `createVoucherOrder(userId, voucherId, orderId)`：事务内乐观锁扣 `tb_seckill_voucher` 库存并写入 `tb_voucher_order`（订单主键即上述 `orderId`）。  
  - **Redis 预热**：上架秒杀券时 `VoucherServiceImpl` 写入 `stock`、`seckill:info:{id}`（`begin`/`end` Unix 秒）、并清理 `owners`，避免重复上架脏数据；请求侧 `ensureSeckillRedisPresent` 可在缓存缺失时从库表补齐。  
  - **数据库兜底**：`tb_voucher_order` 对 `(user_id, voucher_id)` 建立唯一索引，与一人一单语义一致。  
  - **运维**：可用 `SeckillStockRedisSyncTest` 将库表库存同步到 Redis `seckill:stock:*`（与 Lua 扣减对齐）。
- **笔记**：发布、点赞数自增、我的笔记、热门笔记（热门列表会按 `user_id` 回填昵称/头像）。
- **上传**：笔记图片上传与删除（本地目录见 `SystemConstants.IMAGE_UPLOAD_DIR`）。

### 未实现 / 仅骨架

- `FollowController`、`BlogCommentsController`：无接口方法，仅有 `RequestMapping` 前缀；对应 `IFollowService`、`IBlogCommentsService` 为 MyBatis-Plus 默认实现，**关注、评论相关业务未接 HTTP**。
- **课程后续章节**（若视频/资料中有）：如 Feed 流、附近商户 Geo、签到等，需对照课程与 `RedisConstants` 中预留 key 自行核对是否落地。

## 当前问题与改进建议

- **配置硬编码**：MySQL 账号密码在 `application.yaml`，Redisson 地址写死在 `RedissonConfig`，图片上传路径写死在 `SystemConstants.IMAGE_UPLOAD_DIR`。建议改为配置项或环境变量，区分本地、测试、生产环境。
- **MySQL 驱动较旧**：当前使用 `mysql-connector-java:5.1.47` 和 `com.mysql.jdbc.Driver`，Java 17 + Spring Boot 2.7 下建议升级 MySQL 8.x 驱动并使用 `com.mysql.cj.jdbc.Driver`。
- **店铺空值缓存判断有缺陷**：`ShopServiceImpl.queryById` 使用 `StrUtil.isEmpty(shopJson)` 判断未命中，空字符串也会被当成未命中，可能削弱缓存空值防穿透效果。更合理的判断是区分 `null` 未命中和 `""` 空值命中。
- **商户类型缓存格式不标准**：当前把多个 JSON 对象直接拼接，再通过正则拆分。建议存 JSON 数组，或使用 Redis List / Hash，避免格式脆弱。
- **秒杀异步一致性**：Redis 已预扣库存且 Stream 已投递，若异步落库失败，可能出现 Redis 与 MySQL 短期不一致；可按业务需要增加失败补偿、重试或人工对账。消费者当前对业务失败也会 ACK，需知悉「不重试失败消息」策略。
- **博客点赞能力较弱**：当前点赞只是 `liked = liked + 1`，没有重复点赞校验、取消点赞，也没有使用 `blog:liked:*` 维护点赞用户集合。
- **热门笔记**：`tb_blog.user_id` 必须在 `tb_user` 中存在，否则回填用户时会 NPE；数据需保持一致。
- **热门笔记存在 N+1 查询**：查询热门博客后逐条查用户，数据量增大后性能会下降。可考虑联表查询、批量查询用户后组装，或适当缓存用户简要信息。
- **上传接口安全性不足**：文件删除接口使用 GET，且文件名主要来自请求参数；建议改为受控路径校验、限制文件类型/大小、使用 POST/DELETE，并避免任意路径删除风险。
- **全局异常返回信息较粗**：`WebExceptionAdvice` 统一返回“服务器异常”，排查问题依赖日志；可以增加业务异常类型和更明确的错误码。

---

## 课程笔记

### day01

[短信登录业务](https://blog.csdn.net/qq_55926096/article/details/144827101)

- 运用了 **session** 技术，记录用户验证码等信息
- 使用 **Redis** 优化 session，解决集群分布问题
- 通过双重 **Interceptor** 进行 token 登录校验

### day02

[商户信息缓存](https://blog.csdn.net/qq_55926096/article/details/145450099)

- 学习了**缓存穿透**，并通过设置空字符串和布隆过滤器来解决
- 了解了**缓存击穿**和**缓存雪崩**，缓存雪崩可以通过分散设置不同的过期时间来防止，而缓存击穿主要有两种方式
  1. 互斥锁，用 Redis 中自带的 setnx 来进行串行等待保证数据的一致性，但影响性能。
  2. 逻辑过期，通过设置一个逻辑时间，到期后分出新线程修改，其余请求访问旧数据，不保证一致性，但性能更好。

### day03

**秒杀券抢购（课程脉络 + 本仓库当前实现）**

1. **全局 ID 生成器**：由时间戳差值 + 当日序列号等组成（Redis 自增），见 `RedisIdWorker`；秒杀在 Lua 调用前即生成 `orderId`，保证接口返回值与 Stream 消息、落库主键一致。
2. **锁与并发控制（课程）**：乐观锁 / 悲观锁 / `synchronized` 局限；集群下可用 **Redisson `RLock`** 做「用户 + 券」维度互斥（课程方案）。  
   **本仓库秒杀路径**：已改为 **不再用 Redisson 抢券**，高并发一致性由 **单段 Lua 脚本** 在 Redis 侧原子完成预占与入队。
3. **Lua 原子预占**（`SeckillLuaExecutor` + `lua/seckill_order.lua`）：同一时间窗校验、一人一单 Set、`DECR` 库存与失败回滚、`XADD` 投递 `stream.orders`，避免多命令竞态。
4. **Redis Stream 异步下单**（`SeckillOrderConsumer`）：消费组阻塞读取，调用 `IVoucherOrderService#createVoucherOrder(userId, voucherId, orderId)` 落库后 ACK，HTTP 线程只做预占与返回 `orderId`。
5. **落库层**：异步阶段仍对 `tb_seckill_voucher` 使用 **`stock > 0` 条件更新**（乐观锁式扣减），订单表辅以 **`(user_id, voucher_id)` 唯一约束**。

## 运行提示

- 配置 MySQL、Redis 连接（`src/main/resources` 下配置文件）。
- 数据表与课程 SQL 一致时使用实体上的 `tb_*` 表名；若自行改表，需保证外键语义与代码一致（如 `tb_blog.user_id` → `tb_user.id`）。
