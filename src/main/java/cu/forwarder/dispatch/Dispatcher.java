package cu.forwarder.dispatch;

import cu.forwarder.config.AppConfig;
import cu.forwarder.server.ClientSession;
import cu.forwarder.tzsp.TZSPEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatcher:
 * - если приложение в исходящем режиме => отправляет все TZSP-пакеты на configured sendTo (UDP)
 * - если в входящем режиме => отправляет пакет всем зарегистрированным ClientSession (через их offerPacket)
 */
public class Dispatcher {
    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    private final AppConfig cfg;

    // outgoing socket (if needed)
    private DatagramSocket outgoingSocket;
    private InetSocketAddress outgoingTarget;

    // map of active clients (listener mode)
    private final Map<InetSocketAddress, ClientSession> clientMap = new ConcurrentHashMap<>();

    public Dispatcher(AppConfig cfg) throws SocketException {
        this.cfg = cfg;
        if (cfg.outgoingMode()) {
            String[] parts = cfg.sendTo.split(":");
            if (parts.length != 2) throw new IllegalArgumentException("sendTo must be IP:port");
            this.outgoingTarget = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
            this.outgoingSocket = new DatagramSocket();
            log.info("Outgoing configured to {}", outgoingTarget);
        }
    }

    public void registerClient(ClientSession client) {
        clientMap.put(client.getRemote(), client);
    }

    public void unregisterClient(InetSocketAddress remote) {
        clientMap.remove(remote);
    }

    /**
     * Метод, вызываемый из CaptureModule при получении сырого Ethernet-фрейма.
     */
    public void handleCapturedPacket(byte[] rawEthernetFrame) {
        if (rawEthernetFrame == null || rawEthernetFrame.length == 0) return;
        // кодируем TZSP
        byte[] tzsp = TZSPEncoder.encode(rawEthernetFrame);

        // outgoing mode
        if (cfg.outgoingMode()) {
            try {
                DatagramPacket dp = new DatagramPacket(tzsp, tzsp.length, outgoingTarget.getAddress(), outgoingTarget.getPort());
                outgoingSocket.send(dp);
            } catch (Exception e) {
                log.error("Failed to send outgoing TZSP packet: {}", e.getMessage());
            }
        }

        // incoming mode: отправляем всем зарегистрированным клиентам
        if (cfg.incomingMode()) {
            for (ClientSession cs : clientMap.values()) {
                boolean ok = cs.offerPacket(tzsp);
                if (!ok) {
                    log.debug("Packet dropped for client {}", cs.getRemote());
                }
            }
        }
    }
}
