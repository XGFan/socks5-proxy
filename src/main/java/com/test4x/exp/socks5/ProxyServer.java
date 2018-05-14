package com.test4x.exp.socks5;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class ProxyServer {

    public static void main(String[] args) throws InterruptedException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        final Socks5InitialRequestHandler socks5InitialRequestHandler = new Socks5InitialRequestHandler();
        final Proxy relay = new Proxy();
        try {
            serverBootstrap
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(10080)
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                    .addLast(new ProxyIdleHandler())
                                    .addLast(new LoggingHandler(LogLevel.TRACE))
                                    .addLast(Socks5ServerEncoder.DEFAULT)
//                                    .addLast(new IdleStateHandler(3, 30, 0))
                                    .addLast(new Socks5InitialRequestDecoder()) //入站decode
                                    .addLast(socks5InitialRequestHandler) //无需验证
                                    //socks connection
                                    .addLast(new Socks5CommandRequestDecoder())
                                    //Socks connection
                                    .addLast(relay);

                        }
                    });
            ChannelFuture future = serverBootstrap.bind().sync();
            future.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
