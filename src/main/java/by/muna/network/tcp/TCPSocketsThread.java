package by.muna.network.tcp;

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
import java.util.function.Consumer;

public class TCPSocketsThread implements ITCPSockets {
    private Thread thread;
    private Selector selector;

    private ConcurrentLinkedQueue<SocketTask> socketTasks = new ConcurrentLinkedQueue<>();

    public TCPSocketsThread() throws IOException {
        this.selector = Selector.open();

        this.thread = new Thread(this::run);
        this.thread.start();
    }

    public void join() throws InterruptedException {
        this.thread.join();
    }

    private void wakeupSelector() {
        this.selector.wakeup();
    }

    @Override
    public void register(ITCPServer iserver, Consumer<IOException> listener) {
        /*TCPServer server = (TCPServer) iserver;

        // Понятия не имею как, но пробуждение перед регистрацией чинит отсутствие реакции
        // на wakeup после регистрации.
        this.wakeupSelector();

        SelectionKey key = server.serverChannel.register(this.selector, SelectionKey.OP_ACCEPT, server);*/

        TCPServer server = (TCPServer) iserver;
        this.socketTasks.add(new SocketRegisterTask(SocketType.SERVER, server, listener));
        this.wakeupSelector();
    }

    @Override
    public void register(ITCPSocket isocket, Consumer<IOException> listener) {
        /*TCPSocket socket = (TCPSocket) isocket;

        this.wakeupSelector();

        if (socket.channel.isConnectionPending()) {
            socket.channel.register(this.selector, SelectionKey.OP_CONNECT, socket);
        } else {
            socket.channel.register(this.selector, SelectionKey.OP_READ, socket);
        }*/

        TCPSocket socket = (TCPSocket) isocket;
        socket.mothership = this;

        this.socketTasks.add(new SocketRegisterTask(SocketType.SOCKET, socket, listener));
        this.wakeupSelector();
    }

    @Override
    public void unregister(ITCPServer server, Consumer<IOException> listener) {
        throw new RuntimeException("Not implemented yet.");
    }

    @Override
    public void unregister(ITCPSocket socket, Consumer<IOException> listener) {
        throw new RuntimeException("Not implemented yet.");
    }

    private void run() {
        Queue<TCPServer> serversToClose = new LinkedList<>();
        Queue<TCPSocket> socketsToClose = new LinkedList<>();

        while (true) {
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
                            registerTask.finishListener.accept(null);
                        } catch (IOException ex) {
                            registerTask.finishListener.accept(ex);
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
                            }

                            socket.channel.register(this.selector, interest, registerTask.socket);
                            registerTask.finishListener.accept(null);
                        } catch (IOException ex) {
                            registerTask.finishListener.accept(ex);
                        }
                        break;
                    default: throw new RuntimeException("Impossible: " + registerTask.socketType);
                    }
                } break;
                case WRITE_REQUEST: {
                    SocketSocketTask writingTask = (SocketSocketTask) task;
                    SelectionKey key = writingTask.socket.channel.keyFor(this.selector);

                    if (key != null && key.isValid()) {
                        int oldInterest = key.interestOps();

                        if ((oldInterest & SelectionKey.OP_CONNECT) == 0) {
                            key.interestOps(oldInterest | SelectionKey.OP_WRITE);
                        }
                    } else {
                        socketsToClose.add(writingTask.socket);
                    }
                } break;
                case CLOSE: {
                    SocketOrServerTask closeTask = (SocketOrServerTask) task;

                    switch (closeTask.socketType) {
                    case SERVER: serversToClose.add((TCPServer) closeTask.socket); break;
                    case SOCKET: socketsToClose.add((TCPSocket) closeTask.socket); break;
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

                            server.onConnected(newSocketController);

                            continue;
                        }
                    } catch (IOException ex) {
                        serversToClose.add((TCPServer) attachment);
                    }

                    TCPSocket socket = (TCPSocket) attachment;

                    if (socket.closed) {
                        socketsToClose.add(socket);
                        continue;
                    }

                    try {
                        if ((key.interestOps() & SelectionKey.OP_CONNECT) != 0) {
                            if (key.isConnectable() && socket.channel.finishConnect()) {
                                key.interestOps(SelectionKey.OP_READ);

                                socket.onConnected();
                            } else {
                                continue;
                            }
                        }

                        if (key.isReadable()) {
                            //System.out.println("yo, readable");
                            socket.receive();
                        }

                        if (key.isWritable()) {
                            if (socket.send()) {
                                key.interestOps(SelectionKey.OP_READ);
                            }
                        }
                    } catch (IOException ex) {
                        socketsToClose.add(socket);
                    }
                }
            }

            this.closeServersAndSockets(serversToClose, socketsToClose);
        }
    }

    private void closeServersAndSockets(Collection<TCPServer> servers, Collection<TCPSocket> sockets) {
        if (!servers.isEmpty()) this.closeServers(servers);
        if (!sockets.isEmpty()) this.closeSockets(sockets);
    }
    // Нужно быть аккуратным, ниже точь-в-точь такой же метод
    private void closeServers(Iterable<TCPServer> servers) {
        Iterator<TCPServer> it = servers.iterator();
        while (it.hasNext()) {
            TCPServer server = it.next();
            it.remove();

            server.onStop();

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
    private void closeSockets(Iterable<TCPSocket> sockets) {
        Iterator<TCPSocket> it = sockets.iterator();
        while (it.hasNext()) {
            TCPSocket socket = it.next();
            it.remove();

            socket.onClosed();

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
