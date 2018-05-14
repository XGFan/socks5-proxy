import net.dongliu.requests.Proxies;
import net.dongliu.requests.RawResponse;
import net.dongliu.requests.Requests;
import sockslib.client.Socks5;
import sockslib.client.Socks5DatagramSocket;
import sockslib.client.SocksProxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Test {

    public static void main(String[] args) throws Exception {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        testHttp();
        testUdp();
    }


    private static void testHttp() {
        RawResponse send = Requests.get("http://api.ipify.org/?format=text")
                .proxy(Proxies.socksProxy("127.0.0.1", 11080))
//                    .proxy(Proxies.socksProxy("127.0.0.1", 10080))
                .timeout(5000)
                .send();
        System.out.println(send.readToText());
    }

    private static void testUdp() throws IOException {
        SocksProxy proxy = new Socks5(new InetSocketAddress("localhost", 11080));
        DatagramSocket socket = new Socks5DatagramSocket(proxy);

        byte[] receiveData = new byte[1024];
        byte[] sendData = "Hello,Test".getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                InetAddress.getByName("localhost"), 9999);
        socket.send(sendPacket);
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        String modifiedSentence = new String(receivePacket.getData());
        System.out.println("FROM SERVER 1:" + modifiedSentence);


        socket.send(sendPacket);
        DatagramPacket receivePacket2 = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket2);
        String modifiedSentence2 = new String(receivePacket2.getData());
        System.out.println("FROM SERVER 2:" + modifiedSentence2);

        socket.close();
    }

}
