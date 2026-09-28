package cn.mcxyd.xiyuanmarry.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/** JDBC 边界：仅业务体失败并成功回滚才允许补偿；提交失联保守视为结果不明。 */
final class JdbcTransaction {
    private JdbcTransaction() {}

    static <T> T execute(Connection connection, Supplier<T> body) {
        try { connection.setAutoCommit(false); }
        catch (SQLException e) { throw new IllegalStateException("无法开始数据库事务", e); }
        T result;
        try {
            result = body.get();
        } catch (Throwable failure) {
            try { connection.rollback(); }
            catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                // 回滚失败时绝不能打开 autocommit，可能把未确认的变更提交。
                throw new IllegalStateException("数据库回滚结果不明", failure);
            }
            try { connection.setAutoCommit(true); }
            catch (SQLException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            if (failure instanceof RuntimeException runtime) {
                runtime.addSuppressed(new TransactionRollbackException(null));
                throw runtime;
            }
            throw new TransactionRollbackException(failure);
        }
        try { connection.commit(); }
        catch (SQLException failure) {
            try { connection.rollback(); }
            catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw new IllegalStateException("数据库提交结果不明", failure);
        }
        try { connection.setAutoCommit(true); }
        catch (SQLException failure) { throw new IllegalStateException("数据库提交后的连接复位失败", failure); }
        return result;
    }
}
