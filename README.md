# WooEco

🍵 一款高性能、多功能的 Minecraft 经济插件

## 特色

### 🛡️ 线程安全设计

- **细粒度锁机制**：使用 `volatile`、`synchronized` 和 `Atomic*` 类型实现最大程度的线程安全
- **脏数据追踪**：仅保存变更数据到数据库，大幅减少 I/O 操作
- **智能异步调度**：自动检测主线程并智能调度任务
- **超时保护**：异步操作可配置的超时时间，防止死锁

### ⚡ 性能优化

- **O(1) 玩家查找**：基于名称索引的缓存，实现即时玩家查找
- **HikariCP 连接池**：优化的数据库连接，针对 MySQL 进行专项调优
- **O(1) 排名查询**：排行榜排名缓存，大幅提升变量性能
- **读写锁优化**：读操作共享锁，写操作互斥锁，提升并发性能
- **懒加载统计刷新**：全局统计数据按需缓存和刷新

### 🔌 丰富的集成支持

- **PlaceholderAPI**：30+ 变量支持余额、收入、排名、排行榜显示
- **Vault API**：完全兼容依赖经济 API 的其他插件
- **Redis 同步**：跨服数据同步
- **Towny/Factions**：支持城镇/国家银行等非玩家账户

### 🎮 玩家体验

- **多时段收入追踪**：通过 `/eco income day/week/month` 查看日/周/月收入
- **多时段收入排行**：收入排行榜支持日/周/月三个时段
- **收款开关**：玩家可关闭收款功能，防止骚扰转账
- **双排行榜系统**：余额和收入双排行榜，支持黑名单过滤
- **离线转账提示**：上线时提示离线期间收到的转账
- **交易税系统**：可配置税率，支持指定税收接收账户（支持 UUID 或玩家名）
- **交易历史查询**：通过 `/eco history` 查看详细交易记录
- **快捷命令**：`/pay` `/income` 等快捷命令

### 🔄 数据迁移

- **Vault 通用迁移**：从任何 Vault 经济插件迁移（Essentials、CMI 等）
- **XConomy 完整迁移**：从 XConomy 数据库直接迁移，包含交易记录和非玩家账户
- **安全预检**：`--dry-run` 参数预览迁移结果而不写入数据
- **幂等操作**：基于 UPSERT，重复迁移不会产生重复数据

### 🌐 多模式 UUID 支持

- **Default**：根据 `server.properties` 的 `online-mode` 自动判断
- **Online**：始终使用正版 UUID
- **Offline**：使用离线 UUID，适合离线服务器
- **SemiOnline**：半在线模式，适合 GeyserMC 混合服务器（离线→正版 UUID 映射）

## 环境

- Minecraft 1.21+
- Java 21+
- Vault 1.7+
- PlaceholderAPI（可选）
- Redis（可选，用于跨服同步）

## 安装

1. 下载最新版本的 WooEco.jar
2. 将文件放入服务器的 `plugins` 目录
3. 启动服务器，插件会自动生成配置文件
4. 根据需要修改 `config.yml` 配置文件
5. 使用 `/eco reload` 重载配置

## 命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/eco` 或 `/money` | 查看自己的余额 | `wooeco.balance` |
| `/eco look [玩家]` | 查看余额 | `wooeco.balance` / `wooeco.balance.other` |
| `/eco pay <玩家> <金额>` | 向玩家转账 | `wooeco.pay` |
| `/pay <玩家> <金额>` | 快捷转账 | `wooeco.pay` |
| `/eco income [day/week/month] [玩家]` | 查看收入 | `wooeco.income` / `wooeco.income.other` |
| `/income [day/week/month] [玩家]` | 快捷查看收入 | `wooeco.income` |
| `/eco top all [页码]` | 余额排行榜 | `wooeco.top` |
| `/eco top income [day/week/month] [页码]` | 收入排行榜 | `wooeco.top` |
| `/eco history [玩家] [页码]` | 查看交易历史 | `wooeco.history` / `wooeco.history.other` |
| `/eco paytoggle` | 查看收款状态 | `wooeco.paytoggle` |
| `/eco paytoggle <on/off>` | 开启/关闭收款 | `wooeco.paytoggle` |
| `/eco paytoggle <玩家> [on/off]` | 管理员设置他人收款状态 | `wooeco.paytoggle.other` |
| `/eco give <玩家> <金额>` | 给予玩家金币 | `wooeco.admin.give` |
| `/eco take <玩家> <金额>` | 扣除玩家金币 | `wooeco.admin.take` |
| `/eco set <玩家> <金额>` | 设置玩家余额 | `wooeco.admin.set` |
| `/eco giveall <all/online> <金额>` | 批量给予金币 | `wooeco.admin.give` |
| `/eco takeall <all/online> <金额>` | 批量扣除金币 | `wooeco.admin.take` |
| `/eco setall <all/online> <金额>` | 批量设置余额 | `wooeco.admin.set` |
| `/eco reload` | 重载配置 | `wooeco.admin.reload` |
| `/eco debug <on/off/status/player/reload>` | 调试工具 | `wooeco.admin.debug` |
| `/eco migrate <vault/xconomy/status> [--dry-run]` | 数据迁移 | `wooeco.admin.migrate` |

