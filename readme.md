# hm-dianping（黑马点评）

基于黑马程序员 [黑马点评](https://www.bilibili.com/video/BV1NV411u7GE/?spm_id_from=333.337.search-card.all.click) 的 **Spring Boot 后端练习项目**：本地生活 / 商户点评类业务（用户、商户、优惠券、笔记、上传等）。

## 技术栈

| 类别 | 说明 |
|------|------|
| 框架 | Spring Boot 2.7、Spring Web |
| 数据 | MySQL、MyBatis-Plus |
| 缓存 / 会话 | Redis（`StringRedisTemplate`、Hash 存登录态） |
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
│   │   │   ├── service                      # 业务接口
│   │   │   ├── service/impl                 # 业务实现
│   │   │   └── utils                        # Redis 常量、拦截器、ID 生成器、缓存工具等
│   │   └── resources
│   │       ├── application.yaml             # 服务端口、MySQL、Redis、MyBatis-Plus 配置
│   │       ├── db/hmdp.sql                  # 初始化 SQL
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
- **秒杀下单**：库存乐观更新（`stock > 0` 条件更新）、一人一单校验、`RedisIdWorker` 生成订单号、Redisson 按 `userId:voucherId` 加分布式锁防同一用户重复下单。
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
- **秒杀链路仍偏同步**：虽然写入了 `seckill:stock:{voucherId}` 库存缓存，但实际下单仍查询数据库并同步扣库存，没有使用 Redis + Lua 原子校验、消息队列/Redis Stream 异步下单等高并发方案。
- **订单防重缺少数据库兜底**：一人一单主要依赖 Redisson 业务锁和代码查询，`tb_voucher_order` 未看到 `user_id + voucher_id` 唯一索引。建议增加唯一约束，避免锁失效、重复请求或异常场景造成重复订单。
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

**秒杀券抢购**

1. **全局 ID 生成器**：由时间戳差值 + 当日序列号等组成（Redis 自增），见 `RedisIdWorker`。
2. 1) **乐观锁**：允许并发查询，但在更新时检查数据是否被修改过，如果被修改则更新失败。  
   2) **悲观锁**：在查询库存时就直接锁定这条记录，直到整个扣减操作完成才释放锁。  
   3) 单机悲观锁在分布式环境失效，只对当前 JVM 有效。
3. synchorized 锁：单机锁，对当前 JVM 有效，但无法实现分布式锁。
4. 使用Redisson的Rlock实现集群模式下的分布式锁，保证锁的可重入性和可重试性

## 运行提示

- 配置 MySQL、Redis 连接（`src/main/resources` 下配置文件）。
- 数据表与课程 SQL 一致时使用实体上的 `tb_*` 表名；若自行改表，需保证外键语义与代码一致（如 `tb_blog.user_id` → `tb_user.id`）。
