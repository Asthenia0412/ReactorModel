package com.xiaoyongcai.io.MultiReactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiThreadEchoServer {
    public static void main(String[] args) throws IOException {
        new Thread(new MainReactor(8080)).start();
        System.out.println("MultiThreadEchoServer started on port 8080");
    }

    static class MainReactor implements Runnable {
        final Selector selector;
        final ServerSocketChannel serverSocket;
        final SubReactor[] subReactors;
        final AtomicInteger next = new AtomicInteger(0);
        volatile boolean running = true;

        MainReactor(int port) throws IOException {
            selector = Selector.open();
            serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(port));
            serverSocket.configureBlocking(false);
            SelectionKey sk = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            sk.attach(new Acceptor());

            int cpuCores = Runtime.getRuntime().availableProcessors();
            subReactors = new SubReactor[cpuCores];
            for (int i = 0; i < subReactors.length; i++) {
                subReactors[i] = new SubReactor();
                new Thread(subReactors[i]).start();
            }
        }

        @Override
        public void run() {
            try {
                while (running) {
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
            } finally {
                try {
                    selector.close();
                    serverSocket.close();
                    for (SubReactor subReactor : subReactors) {
                        subReactor.stop();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
                        SubReactor subReactor = subReactors[next.getAndIncrement() % subReactors.length];
                        subReactor.register(c);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    static class SubReactor implements Runnable {
        final Selector selector;
        final ExecutorService workerPool;
        volatile boolean running = true;

        SubReactor() throws IOException {
            selector = Selector.open();
            workerPool = Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors() * 2);
        }

        void register(SocketChannel c) throws IOException {
            new Handler(selector, c, workerPool);
        }

        void stop() {
            running = false;
            workerPool.shutdown();
            selector.wakeup();
        }

        @Override
        public void run() {
            try {
                while (running) {
                    selector.select();
                    Set<SelectionKey> selected = selector.selectedKeys();
                    Iterator<SelectionKey> it = selected.iterator();
                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        it.remove();
                        Runnable handler = (Runnable) key.attachment();
                        if (handler != null) {
                            handler.run();
                        }
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                try {
                    selector.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class Handler implements Runnable {
        final SocketChannel socket;
        final SelectionKey sk;
        final ExecutorService workerPool;
        final Selector selector;
        final ByteBuffer input = ByteBuffer.allocate(1024);
        static final int READING = 0, PROCESSING = 1, SENDING = 2;
        volatile int state = READING;

        Handler(Selector sel, SocketChannel c, ExecutorService workerPool) throws IOException {
            socket = c;
            this.workerPool = workerPool;
            this.selector = sel;
            c.configureBlocking(false);
            sk = socket.register(sel, SelectionKey.OP_READ);
            sk.attach(this);
            sel.wakeup();
        }

        @Override
        public void run() {
            try {
                if (state == READING) {
                    read();
                } else if (state == SENDING) {
                    send();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                close();
            }
        }

        void read() throws IOException {
            int length = socket.read(input);
            if (length > 0) {
                state = PROCESSING;
                workerPool.execute(new Processer());
            } else if (length == -1) {
                close();
            }
        }

        void send() throws IOException {
            input.flip();
            socket.write(input);
            if (input.remaining() == 0) {
                state = READING;
                sk.interestOps(SelectionKey.OP_READ);
                input.clear();
            }
        }

        synchronized void close() {
            try {
                if (sk != null) {
                    sk.cancel();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        class Processer implements Runnable {
            @Override
            public void run() {
                try {
                    Thread.sleep(100); // Simulate processing
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                state = SENDING;
                sk.interestOps(SelectionKey.OP_WRITE);
                selector.wakeup();
            }
        }
    }
}