package by.muna.network.tcp;

public interface ITCPSendStatusListener {
    void onSent();
    void onFail();
}
