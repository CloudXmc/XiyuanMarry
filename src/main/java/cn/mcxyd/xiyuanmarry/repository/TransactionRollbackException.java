package cn.mcxyd.xiyuanmarry.repository;

/** 仅表示 JDBC 已完成 rollback；作为 suppressed 标记保留业务异常原类型。 */
public final class TransactionRollbackException extends IllegalStateException {
    public TransactionRollbackException(Throwable cause) { super("数据库事务已确认回滚", cause); }
    public static boolean confirmed(Throwable error) {
        if (error instanceof TransactionRollbackException) return true;
        for (Throwable marker : error.getSuppressed()) if (marker instanceof TransactionRollbackException) return true;
        return false;
    }
}
