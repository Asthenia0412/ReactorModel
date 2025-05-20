package com.xiaoyongcai.io.SingleReactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class EchoServer {
    public static void main(String[] args) throws IOException {
        new Thread(new Reactor(8080)).start();
        System.out.println("EchoServer started on port 8080");
    }

    static class Reactor implements Runnable {
        final Selector selector;
        final ServerSocketChannel serverSocket;

        Reactor(int port) throws IOException {
            selector = Selector.open();
            serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(port));
            serverSocket.configureBlocking(false);
            SelectionKey sk = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            sk.attach(new Acceptor());
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    selector.select();
                    Set<SelectionKey> selected = selector.selectedKeys();
                    Iterator<SelectionKey> it = selected.iterator();
                    while (it.hasNext()) {
                        dispatch(it.next());
                    }
                    selected.clear();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        void dispatch(SelectionKey key) {
            Runnable r = (Runnable) key.attachment();
            if (r != null) {
                r.run();
            }
        }

        class Acceptor implements Runnable {
            @Override
            public void run() {
                try {
                    SocketChannel c = serverSocket.accept();
                    if (c != null) {
                        new EchoHandler(selector, c);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    static class EchoHandler implements Runnable {
        final SocketChannel socket;
        final SelectionKey sk;
        ByteBuffer input = ByteBuffer.allocate(1024);

        EchoHandler(Selector sel, SocketChannel c) throws IOException {
            socket = c;
            c.configureBlocking(false);
            sk = socket.register(sel, SelectionKey.OP_READ);
            sk.attach(this);
            sel.wakeup();
        }

        @Override
        public void run() {
            try {
                socket.read(input);
                if (input.position() > 0) {
                    // 处理Echo逻辑
                    input.flip();

                    byte[] bytes = new byte[input.remaining()];
                    input.get(bytes);
                    String received = new String(bytes);
                    System.out.println("Server received: " + received);

                    // 原样返回
                    ByteBuffer output = ByteBuffer.wrap(bytes);
                    while (output.hasRemaining()) {
                        socket.write(output);
                    }
                    Thread.sleep(1000000);
                    // 重置状态，准备下一次读取
                    input.clear();
                    sk.interestOps(SelectionKey.OP_READ);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}