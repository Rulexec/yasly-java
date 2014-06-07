package by.muna.network.tcp;

public interface ITCPServerListener {
    void onConnected(ITCPSocket socket);
    void onStop();
}
