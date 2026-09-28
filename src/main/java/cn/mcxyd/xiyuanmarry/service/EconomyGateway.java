package cn.mcxyd.xiyuanmarry.service;

import java.util.UUID;

public interface EconomyGateway {
    boolean deposit(UUID player, double amount);
}
