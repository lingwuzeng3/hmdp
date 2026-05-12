# hm-dianping（黑马点评）

基于黑马程序员 [黑马点评](https://www.bilibili.com/video/BV1NV411u7GE/?spm_id_from=333.337.search-card.all.click) 的 **Spring Boot 后端练习项目**：本地生活 / 商户点评类业务（用户、商户、优惠券、笔记、上传等）。

## 技术栈

| 类别 | 说明 |
|------|------|
| 框架 | Spring Boot 2.7、Spring Web |
| 数据 | MySQL（`mysql-connector-j` 8.x、`com.mysql.cj.jdbc.Driver`）、MyBatis-Plus |
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
- **笔记**：发布、点赞数自增、我的笔记、热门笔记（热门列表会按 `user_id` 回填昵称/头像）；**关注动态** `GET /blog/of/follow`（Feed 收件箱，见课程笔记 day05）。
- **上传**：笔记图片上传与删除（本地根目录见 `SystemConstants.IMAGE_UPLOAD_DIR`）。
- **好友关注**：关注/取关、是否关注、共同关注（`FollowController` + `FollowServiceImpl`，Redis Set + `tb_follow` 双写）；与笔记发布联动 Feed（`BlogServiceImpl#saveBlog`）。

### 未实现 / 仅骨架

- **课程其他扩展**：附近商户 Geo、签到等，可对照 `RedisConstants`（如 `SHOP_GEO_KEY`、`USER_SIGN_KEY`）与代码自行核对是否落地。

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

**秒杀券抢购**

1. **全局 ID 生成器**：由时间戳差值 + 当日序列号等组成（Redis 自增），见 `RedisIdWorker`；秒杀在 Lua 调用前即生成 `orderId`，保证接口返回值与 Stream 消息、落库主键一致。
2. **锁与并发控制（课程）**：乐观锁 / 悲观锁 / `synchronized` 局限；集群下可用 **Redisson `RLock`** 做「用户 + 券」维度互斥（课程方案）。  
   **本仓库秒杀路径**：已改为 **不再用 Redisson 抢券**，高并发一致性由 **单段 Lua 脚本** 在 Redis 侧原子完成预占与入队。
3. **Lua 原子预占**（`SeckillLuaExecutor` + `lua/seckill_order.lua`）：同一时间窗校验、一人一单 Set、`DECR` 库存与失败回滚、`XADD` 投递 `stream.orders`，避免多命令竞态。
4. **Redis Stream 异步下单**（`SeckillOrderConsumer`）：消费组阻塞读取，调用 `IVoucherOrderService#createVoucherOrder(userId, voucherId, orderId)` 落库后 ACK，HTTP 线程只做预占与返回 `orderId`。
5. **落库层**：异步阶段仍对 `tb_seckill_voucher` 使用 **`stock > 0` 条件更新**（乐观锁式扣减），订单表辅以 **`(user_id, voucher_id)` 唯一约束**。

### day04

**blog发布模块**
1. 添加了 **图片上传**，并配置了 **本地存储**。
2. 新增/整理了笔记相关查询：`GET /blog/hot` 查询热门笔记，`GET /blog/{id}` 查询笔记详情，`GET /blog/of/me` 查询我的笔记。
3. `GET /blog/hot` 已放行登录拦截；
4. 未登录用户查询热门笔记时，`isLike` 默认返回 `false`，避免 `UserHolder.getUser()` 为空导致异常。

**点赞模块**  
1. 使用 Redis ZSet 维护博客点赞用户：key 为 `blog:liked:{blogId}`，member 为 `userId`，score 为点赞时间戳。
2. 点赞接口 `PUT /blog/like/{id}` 支持重复点击切换：未点赞则 `liked + 1` 并写入 ZSet；已点赞则 `liked - 1` 并从 ZSet 移除。
3. 使用 `opsForZSet().score(...)` 判断当前登录用户是否已点赞，替代普通 Set 的 `isMember` 判断。
4. 新增点赞排行榜接口 `GET /blog/likes/{id}`，默认查询前 5 个最早点赞的用户。
5. 排行榜查询 Redis 后，再通过 `ORDER BY FIELD(id,...)` 按 Redis 返回顺序回填 `UserDTO`，避免数据库 `IN` 查询打乱顺序。

### day05

**好友关注与 Feed**

1. **关注关系双写**：`tb_follow` 与 Redis Set **`follow:{userId}`**（member 为被关注用户 id）；`PUT /follow/{id}/{isFollow}` 关注/取关，`GET /follow/or/not/{id}` 查是否关注（本实现走库表 count）。
2. **共同关注**：`GET /follow/common/{id}` 对两个用户的 **`follow:`** Set 做 **`SINTER`**，再查用户列表返回 `UserDTO`。
3. **推模式收件箱**：笔记 **`saveBlog`** 成功后，向所有粉丝的 **`feed:{粉丝id}`** ZSet **`ZADD`** `blogId`，score 用 **`System.currentTimeMillis()`** 做时间线。
4. **关注动态**：`GET /blog/of/follow`，按 ZSet 分数倒序取笔记 id，批量查 `Blog` 并回填作者与是否点赞。
5. **滚动分页**：`lastId` + `offset` 配合返回的 **`ScrollResult`**（`minTime`、`offset`），处理同一时间戳多条笔记，避免漏翻。
