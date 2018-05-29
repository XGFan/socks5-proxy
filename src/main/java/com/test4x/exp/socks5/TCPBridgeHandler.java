package com.test4x.exp.socks5;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TCPBridgeHandler extends ChannelInboundHandlerAdapter {

    private final Logger logger = LoggerFactory.getLogger(TCPBridgeHandler.class);
    private Channel channel;

    public TCPBridgeHandler(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (channel.isWritable()) {
            try {
                channel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        logger.error("{}:From {} to {} failed", ctx.name(),
                                ctx.channel(), channel,
                                future.cause());
                        ctx.disconnect();
                    }
                });
            } catch (Exception e) {
                logger.error("{}:Forward Error", ctx.name(), e);
                ReferenceCountUtil.release(msg);
            }
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.debug("{} Disconnect,So do {}", ctx.channel(), channel);
        channel.close();
    }
}
