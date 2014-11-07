package by.muna.network.tcp;

import by.muna.data.IPair;
import by.muna.data.Pair;
import by.muna.monads.IAsyncFuture;
import by.muna.network.tcp.SocketTasks.SocketOrServerTask;
import by.muna.network.tcp.SocketTasks.SocketRegisterTask;
import by.muna.network.tcp.SocketTasks.SocketSocketTask;
import by.muna.network.tcp.SocketTasks.SocketTask;
import by.muna.network.tcp.SocketTasks.SocketTaskType;
import by.muna.network.tcp.SocketTasks.SocketType;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPSocketsThread implements ITCPSockets {
    private Thread thread;
    private Selector selector;

    private ConcurrentLinkedQueue<SocketTask> socketTasks = new ConcurrentLinkedQueue<>();

    private boolean stopped = false;

    public TCPSocketsThread() throws IOException {
        this.selector = Selector.open();

        this.thread = new Thread(this::run);
        this.thread.start();
    }

    public void join() throws InterruptedException {
        this.thread.join();
    }
    public void stop() {
        this.stopped = true;
        this.wakeupSelector();
    }

    private void wakeupSelector() {
        this.selector.wakeup();
    }

    @Override
    public IAsyncFuture<Object> register(ITCPServer iserver) {
        if (this.stopped) return callback -> callback.accept("stopped");

        TCPServer server = (TCPServer) iserver;

        return callback -> {
            this.socketTasks.add(new SocketRegisterTask(SocketType.SERVER, server, callback));
            this.wakeupSelector();
        };
    }

    @Override
    public IAsyncFuture<Object> register(ITCPSocket isocket) {
        if (this.stopped) return callback -> callback.accept("stopped");

        TCPSocket socket = (TCPSocket) isocket;

        return callback -> {
            socket.mothership = this;

            this.socketTasks.add(new SocketRegisterTask(SocketType.SOCKET, socket, callback));
            this.wakeupSelector();
        };
    }

    @Override
    public IAsyncFuture<Object> unregister(ITCPServer server) {
        throw new RuntimeException("Not implemented yet.");
    }

    @Override
    public IAsyncFuture<Object> unregister(ITCPSocket socket) {
        throw new RuntimeException("Not implemented yet.");
    }

    private void run() {
        Queue<IPair<TCPServer, Object>> serversToClose = new LinkedList<>();
        Queue<IPair<TCPSocket, Object>> socketsToClose = new LinkedList<>();

        working: while (!this.stopped) {
            while (!this.socketTasks.isEmpty()) {
                SocketTask task = this.socketTasks.poll();

                switch (task.type) {
                case REGISTER: {
                    SocketRegisterTask registerTask = (SocketRegisterTask) task;

                    switch (registerTask.socketType) {
                    case SERVER:
                        try {
                            ((TCPServer) registerTask.socket).serverChannel.register(
                                this.selector, SelectionKey.OP_ACCEPT, registerTask.socket
                            );
                            registerTask.listener.accept(null);
                        } catch (IOException ex) {
                            registerTask.listener.accept(ex);
                        }
                        break;
                    case SOCKET:
                        try {
                            TCPSocket socket = (TCPSocket) registerTask.socket;

                            int interest;
                            if (socket.channel.isConnectionPending()) {
                                interest = SelectionKey.OP_CONNECT;
                            } else {
                                interest = SelectionKey.OP_READ | (
                                    socket.isWritingRequested() ? SelectionKey.OP_WRITE : 0
                                );
                                socket._connection(null);
                            }

                            socket.channel.register(this.selector, interest, registerTask.socket);
                            registerTask.listener.accept(null);
                        } catch (IOException ex) {
                            registerTask.listener.accept(ex);
                        }
                        break;
                    default: throw new RuntimeException("Impossible: " + registerTask.socketType);
                    }
                } break;
                case UNREGISTER: throw new RuntimeException("Not implemented yet");
                case WRITE_REQUEST: {
                    SocketSocketTask writingTask = (SocketSocketTask) task;
                    SelectionKey key = writingTask.socket.channel.keyFor(this.selector);

                    if (key != null && key.isValid()) {
                        int oldInterest = key.interestOps();

                        if ((oldInterest & SelectionKey.OP_CONNECT) == 0) {
                            key.interestOps(oldInterest | SelectionKey.OP_WRITE);
                        }
                    } else {
                        socketsToClose.add(new Pair<>(writingTask.socket, null));
                    }
                } break;
                case CLOSE: {
                    SocketOrServerTask closeTask = (SocketOrServerTask) task;

                    switch (closeTask.socketType) {
                    case SERVER: serversToClose.add(new Pair<>((TCPServer) closeTask.socket, null)); break;
                    case SOCKET: socketsToClose.add(new Pair<>((TCPSocket) closeTask.socket, null)); break;
                    default: throw new RuntimeException("Impossible: " + closeTask.socketType);
                    }
                } break;
                default: throw new RuntimeException("Impossible: " + task.type);
                }
            }

            this.closeServersAndSockets(serversToClose, socketsToClose);

            selection: {
                int selected;

                try {
                    selected = this.selector.select();
                } catch (IOException ex) {
                    throw new RuntimeException("Not implemented yet.", ex);
                }

                //System.out.println("wazzup, selected: " + selected);

                if (selected == 0) break selection;

                Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    Object attachment = key.attachment();

                    // Обрабатываем сервера отдельно
                    try {
                        if (key.isAcceptable()) {
                            TCPServer server = (TCPServer) attachment;
                            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

                            SocketChannel newChannel = serverChannel.accept();
                            newChannel.configureBlocking(false);

                            SelectionKey socketKey = newChannel.register(this.selector, SelectionKey.OP_READ);

                            TCPSocket newSocketController = new TCPSocket(this, newChannel);
                            socketKey.attach(newSocketController);
                            //System.out.println("accepted, bro");

                            server._connected(newSocketController);

                            continue;
                        }
                    } catch (IOException ex) {
                        serversToClose.add(new Pair<>((TCPServer) attachment, ex));
                    }

                    TCPSocket socket = (TCPSocket) attachment;

                    if (socket.closed) {
                        socketsToClose.add(new Pair<>(socket, null));
                        continue;
                    }

                    try {
                        if ((key.interestOps() & SelectionKey.OP_CONNECT) != 0) {
                            try {
                                if (key.isConnectable() && socket.channel.finishConnect()) {
                                    key.interestOps(SelectionKey.OP_READ |
                                        (socket.isWritingRequested() ? SelectionKey.OP_WRITE : 0));

                                    socket._connection(null);
                                } else {
                                    continue;
                                }
                            } catch (IOException ex) {
                                socket._connection(ex);
                                throw ex;
                            }
                        }

                        if (key.isReadable()) {
                            //System.out.println("yo, readable");
                            socket._receive();
                        }

                        if (key.isWritable()) {
                            if (socket._send()) {
                                key.interestOps(SelectionKey.OP_READ);
                            }
                        }
                    } catch (IOException ex) {
                        socketsToClose.add(new Pair<>(socket, ex));
                    }
                }
            }

            this.closeServersAndSockets(serversToClose, socketsToClose);
        }

        /* We must:
         *   - clean tasks queue
         *   - close all active servers&sockets
         */

        while (!this.socketTasks.isEmpty()) {
            SocketTask task = this.socketTasks.poll();
            switch (task.type) {
            case REGISTER: {
                SocketRegisterTask registerTask = (SocketRegisterTask) task;

                switch (registerTask.socketType) {
                case SERVER: serversToClose.add(new Pair<>((TCPServer) registerTask.socket, null)); break;
                case SOCKET: socketsToClose.add(new Pair<>((TCPSocket) registerTask.socket, null)); break;
                default: throw new RuntimeException("Impossible: " + registerTask.socketType);
                }
            } break;
            case UNREGISTER: throw new RuntimeException("Not implemented yet");
            case WRITE_REQUEST: break;
            case CLOSE: break;
            default: throw new RuntimeException("Impossible: " + task.type);
            }
        }

        for (SelectionKey key : this.selector.keys()) {
            Object attachment = key.attachment();

            if (attachment instanceof TCPSocket) {
                socketsToClose.add(new Pair<>((TCPSocket) attachment, null)); break;
            } else if (attachment instanceof TCPServer) {
                serversToClose.add(new Pair<>((TCPServer) attachment, null));
            } else {
                throw new RuntimeException("Unknown attachment: " + attachment);
            }
        }

        this.closeServersAndSockets(serversToClose, socketsToClose);
    }

    private void closeServersAndSockets(
        Collection<IPair<TCPServer, Object>> servers, Collection<IPair<TCPSocket, Object>> sockets)
    {
        if (!servers.isEmpty()) this.closeServers(servers);
        if (!sockets.isEmpty()) this.closeSockets(sockets);
    }
    // Нужно быть аккуратным, ниже точь-в-точь такой же метод
    private void closeServers(Iterable<IPair<TCPServer, Object>> servers) {
        Iterator<IPair<TCPServer, Object>> it = servers.iterator();
        while (it.hasNext()) {
            IPair<TCPServer, Object> serverWithReason = it.next();
            it.remove();

            TCPServer server = serverWithReason.getFirst();
            Object reason = serverWithReason.getSecond();

            server._shutdown(reason);

            try {
                server.serverChannel.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            SelectionKey key = server.serverChannel.keyFor(this.selector);
            if (key != null) key.cancel();
        }
    }
    // Нужно быть аккуратным, выше точь-в-точь такой же метод
    private void closeSockets(Iterable<IPair<TCPSocket, Object>> sockets) {
        Iterator<IPair<TCPSocket, Object>> it = sockets.iterator();
        while (it.hasNext()) {
            IPair<TCPSocket, Object> socketWithReason = it.next();
            it.remove();

            TCPSocket socket = socketWithReason.getFirst();
            Object reason = socketWithReason.getSecond();

            socket._shutdown(reason);

            try {
                socket.channel.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            SelectionKey key = socket.channel.keyFor(this.selector);
            if (key != null) key.cancel();
        }
    }

    void requestWriting(TCPSocket socket) {
        this.socketTasks.add(new SocketSocketTask(SocketTaskType.WRITE_REQUEST, socket));
        this.wakeupSelector();
    }
    void closeMe(TCPServer server) {
        this.socketTasks.add(new SocketOrServerTask(SocketTaskType.CLOSE, SocketType.SERVER, server));
        this.wakeupSelector();
    }
    void closeMe(TCPSocket socket) {
        this.socketTasks.add(new SocketOrServerTask(SocketTaskType.CLOSE, SocketType.SOCKET, socket));
        this.wakeupSelector();
    }
}
