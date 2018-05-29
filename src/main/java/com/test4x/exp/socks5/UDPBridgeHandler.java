package com.test4x.exp.socks5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

class UDPBridgeHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final Logger logger = LoggerFactory.getLogger(UDPBridgeHandler.class);

    private InetSocketAddress client = null;//正常情况下（如果没有被扫描的话），第一个udp信息应该是来自于client

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        final ByteBuf content = msg.content();
        final InetSocketAddress sender = msg.sender();
        if (client == null) {
            client = sender;
        }
        if (client.equals(sender)) { //简单判断来源
            logger.trace("Data from Client");
            content.skipBytes(2);//skip RSV 保留字段
            final byte frag = content.readByte(); //frag 1
            final Socks5AddressType atyp = Socks5AddressType.valueOf(content.readByte()); //ip类型 1
            InetSocketAddress remote = null;
            if (atyp.equals(Socks5AddressType.IPv4)) { //暂时只处理ipv4
                final byte[] ipBytes = new byte[4]; //ipv4 4
                content.readBytes(ipBytes);
                final String ip = NetUtil.bytesToIpAddress(ipBytes);
                final short port = content.readShort();
                remote = new InetSocketAddress(ip, port);
            }
            final ByteBuf data = content.readBytes(content.readableBytes());
            ctx.channel().writeAndFlush(new DatagramPacket(data, remote));//把消息发到真正的远端
        } else {
            logger.trace("Data from Remote");
            final Channel channel = ctx.channel();
            final byte[] fakeHeader = {0, 0,     //rsv
                    0, 1,               //frgs iptype
                    0, 0, 0, 0,         //ip
                    0, 0};              //port
            final ByteBuf data = Unpooled.wrappedBuffer(Unpooled.wrappedBuffer(fakeHeader), content.retain());
            channel.writeAndFlush(new DatagramPacket(data, client));//把消息发到client
        }
    }
}
