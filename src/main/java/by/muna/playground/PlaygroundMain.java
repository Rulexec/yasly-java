package by.muna.playground;

import by.muna.io.IByteReader;
import by.muna.io.IByteWriter;
import by.muna.network.tcp.ITCPSocket;
import by.muna.network.tcp.TCPSocket;
import by.muna.network.tcp.TCPSocketsThread;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class PlaygroundMain {
    public static void main(String[] args) throws Exception {
        TCPSocketsThread sockets = new TCPSocketsThread();

        ITCPSocket socket = new TCPSocket(new InetSocketAddress("google.com", 80));

        socket.getInputStream().onData(new Consumer<IByteReader>() {
            private int buffersCount = 0;
            private List<byte[]> buffers = new LinkedList<byte[]>();

            private final int BUFFER_SIZE = 1024;
            private byte[] buffer = new byte[BUFFER_SIZE];
            private int offset = 0;

            private boolean ended = false;

            @Override public void accept(IByteReader reader) {
                while (true) {
                    int readed = reader.read(this.buffer, this.offset);
                    if (readed == 0) break;

                    this.offset += readed;

                    if (offset == this.BUFFER_SIZE) {
                        this.buffers.add(this.buffer);
                        this.buffersCount++;

                        this.buffer = new byte[this.BUFFER_SIZE];
                        this.offset = 0;
                    }
                }

                if (reader.isEnd() && !this.ended) {
                    this.ended = true;

                    byte[] fullInput = new byte[this.buffersCount * this.BUFFER_SIZE + this.offset];

                    int off = 0;
                    for (byte[] buff : this.buffers) {
                        System.arraycopy(buff, 0, fullInput, off, this.BUFFER_SIZE);
                        off += this.BUFFER_SIZE;
                    }

                    System.arraycopy(this.buffer, 0, fullInput, off, this.offset);

                    System.out.println("data: " + new String(fullInput, Charset.forName("UTF-8")));
                }
            }
        });

        socket.getOutputStream().onCanWrite(new Function<IByteWriter, Boolean>() {
            private byte[] toWrite = "GET / HTTP/1.1\r\nHost: google.com\r\nConnection: close\r\n\r\n".getBytes();
            private int offset = 0;

            @Override
            public Boolean apply(IByteWriter writer) {
                this.offset += writer.write(this.toWrite, this.offset);

                boolean done = this.offset == this.toWrite.length;

                if (done) {
                    //socket.getOutputStream().end();
                }

                return done;
            }
        });
        socket.getOutputStream().requestWriting();

        socket.onConnection().run(x -> {
            if (x == null) {
                System.out.println(
                    "Connected! Local address: " + socket.getLocalAddress() +
                    ". Remote: " + socket.getRemoteAddress());
            } else {
                System.out.println("Connection error: " + x);
            }
        });
        socket.onShutdown().run(x -> {
            System.out.println("shutdown: " + (x == null ? "no error" : x));
            sockets.stop();
        });

        sockets.register(socket).run(x -> System.out.println(x != null ? ("exception: " + x) : "socket registered"));
        sockets.join();

        System.out.println("Bye!");
    }
}
