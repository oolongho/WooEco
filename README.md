# WooEco

🍵一款高性能、线程安全的 Minecraft 经济插件

## 特色

- **线程安全** - 细粒度锁、脏数据追踪、智能异步调度
- **高性能** - O(1) 玩家查找、HikariCP 连接池、排名缓存
- **丰富集成** - PlaceholderAPI (13+ 变量)、Vault API、Redis 跨服同步
- **玩家体验** - 日收入追踪、排行榜、离线转账提示、交易税

## 环境

- Minecraft 1.21+
- Java 21+
- Vault / PlaceholderAPI / Redis (可选)

## 命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/wooeco` | 查看余额 | `wooeco.balance` |
| `/wooeco pay <玩家> <金额>` | 转账 | `wooeco.pay` |
| `/wooeco income [玩家]` | 日收入 | `wooeco.income` |
| `/wooeco history [玩家] [页码]` | 交易历史 | `wooeco.history` |
| `/wooeco top all/income [页码]` | 排行榜 | `wooeco.top` |
| `/wooeco give/take/set <玩家> <金额>` | 管理员操作 | `wooeco.admin.*` |
| `/wooeco reload` | 重载配置 | `wooeco.admin.reload` |
| `/pay` `/income` | 快捷命令 | - |

## PlaceholderAPI

| 变量 | 描述 |
|------|------|
| `%wooeco_balance%` | 余额 |
| `%wooeco_balance_formatted%` | 格式化余额 |
| `%wooeco_daily_income%` | 今日收入 |
| `%wooeco_top_rank%` | 排名 |
| `%wooeco_top_player_<n>%` | 第N名玩家 |
| `%wooeco_top_balance_<n>%` | 第N名余额 |

## API

```java
WooEcoAPI api = WooEcoAPI.getInstance();

double balance = api.getBalance(player.getUniqueId());
api.deposit(uuid, 100.0, BalanceChangeReason.ADMIN, "console");

@EventHandler
public void onBalanceChange(BalanceChangeEvent event) {
    // your logic
}
```

---

⭐ 觉得有用请给个 Star 爱你哟 ❤️
