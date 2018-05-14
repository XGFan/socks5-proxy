import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;

public class UdpEchoServer {

    public static void main(String[] args) throws InterruptedException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        final Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.TRACE))
                                .addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                                        System.out.println(msg);
                                        ByteBuf buf = msg.content();
                                        String response = buf.toString(CharsetUtil.UTF_8) + " x 2";
                                        System.out.println(msg.sender());
                                        ctx.writeAndFlush(new DatagramPacket(
                                                Unpooled.copiedBuffer(response, CharsetUtil.UTF_8),
                                                msg.sender()
                                        ));
                                    }
                                });
                    }
                });

        ChannelFuture sync = bootstrap.bind("127.0.0.1", 9999).sync();
//        final DatagramPacket msg = new DatagramPacket(Unpooled.copiedBuffer("hello from netty".getBytes(Charset.forName("UTF-8"))), new InetSocketAddress("127.0.0.1", 9999));
//        final ChannelFuture sync1 = sync.channel().writeAndFlush(msg).sync();
    }
}
