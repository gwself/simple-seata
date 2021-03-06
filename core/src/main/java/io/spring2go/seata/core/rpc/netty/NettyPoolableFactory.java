package io.spring2go.seata.core.rpc.netty;

import io.netty.channel.Channel;
import io.spring2go.seata.common.exception.FrameworkException;
import io.spring2go.seata.common.util.NetUtil;
import io.spring2go.seata.core.protocol.RegisterRMResponse;
import io.spring2go.seata.core.protocol.RegisterTMResponse;
import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.spring2go.seata.core.rpc.netty.NettyPoolKey.TransactionRole;

import java.net.InetSocketAddress;

/**
 * Created by william on May, 2020
 */
public class NettyPoolableFactory implements KeyedPoolableObjectFactory<NettyPoolKey, Channel> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyPoolableFactory.class);
    private final AbstractRpcRemotingClient rpcRemotingClient;

    /**
     * Instantiates a new Netty key poolable factory.
     *
     * @param rpcRemotingClient the rpc remoting client
     */
    public NettyPoolableFactory(AbstractRpcRemotingClient rpcRemotingClient) {
        this.rpcRemotingClient = rpcRemotingClient;
        this.rpcRemotingClient.setChannelHandlers(rpcRemotingClient);
        this.rpcRemotingClient.start();
    }

    @Override
    public Channel makeObject(NettyPoolKey key) throws Exception {
        InetSocketAddress address = NetUtil.toInetSocketAddress(key.getAddress());
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("NettyPool create channel to " + key);
        }
        Channel tmpChannel = rpcRemotingClient.getNewChannel(address);
        long start = System.currentTimeMillis();
        Object response = null;
        Channel channelToServer = null;
        if (null == key.getMessage()) {
            throw new FrameworkException(
                    "register msg is null, role:" + key.getTransactionRole().name());
        }
        try {
            response = rpcRemotingClient.sendAsyncRequestWithResponse(null, tmpChannel, key.getMessage());
            if (!isResponseSuccess(response, key.getTransactionRole())) {
                rpcRemotingClient.onRegisterMsgFail(key.getAddress(), tmpChannel, response, key.getMessage());
            } else {
                channelToServer = tmpChannel;
                rpcRemotingClient.onRegisterMsgSuccess(key.getAddress(), tmpChannel, response,
                        key.getMessage());
            }
        } catch (Exception exx) {
            if (tmpChannel != null) { tmpChannel.close(); }
            throw new FrameworkException(
                    "register error,role:" + key.getTransactionRole().name() + ",err:" + exx.getMessage());
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(
                    "register sucesss, cost " + (System.currentTimeMillis() - start) + " ms, version:"
                            + getVersion(response, key.getTransactionRole()) + ",role:" + key.getTransactionRole().name()
                            + ",channel:" + channelToServer);
        }
        return channelToServer;
    }

    private boolean isResponseSuccess(Object response, TransactionRole transactionRole) {
        if (null == response) { return false; }
        if (transactionRole.equals(TransactionRole.TMROLE)) {
            if (!(response instanceof RegisterTMResponse)) {
                return false;
            }
            if (((RegisterTMResponse)response).isIdentified()) {
                return true;
            }
        } else if (transactionRole.equals(TransactionRole.RMROLE)) {
            if (!(response instanceof RegisterRMResponse)) {
                return false;
            }
            if (((RegisterRMResponse)response).isIdentified()) {
                return true;
            }
        }
        return false;
    }

    private String getVersion(Object response, TransactionRole transactionRole) {
        if (transactionRole.equals(TransactionRole.TMROLE)) {
            return ((RegisterTMResponse)response).getVersion();
        } else {
            return ((RegisterRMResponse)response).getVersion();
        }
    }

    @Override
    public void destroyObject(NettyPoolKey key, Channel channel) throws Exception {

        if (null != channel) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("will destroy channel:" + channel);
            }
            channel.disconnect();
            channel.close();
        }
    }

    @Override
    public boolean validateObject(NettyPoolKey key, Channel obj) {
        if (null != obj && obj.isActive()) {
            return true;
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("channel valid false,channel:" + obj);
        }
        return false;
    }

    @Override
    public void activateObject(NettyPoolKey key, Channel obj) throws Exception {

    }

    @Override
    public void passivateObject(NettyPoolKey key, Channel obj) throws Exception {

    }
}
