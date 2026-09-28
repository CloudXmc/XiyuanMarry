package cn.mcxyd.xiyuanmarry.model;
import java.util.UUID;
public record Proposal(UUID id, UUID proposer, UUID target, String type, long expiresAt) {}