**命令别名：** 通过 `config.yml` 的 `currency.aliases` 配置，默认：`/eco`、`/money`、`/bal`；`/wooeco`、`/weco` 为固定别名

## 权限

### 权限节点层级

```
wooeco.player (默认: true)             ← 玩家常用权限集合
├── wooeco.use                         ← 基本使用
├── wooeco.help                        ← 查看帮助
├── wooeco.balance                     ← 查看自己余额
├── wooeco.pay                         ← 转账
├── wooeco.income                      ← 查看自己收入
├── wooeco.history                     ← 查看交易历史
├── wooeco.top                         ← 排行榜
└── wooeco.paytoggle                   ← 收款开关

wooeco.admin (默认: op)                ← 管理员权限集合
├── wooeco.player                      ← 继承玩家全部权限
├── wooeco.balance.other               ← 查看他人余额
├── wooeco.income.other                ← 查看他人收入
├── wooeco.history.other               ← 查看他人历史
├── wooeco.paytoggle.other             ← 设置他人收款
├── wooeco.admin.give                  ← 给予金币
├── wooeco.admin.take                  ← 扣除金币
├── wooeco.admin.set                   ← 设置余额
├── wooeco.admin.reload                ← 重载配置
├── wooeco.admin.debug                 ← 调试
├── wooeco.admin.migrate               ← 数据迁移
└── wooeco.bypass.tax                  ← 豁免交易税
```

> 💡 权限插件中只需分配 `wooeco.player` 或 `wooeco.admin` 即可，各子权限仍可单独授予/撤销。

### 完整权限列表

| 权限 | 描述 | 默认 |
|------|------|------|
| `wooeco.player` | 玩家常用权限集合 | true |
| `wooeco.admin` | 管理员权限集合（含玩家权限） | op |
| `wooeco.use` | 基本使用权限 | true |
| `wooeco.help` | 查看帮助 | true |
| `wooeco.balance` | 查看自己的余额 | true |
| `wooeco.balance.other` | 查看其他玩家的余额 | op |
| `wooeco.pay` | 向玩家转账 | true |
| `wooeco.income` | 查看自己的收入 | true |
| `wooeco.income.other` | 查看其他玩家的收入 | op |
| `wooeco.history` | 查看交易历史 | true |
| `wooeco.history.other` | 查看其他玩家的交易历史 | op |
| `wooeco.top` | 查看排行榜 | true |
| `wooeco.paytoggle` | 切换收款功能 | true |
| `wooeco.paytoggle.other` | 设置他人收款状态 | op |
| `wooeco.admin.give` | 给予玩家金币 | op |
| `wooeco.admin.take` | 扣除玩家金币 | op |
| `wooeco.admin.set` | 设置玩家余额 | op |
| `wooeco.admin.reload` | 重载配置 | op |
| `wooeco.admin.migrate` | 数据迁移 | op |
| `wooeco.admin.debug` | 接收调试信息 | op |
| `wooeco.bypass.tax` | 豁免交易税 | op |

## PlaceholderAPI 变量

### 余额与收入

| 变量 | 描述 |
|------|------|
| `%wooeco_balance%` | 玩家余额 |
| `%wooeco_balance_formatted%` | 格式化余额（带货币符号） |
| `%wooeco_balance_value%` | 余额数值（无格式化） |
| `%wooeco_daily_income%` | 今日收入 |
| `%wooeco_daily_income_formatted%` | 今日收入（格式化） |
| `%wooeco_weekly_income%` | 本周收入 |
| `%wooeco_weekly_income_formatted%` | 本周收入（格式化） |
| `%wooeco_monthly_income%` | 本月收入 |
| `%wooeco_monthly_income_formatted%` | 本月收入（格式化） |

