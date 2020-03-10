package cn.xpbootcamp.legacy_code;

import cn.xpbootcamp.legacy_code.service.WalletService;
import cn.xpbootcamp.legacy_code.service.WalletServiceImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import javax.transaction.InvalidTransactionException;

public class WalletTransactionTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private WalletService walletService;
    private WalletTransaction walletTransaction;

    @Test
    public void should_return_false_when_trader_is_invalid() throws InvalidTransactionException {
        walletService = Mockito.mock(WalletServiceImpl.class);
        walletTransaction = new WalletTransaction(null, null, null, 100L, "orderId", 1.0);

        expectedException.expect(InvalidTransactionException.class);
        expectedException.expectMessage("This is an invalid transaction");
        walletTransaction.execute(walletService);
    }

}