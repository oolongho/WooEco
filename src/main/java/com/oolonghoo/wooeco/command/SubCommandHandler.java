package com.oolonghoo.wooeco.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 子命令处理器接口
 * 所有子命令处理器都需要实现此接口
 */
public interface SubCommandHandler {
    
    /**
     * 获取子命令名称
     */
    String getName();
    
    /**
     * 获取子命令描述（用于 help 命令）
     */
    String getDescription();
    
    /**
     * 获取子命令所需的权限
     */
    String getPermission();
    
    /**
     * 执行子命令逻辑
     * @param sender 命令发送者
     * @param args 命令参数（不包含子命令本身）
     * @return true 表示命令处理成功，false 表示处理失败
     */
    boolean execute(CommandSender sender, String[] args);
    
    /**
     * 获取 Tab 补全建议
     * @param sender 命令发送者
     * @param args 命令参数（不包含子命令本身）
     * @return 补全建议列表
     */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
    
    /**
     * 判断是否为管理员命令
     */
    default boolean isAdminCommand() {
        return false;
    }
}
