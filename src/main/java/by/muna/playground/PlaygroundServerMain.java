package by.muna.playground;

import by.muna.io.AsyncStreamUtil;
import by.muna.io.IByteReader;
import by.muna.io.IByteWriter;
import by.muna.network.tcp.ITCPServer;
import by.muna.network.tcp.ITCPSocket;
import by.muna.network.tcp.TCPServer;
import by.muna.network.tcp.TCPSocket;
import by.muna.network.tcp.TCPSocketsThread;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Function;

public class PlaygroundServerMain {
    private static Timer timer = new Timer();

    public static void main(String[] args) throws Exception {
        TCPSocketsThread sockets = new TCPSocketsThread();

        ITCPServer server = new TCPServer(new InetSocketAddress(9999));

        server.setConsumer(socket -> {
            System.out.println("Connected! Remote address: " + socket.getRemoteAddress());
            AsyncStreamUtil.pipe(socket.getInputStream(), socket.getOutputStream());

            socket.onShutdown().run(x -> {
                System.out.println("Disconnected " + socket.getRemoteAddress() + " by " + x);
                sockets.stop();
            });
        });

        sockets.register(server).run(x -> System.out.println("registration: " + x));
        //sockets.join();

        ITCPSocket socket = new TCPSocket(new InetSocketAddress("localhost", 9999));

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
                    //System.out.println("C> Readed: " + readed);
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
            private byte[] toWrite = "Hi!".getBytes();
            private int offset = 0;

            @Override
            public Boolean apply(IByteWriter writer) {
                this.offset += writer.write(this.toWrite, this.offset);

                boolean done = this.offset == this.toWrite.length;

                if (done) {
                    //socket.getOutputStream().end();
                    PlaygroundServerMain.timer.schedule(new TimerTask() {
                        @Override public void run() {
                            socket.getOutputStream().end();
                            PlaygroundServerMain.timer.cancel();
                        }
                    }, 100);
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
        });

        sockets.register(socket).run(x -> System.out.println("Socket registration: " + x));

        sockets.join();

        System.out.println("Bye!");
    }
}
