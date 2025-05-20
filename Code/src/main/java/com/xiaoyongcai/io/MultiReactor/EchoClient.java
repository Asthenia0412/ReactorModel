package com.xiaoyongcai.io.MultiReactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EchoClient {
    public static void main(String[] args) throws IOException {
        // 单客户端模式
        if (args.length == 0) {
            startSingleClient();
        }
        // 多客户端测试模式
        else if (args.length == 2 && "-t".equals(args[0])) {
            int threadCount = Integer.parseInt(args[1]);
            startMultiClients(threadCount);
        } else {
            System.out.println("Usage:");
            System.out.println("  Single client: java EchoClient");
            System.out.println("  Multi clients: java EchoClient -t <threadCount>");
        }
    }

    private static void startSingleClient() throws IOException {
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

    private static void startMultiClients(int threadCount) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int clientId = i + 1;
            executor.execute(() -> {
                try {
                    SocketChannel socketChannel = SocketChannel.open();
                    socketChannel.connect(new InetSocketAddress("localhost", 8080));

                    String message = "Hello from client " + clientId;
                    ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
                    socketChannel.write(buffer);

                    ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                    int bytesRead = socketChannel.read(readBuffer);
                    if (bytesRead > 0) {
                        readBuffer.flip();
                        byte[] bytes = new byte[readBuffer.remaining()];
                        readBuffer.get(bytes);
                        String response = new String(bytes);
                        System.out.println("Client " + clientId + " received: " + response);
                    }

                    socketChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executor.shutdown();
        System.out.println("All clients finished");
    }
}