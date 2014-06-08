package by.muna.playground.network;

import by.muna.buffers.IBufferReadable;
import by.muna.network.tcp.ITCPSendStatusListener;
import by.muna.network.tcp.ITCPSocketListener;
import by.muna.network.tcp.TCPSocket;
import by.muna.network.tcp.TCPSocketsThread;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainClient {
    public static void main(String[] args) throws Exception {
        TCPSocketsThread sockets = new TCPSocketsThread();

        TCPSocket socket = new TCPSocket(new InetSocketAddress(3141));
        socket.setListener(new ITCPSocketListener() {
            @Override
            public void onConnected() {
                System.out.println("Connected!");
            }

            @Override
            public void onData(IBufferReadable reader) {
                ByteBuffer buffer = ByteBuffer.allocate(16);
                while (true) {
                    int readed = reader.read(buffer);
                    System.out.println("Readed: " + readed);
                    if (readed == 0) break;
                }
                //System.out.println("Data here!");
            }

            @Override
            public void onClosed() {
                System.out.println("Closed :(");
            }
        });

        sockets.register(socket, ex -> {
            if (ex != null) ex.printStackTrace();
            else System.out.println("registered");
        });

        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 8);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(2);
        buffer.putInt(42);
        buffer.flip();

        socket.requestWriting(() -> buffer, new ITCPSendStatusListener() {
            @Override
            public void onSent() {
                System.out.println("Sent");
            }

            @Override
            public void onFail() {
                System.out.println("Sending error");
            }
        });

        sockets.join();
    }
}
