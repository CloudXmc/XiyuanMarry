package cn.mcxyd.xiyuanmarry.repository;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcTransactionTest {
    @Test void commitsBeforeReturningValue() throws Exception {
        var c=mock(Connection.class); assertEquals(42,JdbcTransaction.execute(c,()->42));
        var ordered=inOrder(c); ordered.verify(c).setAutoCommit(false); ordered.verify(c).commit();
        ordered.verify(c).setAutoCommit(true); verify(c,never()).rollback();
    }
    @Test void bodyFailureIsRefundableOnlyAfterSuccessfulRollback() throws Exception {
        var c=mock(Connection.class); var failure=new IllegalStateException("write failed");
        var e=assertThrows(IllegalStateException.class,()->JdbcTransaction.execute(c,()->{throw failure;}));
        assertSame(failure,e); assertTrue(TransactionRollbackException.confirmed(e));
        verify(c).rollback(); verify(c,never()).commit();
    }
    @Test void commitFailureRemainsUncertainEvenIfRollbackReturns() throws Exception {
        var c=mock(Connection.class); doThrow(new SQLException("unknown commit result")).when(c).commit();
        var e=assertThrows(IllegalStateException.class,()->JdbcTransaction.execute(c,()->42));
        assertFalse(TransactionRollbackException.confirmed(e)); verify(c).rollback();
    }
    @Test void rollbackFailureNeverEnablesAutocommit() throws Exception {
        var c=mock(Connection.class); doThrow(new SQLException("connection lost")).when(c).rollback();
        var e=assertThrows(IllegalStateException.class,()->JdbcTransaction.execute(c,()->{throw new IllegalArgumentException();}));
        assertFalse(TransactionRollbackException.confirmed(e)); verify(c,never()).setAutoCommit(true);
    }
}
