package cu.forwarder.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Клиентская сессия:
 * - собственная очередь TZSP-пакетов (байт[])
 * - отдельный поток отправки (один поток на клиента)
 * - таймер "inactivity", который закрывает сессию после timeoutSeconds
 */
public class ClientSession {

    private static final Logger log = LoggerFactory.getLogger(ClientSession.class);

    private final InetSocketAddress remote;
    private final DatagramSocket socket;
    private final BlockingQueue<byte[]> queue;
    private final Thread senderThread;
    private final ScheduledExecutorService scheduler;
    private final int timeoutSeconds;
    private final Runnable onClose;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile long lastSeenEpochSec;

    public ClientSession(InetSocketAddress remote,
                         int timeoutSeconds,
                         Runnable onClose) throws SocketException {
        this.remote = remote;
        this.socket = new DatagramSocket(); // ephemeral local port for sending
        this.queue = new LinkedBlockingQueue<>(1024); // bounded queue
        this.timeoutSeconds = timeoutSeconds;
        this.onClose = onClose;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "client-timeouter-" + remote);
            t.setDaemon(true);
            return t;
        });
        this.lastSeenEpochSec = System.currentTimeMillis() / 1000L;

        this.senderThread = new Thread(this::runSender, "client-sender-" + remote);
        this.senderThread.setDaemon(true);
        this.senderThread.start();

        // Запускаем периодический таск проверки inactivity
        scheduler.scheduleAtFixedRate(this::checkTimeout, timeoutSeconds, timeoutSeconds, TimeUnit.SECONDS);
    }

    public void touch() {
        this.lastSeenEpochSec = System.currentTimeMillis() / 1000L;
    }

    public boolean offerPacket(byte[] tzspPacket) {
        if (!running.get()) return false;
        boolean ok = queue.offer(tzspPacket);
        if (!ok) {
            // если очередь полна, попробуем удалить старые и вставить
            queue.poll();
            ok = queue.offer(tzspPacket);
            if (!ok) {
                log.warn("Dropping packet for {} - queue full", remote);
            }
        }
        return ok;
    }

    private void runSender() {
        while (running.get()) {
            try {
                byte[] pkt = queue.poll(1, TimeUnit.SECONDS);
                if (pkt == null) continue;
                DatagramPacket dp = new DatagramPacket(pkt, pkt.length, remote.getAddress(), remote.getPort());
                socket.send(dp);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error sending to {}: {}", remote, e.getMessage());
            }
        }
    }

    private void checkTimeout() {
        long now = System.currentTimeMillis() / 1000L;
        if (now - lastSeenEpochSec >= timeoutSeconds) {
            log.info("Client {} timed out ({}s) - closing", remote, timeoutSeconds);
            close();
        }
    }

    public void close() {
        if (!running.compareAndSet(true, false)) return;
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {}
        try {
            socket.close();
        } catch (Exception ignored) {}
        try {
            senderThread.interrupt();
        } catch (Exception ignored) {}

        try {
            onClose.run();
        } catch (Exception ignored) {}
    }

    public InetSocketAddress getRemote() {
        return remote;
    }
}
