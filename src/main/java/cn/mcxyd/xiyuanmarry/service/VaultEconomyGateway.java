package cn.mcxyd.xiyuanmarry.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;

import java.util.UUID;

/** 仅在 Vault 插件和经济服务均已注册时创建。 */
public final class VaultEconomyGateway implements EconomyGateway {
    private final Economy economy;

    private VaultEconomyGateway(Economy economy) { this.economy = economy; }

    public static EconomyGateway createIfAvailable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return null;
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : new VaultEconomyGateway(registration.getProvider());
    }

    @Override public boolean deposit(UUID player, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return true;
        var response = economy.depositPlayer(Bukkit.getOfflinePlayer(player), amount);
        return response != null && response.transactionSuccess();
    }
}
