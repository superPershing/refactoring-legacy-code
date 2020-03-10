package cn.xpbootcamp.legacy_code;

import cn.xpbootcamp.legacy_code.enums.STATUS;
import cn.xpbootcamp.legacy_code.service.WalletService;
import cn.xpbootcamp.legacy_code.utils.IdGenerator;
import cn.xpbootcamp.legacy_code.utils.RedisDistributedLock;

import javax.transaction.InvalidTransactionException;

public class WalletTransaction {
    private STATUS status;
    private String id;
    private Long buyerId;
    private Long sellerId;
    private Long productId;
    private String orderId;
    private Long createdTimestamp;
    private Double amount;


    public WalletTransaction(String preAssignedId, Long buyerId, Long sellerId, Long productId, String orderId, Double amount) {
        this.amount = amount;

        // FIXME
        if (preAssignedId != null && !preAssignedId.isEmpty()) {
            this.id = preAssignedId;
        } else {
            this.id = IdGenerator.generateTransactionId();
        }
        if (!this.id.startsWith("t_")) {
            this.id = "t_" + preAssignedId;
        }
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.productId = productId;
        this.orderId = orderId;
        this.status = STATUS.TO_BE_EXECUTED;
        this.createdTimestamp = System.currentTimeMillis();
    }

    public boolean execute(WalletService walletService) throws InvalidTransactionException {
        if (buyerId == null || sellerId == null || amount < 0.0) {
            throw new InvalidTransactionException("This is an invalid transaction");
        }
        if (checkIfExecuted()) {
            return true;
        }
        boolean isLocked = false;
        try {
            isLocked = RedisDistributedLock.getSingletonInstance().lock(id);

            if (!isLocked) {
                return false;
            }
            if (checkIfExecuted()) {
                return true; // double check
            }
            if (checkIfExpired()) {
                this.status = STATUS.EXPIRED;
                return false;
            }

            if (walletService.moveMoney(id, buyerId, sellerId, amount) != null) {
                this.status = STATUS.EXECUTED;
                return true;
            }
            this.status = STATUS.FAILED;
            return false;
        } finally {
            if (isLocked) {
                RedisDistributedLock.getSingletonInstance().unlock(id);
            }
        }
    }

    protected boolean checkIfExecuted() {
        return status == STATUS.EXECUTED;
    }

    protected boolean checkIfExpired() {
        return System.currentTimeMillis() - createdTimestamp > 1728000000;
    }

    public String getId() {
        return id;
    }

    public STATUS getStatus() {
        return status;
    }
}