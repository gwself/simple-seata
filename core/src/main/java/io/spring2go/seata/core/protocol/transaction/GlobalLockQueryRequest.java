package io.spring2go.seata.core.protocol.transaction;

import io.spring2go.seata.core.protocol.MergedMessage;
import io.spring2go.seata.core.rpc.RpcContext;

/**
 * Created by william on May, 2020
 */
public class GlobalLockQueryRequest extends BranchRegisterRequest implements MergedMessage {
    @Override
    public short getTypeCode() {
        return TYPE_GLOBAL_LOCK_QUERY;
    }

    @Override
    public AbstractTransactionResponse handle(RpcContext rpcContext) {
        return handler.handle(this, rpcContext);
    }

}
