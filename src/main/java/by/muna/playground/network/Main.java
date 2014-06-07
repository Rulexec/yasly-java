package by.muna.playground.network;

import by.muna.callbacks.FinishListener;
import by.muna.network.tcp.IBufferReadable;
import by.muna.network.tcp.ITCPServerListener;
import by.muna.network.tcp.ITCPSendStatusListener;
import by.muna.network.tcp.ITCPSocket;
import by.muna.network.tcp.ITCPSocketListener;
import by.muna.network.tcp.TCPServer;
import by.muna.network.tcp.TCPSocketsThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {
    private static class User {
        public long userId;
        public Set<Integer> subscriptions = new HashSet<>();
        public boolean closed = false;
        public ITCPSocket socket;

        public User(long userId, ITCPSocket socket) {
            this.userId = userId;
        }
    }

    public static void main(String[] args) throws Exception {
        ITCPServerListener listener = new ITCPServerListener() {
            private long lastUserId = 0;
            private Map<Long, User> users = new HashMap<>();
            private Map<Integer, List<User>> channels = new HashMap<>();

            @Override
            public void onConnected(ITCPSocket socket) {
                final long userId = ++this.lastUserId;

                final User user = new User(userId, socket);
                this.users.put(userId, user);

                socket.setListener(new ITCPSocketListener() {
                    private ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 8); // максимальная длина пакета
                    { this.buffer.order(ByteOrder.BIG_ENDIAN); }

                    private boolean readingCmd = true;
                    private int cmd;

                    @Override public void onConnected() {}

                    @Override
                    public void onData(IBufferReadable reader) {
                        while (true) {
                            int readed = reader.read(this.buffer);
                            System.out.println(readed);
                            if (readed == 0) break;

                            if (this.readingCmd) {
                                if (this.buffer.position() >= 4) { // Прочитана команда
                                     this.readingCmd = false;

                                    this.cmd = this.buffer.getInt(0);

                                    if (!(this.cmd >= 1 && this.cmd <= 3)) {
                                        // Такой команды не существует
                                        //System.out.println("Closing");
                                        user.closed = true;
                                        socket.close();
                                        break;
                                    }
                                } else {
                                    continue;
                                }
                            }

                            switch (this.cmd) {
                            case 1:
                                if (this.buffer.position() >= 16) { // Считана команда, канал, мессадж
                                    int channelId = this.buffer.getInt(4);
                                    long message = this.buffer.getLong(8);

                                    List<User> subscribedUsers = channels.get(channelId);
                                    if (subscribedUsers == null) break;

                                    // FIXME: А можно не создавать каждый раз
                                    ByteBuffer messageBuffer = ByteBuffer.allocate(4 + 4 + 8 + 8);
                                    messageBuffer.order(ByteOrder.BIG_ENDIAN);
                                    messageBuffer.putInt(1);
                                    messageBuffer.putInt(channelId);
                                    messageBuffer.putLong(user.userId);
                                    messageBuffer.putLong(message);
                                    messageBuffer.position(0);

                                    Iterator<User> it = subscribedUsers.iterator();
                                    while (it.hasNext()) {
                                        User subscribedUser = it.next();
                                        if (subscribedUser.closed) {
                                            // Удаляем юзера из списка юзеров этого канала,
                                            // FIXME: Здесь есть проблема в том, что они удаляются только при
                                            // отправке сообщения в канал, но сейчас не критично.
                                            it.remove();
                                            continue;
                                        }

                                        subscribedUser.socket.requestWriting(
                                            () -> messageBuffer,
                                            new ITCPSendStatusListener() {
                                                @Override
                                                public void onSent() {
                                                    //
                                                }

                                                @Override
                                                public void onFail() {
                                                    subscribedUser.closed = true;
                                                    subscribedUser.socket.close();
                                                }
                                            }
                                        );
                                    }
                                }

                                break;
                            case 2: case 3:
                                if (this.buffer.position() >= 8) { // Считана команда и id канала
                                    int channelId = this.buffer.getInt(4);

                                    if (this.cmd == 2) { // Подписываемся
                                        boolean added = !user.subscriptions.add(channelId);

                                        if (added) {
                                            channels.compute(channelId, (k, subscribedUsers) -> {
                                                if (subscribedUsers == null) {
                                                    subscribedUsers = new LinkedList<>();
                                                }

                                                subscribedUsers.add(user);

                                                return subscribedUsers;
                                            });
                                        }
                                    } else { // Отписываемся
                                        user.subscriptions.remove(channelId);
                                    }
                                }
                                break;
                            default: throw new RuntimeException("Impossible: " + this.cmd);
                            }
                        }
                    }

                    @Override
                    public void onClosed() {
                        user.closed = true;
                        users.remove(userId);
                        //System.out.println("Closed! " + socket);
                    }
                });
            }

            @Override public void onStop() {}
        };

        TCPSocketsThread sockets = new TCPSocketsThread();

        TCPServer server = new TCPServer(new InetSocketAddress(3141));
        server.setListener(listener);

        sockets.register(server, new FinishListener<IOException>() {
            @Override
            public void onFinish(IOException result) {
                if (result != null) {
                    result.printStackTrace();
                }
            }
        });

        sockets.join();
        //TCPServerThread server = new TCPServerThread(new InetSocketAddress(3141), listener);

        //server.join();
    }
}
