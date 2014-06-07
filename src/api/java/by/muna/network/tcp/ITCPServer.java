package by.muna.network.tcp;

public interface ITCPServer {
    void setListener(ITCPServerListener listener);
    void stop();
}
