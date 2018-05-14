package com.test4x.exp.socks5;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static io.netty.util.CharsetUtil.UTF_8;

@ChannelHandler.Sharable
public class Proxy extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {
    private static final Logger logger = LoggerFactory.getLogger(Proxy.class);

    @Override
    protected void channelRead0(final ChannelHandlerContext chc, DefaultSocks5CommandRequest msg) {
        logger.debug("目标服务器  : " + msg.type() + "," + msg.dstAddr() + "," + msg.dstPort());
        if (msg.type().equals(Socks5CommandType.CONNECT)) {
            logger.trace("准备连接目标服务器");
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(chc.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            //将目标服务器信息转发给客户端
                            ch.pipeline().addLast(new TCPBridgeHandler(chc.channel()));
                        }
                    });
            logger.trace("连接目标服务器");
            ChannelFuture future = bootstrap.connect(msg.dstAddr(), msg.dstPort());
            future.addListener((ChannelFutureListener) cfl -> {
                if (cfl.isSuccess()) {
                    logger.trace("成功连接目标服务器");
                    chc.pipeline().addLast(new TCPBridgeHandler(cfl.channel()));
                    chc.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4));
                } else {
                    chc.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4));
                }
            });
        } else if (msg.type().equals(Socks5CommandType.UDP_ASSOCIATE)) {
            logger.trace("UDP无需连接目标服务器");
            final Bootstrap udpServer = new Bootstrap()
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
            final ChannelFuture future = udpServer.bind(0); // 启动server
            future.addListener((ChannelFutureListener) cfl -> {
                final InetSocketAddress inetSocketAddress = (InetSocketAddress) cfl.channel().localAddress();
                final int port = inetSocketAddress.getPort();
                chc.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4,
                        ((InetSocketAddress) chc.channel().localAddress()).getHostString(),
                        port));
            });
            chc.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    logger.trace("TCP连接断开，UDP也即将断开");
                    future.channel().close();
                    super.channelInactive(ctx);
                }
            });
        }
    }

    private class TCPBridgeHandler extends ChannelInboundHandlerAdapter {
        private Channel channel;

        public TCPBridgeHandler(Channel channel) {
            this.channel = channel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            channel.writeAndFlush(msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            channel.close();
        }
    }

    private class UDPBridgeHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        private InetSocketAddress client = null;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            logger.trace("收到UDP消息! {} {}", msg.sender(), msg.content().toString(UTF_8));

            final ByteBuf content = msg.content();
            final InetSocketAddress sender = msg.sender();
            if (client == null || client.equals(sender)) {
                logger.trace("判断为Client发来的");
                client = sender;
                content.skipBytes(2);//skip RSV 保留字段
                final byte frag = content.readByte();
                final Socks5AddressType atyp = Socks5AddressType.valueOf(content.readByte());
                InetSocketAddress remote = null;
                if (atyp.equals(Socks5AddressType.IPv4)) {
                    final byte[] ipBytes = new byte[4];
                    content.readBytes(ipBytes);
                    final String ip = NetUtil.bytesToIpAddress(ipBytes);
                    final short port = content.readShort();
                    remote = new InetSocketAddress(ip, port);
                }
                final ByteBuf data = content.readBytes(content.readableBytes());
                ctx.channel().writeAndFlush(new DatagramPacket(data, remote));//把消息发到真正的远端
            } else {
                logger.trace("判断为Remote发来的");
                final Channel channel = ctx.channel();

                final byte[] fakeHeader = {0, 0,
                        0, 1,
                        0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0};
                final ByteBuf data = Unpooled.wrappedBuffer(Unpooled.wrappedBuffer(fakeHeader), content.retain());
                channel.writeAndFlush(new DatagramPacket(data, client));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
        }
    }

}
