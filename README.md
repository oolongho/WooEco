# WooEco

🍵一款高性能、线程安全的 Minecraft 经济插件

## 特色

### 🛡️ 线程安全设计
- **细粒度锁机制**：使用 `volatile`、`synchronized` 和 `Atomic*` 类型实现最大程度的线程安全
- **脏数据追踪**：仅保存变更数据到数据库，大幅减少 I/O 操作
- **智能异步调度**：自动检测主线程并智能调度任务
- **超时保护**：异步操作可配置的超时时间，防止死锁

### ⚡ 性能优化
- **O(1) 玩家查找**：基于名称索引的缓存，实现即时玩家查找
- **HikariCP 连接池**：优化的数据库连接，针对 MySQL 进行专项调优
- **O(1) 排名查询**：排行榜排名缓存，大幅提升 PlaceholderAPI 变量性能
- **读写锁优化**：读操作共享锁，写操作互斥锁，提升并发性能
- **懒加载统计刷新**：全局统计数据按需缓存和刷新

### 🔌 丰富的集成支持
- **PlaceholderAPI**：13+ 变量支持余额、排名、排行榜显示
- **Vault API**：完全兼容依赖经济 API 的其他插件
- **Redis 同步**：跨服数据同步
- **Towny/Factions**：支持城镇/国家银行等非玩家账户

### 🎮 玩家体验
- **日收入追踪**：通过 `/eco income` 查看今日收入
- **双排行榜系统**：余额和收入双排行榜，支持黑名单过滤
- **离线转账提示**：上线时提示离线期间收到的转账
- **交易税系统**：可配置税率，支持指定税收接收账户
- **交易历史查询**：通过 `/eco history` 查看详细交易记录
- **快捷命令**：`/pay` `/income` 等快捷命令

## 环境

- Minecraft 1.21+
- Java 21+
- Vault（可选）
- PlaceholderAPI（可选）
- Redis（可选，用于跨服同步）

## 命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/wooeco` | 查看余额 | `wooeco.balance` |
| `/wooeco pay <玩家> <金额>` | 向玩家转账 | `wooeco.pay` |
| `/wooeco income [玩家]` | 查看日收入 | `wooeco.income` |
| `/wooeco history [玩家] [页码]` | 查看交易历史 | `wooeco.history` |
| `/wooeco top all/income [页码]` | 查看排行榜 | `wooeco.top` |
| `/wooeco give/take/set <玩家> <金额>` | 管理员操作 | `wooeco.admin.*` |
| `/wooeco giveall/takeall/setall <all/online> <金额>` | 批量操作 | `wooeco.admin.*` |
| `/wooeco reload` | 重载配置 | `wooeco.admin.reload` |
| `/pay <玩家> <金额>` | 快捷转账 | `wooeco.pay` |
| `/income [玩家]` | 快捷查看收入 | `wooeco.income` |

## PlaceholderAPI 变量

| 变量 | 描述 |
|------|------|
| `%wooeco_balance%` | 玩家余额 |
| `%wooeco_balance_formatted%` | 格式化余额（带货币符号） |
| `%wooeco_daily_income%` | 今日总收入 |
| `%wooeco_top_rank%` | 玩家排行榜排名 |
| `%wooeco_top_player_<n>%` | 排行榜第N名玩家名 |
| `%wooeco_top_balance_<n>%` | 排行榜第N名余额 |
| `%wooeco_sum_balance%` | 全服总余额 |
| `%wooeco_player_count%` | 账户总数 |

## API 使用示例

```java
WooEcoAPI api = WooEcoAPI.getInstance();

// 获取余额
double balance = api.getBalance(player.getUniqueId());

// 存款（带原因）
api.deposit(uuid, 100.0, BalanceChangeReason.ADMIN, "console");

// 监听事件
@EventHandler
public void onBalanceChange(BalanceChangeEvent event) {
    Player player = event.getPlayer();
    BigDecimal newBalance = event.getNewBalance();
    // 你的逻辑
}
```

---

❤️ 主包是开发新手，如果有做得不好的地方，欢迎指正。希望能和大家一起交流！
⭐ 觉得有用请给个 Star 爱你哟 