### 排名

| 变量 | 描述 |
|------|------|
| `%wooeco_top_rank%` | 自己的余额排名 |
| `%wooeco_top_rank_<玩家>%` | 指定玩家的余额排名 |

### 余额排行榜

| 变量 | 描述 |
|------|------|
| `%wooeco_top_player_<n>%` | 余额排行榜第 N 名玩家名 |
| `%wooeco_top_balance_<n>%` | 余额排行榜第 N 名余额 |
| `%wooeco_top_balance_formatted_<n>%` | 余额排行榜第 N 名余额（格式化） |

### 收入排行榜

| 变量 | 描述 |
|------|------|
| `%wooeco_top_income_player_<n>%` | 日收入排行榜第 N 名玩家名 |
| `%wooeco_top_income_<n>%` | 日收入排行榜第 N 名收入 |
| `%wooeco_top_income_formatted_<n>%` | 日收入排行榜第 N 名收入（格式化） |
| `%wooeco_top_income_player_week_<n>%` | 周收入排行榜第 N 名玩家名 |
| `%wooeco_top_income_week_<n>%` | 周收入排行榜第 N 名收入 |
| `%wooeco_top_income_formatted_week_<n>%` | 周收入排行榜第 N 名收入（格式化） |
| `%wooeco_top_income_player_month_<n>%` | 月收入排行榜第 N 名玩家名 |
| `%wooeco_top_income_month_<n>%` | 月收入排行榜第 N 名收入 |
| `%wooeco_top_income_formatted_month_<n>%` | 月收入排行榜第 N 名收入（格式化） |

### 全局统计

| 变量 | 描述 |
|------|------|
| `%wooeco_sum_balance%` | 全服总余额 |
| `%wooeco_sum_balance_formatted%` | 全服总余额（格式化） |
| `%wooeco_player_count%` | 账户总数 |
| `%wooeco_pay_toggle%` | 收款开关状态（true/false） |

## 数据迁移

### 从 Vault 迁移（通用）

可从任何 Vault 经济插件迁移（Essentials、CMI 等）：

1. 保持旧经济插件运行
2. 在 WooEco 配置中设置 `vault.register-as-provider: false`
3. 执行 `/eco migrate vault`
4. 验证数据后，设置 `vault.register-as-provider: true` 并移除旧插件

### 从 XConomy 迁移（完整）

从 XConomy 数据库直接迁移，包含玩家余额、非玩家账户和交易记录：

1. 在 `config.yml` 中配置 `migration.xconomy` 部分
2. 执行 `/eco migrate xconomy`
3. 使用 `--dry-run` 预览迁移结果：`/eco migrate xconomy --dry-run`

### 迁移命令一览

| 命令 | 描述 |
|------|------|
| `/eco migrate vault` | 从 Vault 经济插件迁移 |
| `/eco migrate xconomy` | 从 XConomy 数据库迁移 |
| `/eco migrate vault --dry-run` | 预览 Vault 迁移（不写入数据） |
| `/eco migrate xconomy --dry-run` | 预览 XConomy 迁移（不写入数据） |
| `/eco migrate status` | 查看上次迁移结果 |

## UUID 模式

| 模式 | 描述 |
|------|------|
| `Default` | 根据 `server.properties` 的 `online-mode` 自动判断 |
| `Online` | 始终使用正版 UUID |
| `Offline` | 使用离线 UUID，适合离线服务器 |
| `SemiOnline` | 半在线模式，适合 GeyserMC 混合服务器 |

## API 使用示例

