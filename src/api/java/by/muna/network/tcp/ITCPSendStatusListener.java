package by.muna.network.tcp;

public interface ITCPSendStatusListener {
    void onSent();
    void onCancelled();
    void onFail(int bytesSent);
}
