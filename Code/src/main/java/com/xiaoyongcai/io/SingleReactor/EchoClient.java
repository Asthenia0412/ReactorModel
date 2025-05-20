package com.xiaoyongcai.io.SingleReactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class EchoClient {
    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("localhost", 8080));

        Scanner scanner = new Scanner(System.in);
        System.out.println("Connected to EchoServer. Type 'exit' to quit.");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine();

            if ("exit".equalsIgnoreCase(input)) {
                break;
            }

            // 发送消息到服务器
            ByteBuffer writeBuffer = ByteBuffer.wrap(input.getBytes());
            socketChannel.write(writeBuffer);

            // 读取服务器响应
            ByteBuffer readBuffer = ByteBuffer.allocate(1024);
            int bytesRead = socketChannel.read(readBuffer);
            if (bytesRead > 0) {
                readBuffer.flip();
                byte[] bytes = new byte[readBuffer.remaining()];
                readBuffer.get(bytes);
                String response = new String(bytes);
                System.out.println("Server response: " + response);
            }
        }

        socketChannel.close();
        scanner.close();
        System.out.println("Disconnected from server");
    }
}