package cu.forwarder.server;

import cu.forwarder.config.AppConfig;
import cu.forwarder.dispatch.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ListenerServer {

    private static final Logger log = LoggerFactory.getLogger(ListenerServer.class);

    private final AppConfig cfg;
    private final Dispatcher dispatcher;
    private final DatagramSocket listenSocket;
    private final Map<InetSocketAddress, ClientSession> clients = new ConcurrentHashMap<>();
    private final AtomicInteger clientCount = new AtomicInteger(0);

    public ListenerServer(AppConfig cfg, Dispatcher dispatcher) throws SocketException {
        this.cfg = cfg;
        this.dispatcher = dispatcher;
        this.listenSocket = new DatagramSocket(cfg.listenPort);
    }

    public void start() {
        Thread t = new Thread(this::runLoop, "listener-server");
        t.setDaemon(false);
        t.start();
        log.info("ListenerServer started on UDP port {}", cfg.listenPort);
    }

    private void runLoop() {
        byte[] buf = new byte[2048];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        while (true) {
            try {
                listenSocket.receive(pkt);
                InetSocketAddress remote = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                log.debug("Received trigger packet from {}", remote);
                // get or create client
                ClientSession cs = clients.get(remote);
                if (cs == null) {
                    if (clientCount.get() >= cfg.maxClients) {
                        log.warn("Max clients reached ({}). Ignoring new client {}", cfg.maxClients, remote);
                    } else {
                        try {
                            ClientSession newCs = new ClientSession(remote, cfg.timeoutSeconds, () -> removeClient(remote));
                            cs = clients.putIfAbsent(remote, newCs);
                            if (cs == null) {
                                cs = newCs;
                                clientCount.incrementAndGet();
                                // register with dispatcher so it will send packets to this client
                                dispatcher.registerClient(cs);
                                log.info("New client registered: {} (total {})", remote, clientCount.get());
                            } else {
                                // someone raced and inserted; close newly created
                                newCs.close();
                            }
                        } catch (Exception e) {
                            log.error("Failed to create ClientSession for {}: {}", remote, e.getMessage());
                        }
                    }
                }
                // touch if exists
                if (cs != null) {
                    cs.touch();
                }
            } catch (Exception e) {
                log.error("ListenerServer error: {}", e.getMessage());
            }
        }
    }

    private void removeClient(InetSocketAddress remote) {
        ClientSession removed = clients.remove(remote);
        if (removed != null) {
            clientCount.decrementAndGet();
            dispatcher.unregisterClient(remote);
            log.info("Client removed: {} (total {})", remote, clientCount.get());
        }
    }
}