```java
import com.oolonghoo.wooeco.api.WooEcoAPI;
import com.oolonghoo.wooeco.api.events.BalanceChangeEvent;
import com.oolonghoo.wooeco.api.events.TransactionEvent;
import com.oolonghoo.wooeco.manager.EconomyManager.EconomyResult;
import com.oolonghoo.wooeco.manager.TransactionManager.TransactionResult;
import java.util.UUID;

public class MyPlugin extends JavaPlugin {
    
    public void exampleUsage() {
        UUID playerUuid = player.getUniqueId();
        
        if (!WooEcoAPI.isLoaded()) {
            return;
        }
        
        double balance = WooEcoAPI.getBalance(playerUuid);
        boolean hasEnough = WooEcoAPI.has(playerUuid, 100.0);
        
        EconomyResult depositResult = WooEcoAPI.deposit(playerUuid, 100.0);
        if (depositResult.isSuccess()) {
            player.sendMessage("存款成功！新余额：" + depositResult.getBalance());
        }
        
        EconomyResult withdrawResult = WooEcoAPI.withdraw(playerUuid, 50.0);
        if (withdrawResult.isSuccess()) {
            player.sendMessage("扣款成功！");
        }
        
        EconomyResult setResult = WooEcoAPI.set(playerUuid, 1000.0);
        
        TransactionResult txResult = WooEcoAPI.transfer(
            player1.getUniqueId(), 
            player2.getUniqueId(), 
            50.0
        );
        
        String formatted = WooEcoAPI.format(12345.67);
        List<PlayerAccount> topPlayers = WooEcoAPI.getTopBalances(10);
    }
    
    @EventHandler
    public void onBalanceChange(BalanceChangeEvent event) {
        UUID uuid = event.getPlayerUuid();
        double oldBalance = event.getOldBalance();
        double newBalance = event.getNewBalance();
        double change = event.getChangeAmount();
    }
    
    @EventHandler
    public void onTransaction(TransactionEvent event) {
        UUID sender = event.getSenderUuid();
        UUID receiver = event.getReceiverUuid();
        double amount = event.getAmount();
        double tax = event.getTax();
    }
}
```

## 配置文件

<details>
<summary>点击查看 config.yml</summary>

```yaml
# WooEco 配置文件

settings:
  debug: false
  language: zh-CN
  auto-save-interval: 300
  uuid-mode: Default        # Default/Online/Offline/SemiOnline
  username-ignore-case: false

performance:
  async-timeout: 3
  batch-async: true
  force-async: false
  disable-cache: false
  max-concurrent-operations: 10
  max-queue-size: 100

debug:
  enabled: false
  log-to-file: true
  log-to-console: true
  log-to-online-admins: false
  categories:
    - "ALL"                  # ALL/DATABASE/ECONOMY/TRANSACTION/CACHE/SYNC/COMMAND/EVENT/CONFIG/API

currency:
  singular-name: "金币"
  plural-name: "金币"
  symbol: "&e￥&r"
  aliases:
    - "money"
    - "bal"
    - "eco"
  starting-balance: 0
  max-balance: 10000000000000000
  integer-balance: false
  rounding-mode: 2           # 0=向下, 1=向上, 2=四舍五入
  format:
    thousands-separator: ","
    decimal-places: 2
    display-format: "%balance% %currencyname%"
  format-balance:
    enabled: false
    thresholds:
      1000: "k"
      1000000: "m"
      1000000000: "b"

transaction:
  min-amount: 1
  max-amount: 1000000
  offline-transfer-tips: true
  tax:
    enabled: true
    rate: 5
    receiver: null            # null=销毁税收，支持 UUID 或玩家名

command-cooldown:
  enabled: true
  cooldowns:
    pay: 3
    give: 0
    giveall: 0
    take: 0
    takeall: 0
    set: 0
    setall: 0
    balance: 0
    look: 0
    top: 5
    history: 3
    income: 0
  message: "&c请等待 %time% 秒后再次使用此命令"

history:
  per-page: 10

leaderboard:
  cache-refresh: 60
  per-page: 10
  blacklist:
    enabled: false
    players:
      - "Notch"
    uuids: []

database:
  type: SQLite
  file: "data.db"
  mysql:
    host: localhost
    port: 3306
    database: wooeco
    user: root
    password: ""
    pool-size: 10
    table-prefix: "wooeco_"
    use-ssl: false
    server-timezone: "Asia/Shanghai"
  auto-save: 60

sync:
  enable: false
  redis:
    host: localhost
    port: 6379
    password: ""
    channel: "wooeco:sync"
  server-id: "server-1"

logging:
  transaction: true
  admin: true
  retention-days: 30         # 0=永久保留

non-player-account:
  enable: false
  whitelist:
    enable: false
    fields-list:
      - "town-"
      - "nation-"
      - "bank-"
      - "tax"

vault:
  register-as-provider: true  # 迁移期间设为 false

migration:
  xconomy:
    type: "MySQL"
    sqlite-file: "plugins/XConomy/data.db"
    table-prefix: ""
    table-suffix: ""
    mysql:
      host: localhost
      port: 3306
      database: ""
      user: ""
      password: ""
    migrate-records: true
    migrate-non-player: true
```

</details>

<br />

***

❤️ 主包是开发新手，如果有做得不好的地方，欢迎指正。希望能和大家一起交流！

⭐ 觉得有用请给个 Star 爱你哟
