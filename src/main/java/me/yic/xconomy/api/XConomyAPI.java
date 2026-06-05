package me.yic.xconomy.api;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.api.WooEcoAPI;
import com.oolonghoo.wooeco.manager.EconomyManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import me.yic.xconomy.data.syncdata.PlayerData;
import me.yic.xconomy.info.SyncChannalType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * XConomy 兼容层 API
 * 委托给 WooEcoAPI 实现，让依赖 XConomy 的插件无需修改即可使用
 */
public class XConomyAPI {

    private static boolean compatEnabled = true;

    public static void setCompatEnabled(boolean enabled) {
        compatEnabled = enabled;
    }

    public static boolean isCompatEnabled() {
        return compatEnabled;
    }

    public String getversion() {
        if (!WooEcoAPI.isLoaded()) return "Unknown";
        return WooEcoAPI.getInstance().getDescription().getVersion();
    }

    public SyncChannalType getSyncChannalType() {
        if (!WooEcoAPI.isLoaded()) return SyncChannalType.OFF;
        if (WooEcoAPI.getInstance().getRedisSyncManager() != null) {
            return SyncChannalType.REDIS;
        }
        return SyncChannalType.OFF;
    }

    public BigDecimal formatdouble(String amount) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public String getdisplay(BigDecimal balance) {
        if (!WooEcoAPI.isLoaded()) return balance.toString();
        return WooEcoAPI.formatWithColor(balance.doubleValue());
    }

    public boolean createPlayerData(UUID uid, String name) {
        if (!WooEcoAPI.isLoaded()) return false;
        if (WooEcoAPI.hasAccount(uid)) return false;
        WooEcoAPI.createAccount(uid, name);
        return true;
    }

    public PlayerData getPlayerData(UUID uid) {
        if (!WooEcoAPI.isLoaded()) return null;
        PlayerAccount account = WooEcoAPI.getAccount(uid);
        return PlayerData.fromAccount(account);
    }

    public PlayerData getPlayerData(String name) {
        if (!WooEcoAPI.isLoaded()) return null;
        WooEco plugin = WooEcoAPI.getInstance();
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(name);
        PlayerAccount account = WooEcoAPI.getAccount(offlinePlayer.getUniqueId());
        return PlayerData.fromAccount(account);
    }

    public boolean createNonPlayerData(String account) {
        if (!WooEcoAPI.isLoaded()) return false;
        WooEco plugin = WooEcoAPI.getInstance();
        if (plugin.getNonPlayerAccountManager() == null) return false;
        return plugin.getNonPlayerAccountManager().getOrCreateAccount(account) != null;
    }

    public BigDecimal getNonPlayerBalance(String account) {
        if (!WooEcoAPI.isLoaded()) return BigDecimal.ZERO;
        WooEco plugin = WooEcoAPI.getInstance();
        if (plugin.getNonPlayerAccountManager() == null) return BigDecimal.ZERO;
        return plugin.getNonPlayerAccountManager().getBalance(account);
    }

    @Deprecated
    public BigDecimal getorcreateAccountBalance(String account) {
        return getNonPlayerBalance(account);
    }

    public boolean ismaxnumber(BigDecimal amount) {
        if (!WooEcoAPI.isLoaded()) return false;
        BigDecimal maxBalance = WooEcoAPI.getInstance().getCurrencyConfig().getMaxBalanceBigDecimal();
        return amount.compareTo(maxBalance) > 0;
    }

    public int changePlayerBalance(UUID u, String playername, BigDecimal amount, Boolean isAdd) {
        return changePlayerBalance(u, playername, amount, isAdd, null);
    }

    public int changePlayerBalance(UUID u, String playername, BigDecimal amount, Boolean isAdd, String pluginname) {
        if (!WooEcoAPI.isLoaded()) return 1;
        if (ismaxnumber(amount)) return 3;

        EconomyManager.EconomyResult result;
        if (isAdd == null) {
            result = WooEcoAPI.set(u, amount.doubleValue());
        } else if (isAdd) {
            result = WooEcoAPI.deposit(u, amount.doubleValue());
        } else {
            if (!WooEcoAPI.has(u, amount.doubleValue())) return 2;
            result = WooEcoAPI.withdraw(u, amount.doubleValue());
        }

        return result.isSuccess() ? 0 : 2;
    }

    @Deprecated
    public int changeAccountBalance(String account, BigDecimal amount, Boolean isAdd) {
        return changeNonPlayerBalance(account, amount, isAdd);
    }

    @Deprecated
    public int changeAccountBalance(String account, BigDecimal amount, Boolean isAdd, String pluginname) {
        return changeNonPlayerBalance(account, amount, isAdd, pluginname);
    }

    public int changeNonPlayerBalance(String account, BigDecimal amount, Boolean isAdd) {
        return changeNonPlayerBalance(account, amount, isAdd, null);
    }

    public int changeNonPlayerBalance(String account, BigDecimal amount, Boolean isAdd, String pluginname) {
        if (!WooEcoAPI.isLoaded()) return 1;
        WooEco plugin = WooEcoAPI.getInstance();
        if (plugin.getNonPlayerAccountManager() == null) return 1;

        boolean success;
        if (isAdd == null) {
            success = plugin.getNonPlayerAccountManager().setBalance(account, amount);
        } else if (isAdd) {
            success = plugin.getNonPlayerAccountManager().deposit(account, amount);
        } else {
            success = plugin.getNonPlayerAccountManager().withdraw(account, amount);
        }

        return success ? 0 : 2;
    }

    public List<String> getbalancetop() {
        if (!WooEcoAPI.isLoaded()) return new ArrayList<>();
        List<PlayerAccount> top = WooEcoAPI.getTopBalances(10);
        List<String> result = new ArrayList<>(top.size());
        for (PlayerAccount account : top) {
            result.add(account.getPlayerName());
        }
        return result;
    }

    public BigDecimal getsumbalance() {
        if (!WooEcoAPI.isLoaded()) return BigDecimal.ZERO;
        return WooEcoAPI.getInstance().getGlobalStatsManager().getTotalBalance();
    }

    public boolean getglobalpermission(String permission) {
        return true;
    }

    public void setglobalpermission(String permission, boolean value) {
        // WooEco 不支持全局支付权限控制，空实现
    }

    public Boolean getpaymentpermission(UUID uid) {
        if (!WooEcoAPI.isLoaded()) return true;
        return WooEcoAPI.getInstance().getPayToggleManager().isPayEnabled(uid);
    }

    public void setpaymentpermission(UUID uid, Boolean value) {
        if (!WooEcoAPI.isLoaded()) return;
        WooEcoAPI.getInstance().getPayToggleManager().setPayEnabled(uid, value);
    }

    public Boolean getpaytoggle(UUID uid) {
        if (!WooEcoAPI.isLoaded()) return true;
        return WooEcoAPI.getInstance().getPayToggleManager().isPayEnabled(uid);
    }

    public void setpaytoggle(UUID uid, boolean value) {
        if (!WooEcoAPI.isLoaded()) return;
        WooEcoAPI.getInstance().getPayToggleManager().setPayEnabled(uid, value);
    }
}
