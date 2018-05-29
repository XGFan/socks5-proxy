package com.test4x.exp.socks5;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public class Proxy extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {
    private final Logger logger = LoggerFactory.getLogger(Proxy.class);

    @Override
    protected void channelRead0(final ChannelHandlerContext chc, DefaultSocks5CommandRequest msg) {
        Bootstrap bootstrap = new Bootstrap();
        if (msg.type().equals(Socks5CommandType.CONNECT)) {
            bootstrap.group(chc.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            //将目标服务器信息转发给客户端
                            ch.pipeline()
                                    .addLast(new LoggingHandler(LogLevel.TRACE))
                                    .addLast(new TCPBridgeHandler(chc.channel()));
                        }
                    });
            ChannelFuture future = bootstrap.connect(msg.dstAddr(), msg.dstPort());
            future.addListener((ChannelFutureListener) cfl -> {
                if (cfl.isSuccess()) {
                    logger.trace("连接目标服务器成功");
                    chc.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, msg.dstAddrType()));//告知客户端
                    chc.pipeline().addLast(new TCPBridgeHandler(cfl.channel()));//添加转发handler
                } else {
                    logger.trace("连接目标服务器失败");
                    chc.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, msg.dstAddrType()));//告知客户端
                }
            });
        } else if (msg.type().equals(Socks5CommandType.UDP_ASSOCIATE)) {
            logger.trace("UDP无需连接目标服务器");
            final Bootstrap udpServer = bootstrap
                    .group(chc.channel().eventLoop())
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer() {
                                 @Override
                                 protected void initChannel(Channel ch) throws Exception {
                                     ch.pipeline()
                                             .addLast(new LoggingHandler(LogLevel.TRACE))
                                             .addLast(new UDPBridgeHandler());
                                 }
                             }
                    );
            udpServer.bind(0) // 启动server
                    .addListener((ChannelFutureListener) cfl -> {
                        final InetSocketAddress address = (InetSocketAddress) cfl.channel().localAddress(); //获取启动server所绑定的地址
                        chc.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, msg.dstAddrType(),
                                ((InetSocketAddress) chc.channel().localAddress()).getHostString(),
                                address.getPort()));
                        chc.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                logger.trace("TCP连接断开，UDP也即将断开");
                                cfl.channel().close();
                                super.channelInactive(ctx);
                            }
                        });
                    });
        }
    }
}
