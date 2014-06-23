package by.muna.network.tcp;

public interface ITCPServer {
    void setListener(ITCPServerListener listener);

    int getPort();

    void stop();
}
