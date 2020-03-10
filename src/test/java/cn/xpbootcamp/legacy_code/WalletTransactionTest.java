package cn.xpbootcamp.legacy_code;

import cn.xpbootcamp.legacy_code.enums.STATUS;
import cn.xpbootcamp.legacy_code.service.WalletService;
import cn.xpbootcamp.legacy_code.service.WalletServiceImpl;
import cn.xpbootcamp.legacy_code.utils.IdGenerator;
import cn.xpbootcamp.legacy_code.utils.RedisDistributedLock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.transaction.InvalidTransactionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({RedisDistributedLock.class, IdGenerator.class})
public class WalletTransactionTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private WalletService walletService;
    private WalletTransaction walletTransaction;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void should_generate_id_with_preAssigned_id_when_given_valid_pre_assigned_id() {
        walletTransaction = new WalletTransaction("t_id", null, null, 100L, "orderId", 1.0);
        assertThat(walletTransaction.getId()).isEqualTo("t_id");
    }

    @Test
    public void should_generate_id_with_IdGenerator_when_given_null_pre_assigned_id() {
        mockStatic(IdGenerator.class);
        PowerMockito.when(IdGenerator.generateTransactionId()).thenReturn("uuid");
        walletTransaction = new WalletTransaction(null, null, null, 100L, "orderId", 1.0);

        assertThat(walletTransaction.getId()).isEqualTo("t_null");
    }

    @Test
    public void should_generate_id_with_IdGenerator_when_given_empty_pre_assigned_id() {
        mockStatic(IdGenerator.class);
        PowerMockito.when(IdGenerator.generateTransactionId()).thenReturn("uuid");
        walletTransaction = new WalletTransaction("", null, null, 100L, "orderId", 1.0);

        assertThat(walletTransaction.getId()).isEqualTo("t_");
    }

    @Test
    public void should_return_false_when_trader_is_invalid() throws InvalidTransactionException {
        walletService = Mockito.mock(WalletServiceImpl.class);
        walletTransaction = new WalletTransaction(null, null, null, 100L, "orderId", 1.0);

        expectedException.expect(InvalidTransactionException.class);
        expectedException.expectMessage("This is an invalid transaction");
        walletTransaction.execute(walletService);
    }

    @Test
    public void should_return_true_when_state_is_executed() throws InvalidTransactionException {
        walletService = Mockito.mock(WalletServiceImpl.class);
        walletTransaction = Mockito.spy(new WalletTransaction(null, 1L, 2L, 100L, "orderId", 1.0));
        Mockito.doReturn(true).when(walletTransaction).checkIfExecuted();

        assertThat(walletTransaction.execute(walletService)).isEqualTo(true);
        Mockito.verify(walletService, Mockito.times(0)).moveMoney(anyString(), anyLong(), anyLong(), anyDouble());
    }

    @Test
    public void should_return_false_when_lock_failed() throws InvalidTransactionException {
        walletService = Mockito.mock(WalletServiceImpl.class);
        walletTransaction = new WalletTransaction("id", 1L, 2L, 100L, "orderId", 1.0);

        mockStatic(RedisDistributedLock.class);
        RedisDistributedLock lock = Mockito.spy(new RedisDistributedLock());

        Mockito.doReturn(false).when(lock).lock(anyString());
        PowerMockito.when(RedisDistributedLock.getSingletonInstance()).thenReturn(lock);

        assertThat(walletTransaction.execute(walletService)).isEqualTo(false);
        Mockito.verify(walletService, Mockito.times(0)).moveMoney(anyString(), anyLong(), anyLong(), anyDouble());
    }

    @Test
    public void should_return_true_when_double_check_state_is_executed() throws InvalidTransactionException {
        walletService = Mockito.mock(WalletServiceImpl.class);
        walletTransaction = Mockito.spy(new WalletTransaction("id", 1L, 2L, 100L, "orderId", 1.0));

        mockStatic(RedisDistributedLock.class);
        RedisDistributedLock lock = Mockito.spy(new RedisDistributedLock());

        Mockito.doReturn(true).when(lock).lock(anyString());
        Mockito.doNothing().when(lock).unlock(anyString());
        Mockito.doReturn(false, true).when(walletTransaction).checkIfExecuted();
        PowerMockito.when(RedisDistributedLock.getSingletonInstance()).thenReturn(lock);

        assertThat(walletTransaction.execute(walletService)).isEqualTo(true);
        Mockito.verify(walletService, Mockito.times(0)).moveMoney(anyString(), anyLong(), anyLong(), anyDouble());
    }

    @Test
    public void should_return_false_when_is_expired() throws InvalidTransactionException {
        walletService = Mockito.mock(WalletServiceImpl.class);
        walletTransaction = Mockito.spy(new WalletTransaction("id", 1L, 2L, 100L, "orderId", 1.0));

        mockStatic(RedisDistributedLock.class);
        RedisDistributedLock lock = Mockito.spy(new RedisDistributedLock());

        Mockito.doReturn(true).when(lock).lock(anyString());
        Mockito.doNothing().when(lock).unlock(anyString());
        Mockito.doReturn(true).when(walletTransaction).checkIfExpired();
        PowerMockito.when(RedisDistributedLock.getSingletonInstance()).thenReturn(lock);

        assertThat(walletTransaction.execute(walletService)).isEqualTo(false);
        assertThat(walletTransaction.getStatus()).isEqualTo(STATUS.EXPIRED);
        Mockito.verify(walletService, Mockito.times(0)).moveMoney(anyString(), anyLong(), anyLong(), anyDouble());
    }

    @Test
    public void should_return_true_when_move_money_success() throws InvalidTransactionException {
        walletService = Mockito.mock(WalletServiceImpl.class);
        walletTransaction = Mockito.spy(new WalletTransaction("id", 1L, 2L, 100L, "orderId", 1.0));

        mockStatic(RedisDistributedLock.class);
        RedisDistributedLock lock = Mockito.spy(new RedisDistributedLock());

        Mockito.doReturn(true).when(lock).lock(anyString());
        Mockito.doNothing().when(lock).unlock(anyString());
        Mockito.doReturn(false).when(walletTransaction).checkIfExpired();
        Mockito.doReturn("walletTransactionId").when(walletService).moveMoney(anyString(), anyLong(), anyLong(), anyDouble());
        PowerMockito.when(RedisDistributedLock.getSingletonInstance()).thenReturn(lock);

        assertThat(walletTransaction.execute(walletService)).isEqualTo(true);
        assertThat(walletTransaction.getStatus()).isEqualTo(STATUS.EXECUTED);
    }

    @Test
    public void should_return_false_when_move_money_failed() throws InvalidTransactionException {
        walletService = Mockito.mock(WalletServiceImpl.class);
        walletTransaction = Mockito.spy(new WalletTransaction("id", 1L, 2L, 100L, "orderId", 1.0));

        mockStatic(RedisDistributedLock.class);
        RedisDistributedLock lock = Mockito.spy(new RedisDistributedLock());

        Mockito.doReturn(true).when(lock).lock(anyString());
        Mockito.doNothing().when(lock).unlock(anyString());
        Mockito.doReturn(false).when(walletTransaction).checkIfExpired();
        Mockito.doReturn(null).when(walletService).moveMoney(anyString(), anyLong(), anyLong(), anyDouble());
        PowerMockito.when(RedisDistributedLock.getSingletonInstance()).thenReturn(lock);

        assertThat(walletTransaction.execute(walletService)).isEqualTo(false);
        assertThat(walletTransaction.getStatus()).isEqualTo(STATUS.FAILED);
    }
}