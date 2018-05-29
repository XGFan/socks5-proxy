package com.test4x.exp.socks5.test;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Http（TCP）服务端
 * 用于测试
 */
public class WebIpServer {
    public static void main(String[] args) throws Exception {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");//简单设置日志
        final ServerBootstrap bootstrap = new ServerBootstrap();
        final Logger logger = LoggerFactory.getLogger(WebIpServer.class);
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();
        try {
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                    .addLast(new LoggingHandler(LogLevel.TRACE))
                                    .addLast(new HttpRequestDecoder())
                                    .addLast(new HttpResponseEncoder())
                                    .addLast(new HttpObjectAggregator(100 * 1024 * 1024))
                                    .addLast(new SimpleChannelInboundHandler<HttpRequest>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
                                            logger.info("{}", msg.headers().entries());
                                            final String ip = ((InetSocketAddress) ctx.channel().localAddress()).getHostString();
                                            final DefaultFullHttpResponse response =
                                                    new DefaultFullHttpResponse(msg.protocolVersion(), HttpResponseStatus.OK);
                                            response.content().writeCharSequence(ip, StandardCharsets.UTF_8);
                                            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                                        }
                                    });

                        }
                    });

            final Channel ch = bootstrap.bind(8080).sync().channel();
            ch.closeFuture().sync();
        } finally {
            boss.shutdownGracefully().sync();
            worker.shutdownGracefully().sync();
        }
    }
}
