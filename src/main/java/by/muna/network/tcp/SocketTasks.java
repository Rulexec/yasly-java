package by.muna.network.tcp;

import java.util.function.Consumer;

class SocketTasks {
    public static enum SocketTaskType {
        REGISTER, UNREGISTER,

        WRITE_REQUEST, CLOSE
    }
    public static enum SocketType {
        SERVER, SOCKET
    }

    public static class SocketTask {
        public SocketTaskType type;

        public SocketTask(SocketTaskType type) {
            this.type = type;
        }
    }

    public static class SocketSocketTask extends SocketTask {
        public TCPSocket socket;

        public SocketSocketTask(SocketTaskType type, TCPSocket socket) {
            super(type);
            this.socket = socket;
        }
    }

    public static class SocketOrServerTask extends SocketTask {
        public SocketType socketType;
        public Object socket;

        public SocketOrServerTask(SocketTaskType type, SocketType socketType, Object socket) {
            super(type);
            this.socketType = socketType;
            this.socket = socket;
        }
    }

    public static class SocketRegisterTask extends SocketOrServerTask {
        public Consumer<Object> listener;

        public SocketRegisterTask(SocketType socketType, Object socket, Consumer<Object> listener) {
            super(SocketTaskType.REGISTER, socketType, socket);
            this.listener = listener;
        }
    }
}
