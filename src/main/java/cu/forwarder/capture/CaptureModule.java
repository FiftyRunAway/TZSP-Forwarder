package cu.forwarder.capture;

import cu.forwarder.config.AppConfig;
import cu.forwarder.dispatch.Dispatcher;
import org.pcap4j.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * CaptureModule для pcap4j.
 * Работает в следующем режиме:
 * - если cfg.listInterfaces == true -> печатает список интерфейсов и выходит
 * - иначе открывает указанный интерфейс и запускает loop, передавая сырой ethernet-кадр в dispatcher
 */
public class CaptureModule {

    private static final Logger log = LoggerFactory.getLogger(CaptureModule.class);

    private final AppConfig cfg;
    private final Dispatcher dispatcher;
    private PcapHandle handle;

    public CaptureModule(AppConfig cfg, Dispatcher dispatcher) {
        this.cfg = cfg;
        this.dispatcher = dispatcher;
    }

    public void listInterfacesAndExit() throws PcapNativeException {
        List<PcapNetworkInterface> all = Pcaps.findAllDevs();
        if (all == null || all.isEmpty()) {
            System.out.println("No interfaces found.");
            return;
        }
        System.out.println("Available interfaces:");
        for (PcapNetworkInterface nif : all) {
            String desc = nif.getDescription() == null ? "" : nif.getDescription();
            System.out.printf("%s - %s (%s)%n", nif.getName(), nif.getName(), desc);
        }
    }

    public void startCapture() throws Exception {
        if (cfg.iface == null || cfg.iface.isEmpty()) {
            throw new IllegalArgumentException("Interface name is required (use -i --iface).");
        }
        PcapNetworkInterface nif = Pcaps.getDevByName(cfg.iface);
        if (nif == null) {
            throw new IllegalArgumentException("Interface not found: " + cfg.iface);
        }

        int snapLen = 65536;
        int timeoutMillis = 10;
        handle= new PcapHandle.Builder(nif.getName())
                .snaplen(snapLen)
                .promiscuousMode(PcapNetworkInterface.PromiscuousMode.PROMISCUOUS)
                .timeoutMillis(timeoutMillis)
                .rfmon(true)
                .build();

        if (cfg.filter != null && !cfg.filter.isBlank()) {
            handle.setFilter(cfg.filter, BpfProgram.BpfCompileMode.OPTIMIZE);
            log.info("Applied filter: {}", cfg.filter);
        }

        log.info("Started capture on interface {}", cfg.iface);

        PacketListener listener = packet -> {
            try {
                byte[] raw = packet.getRawData();
                // Проверим тип link layer: pcap4j в Windows обычно поставляет ethernet frames.
                // Если длина слишком мала — игнорируем.
                if (raw.length < 14) {
                    log.debug("Ignoring too small packet (len={})", raw.length);
                    return;
                }
                dispatcher.handleCapturedPacket(raw);
            } catch (Exception e) {
                log.error("Error handling packet: {}", e.getMessage());
            }
        };

        Thread t = new Thread(() -> {
            try {
                handle.loop(-1, listener);
            } catch (PcapNativeException | NotOpenException | InterruptedException e) {
                log.error("pcap loop terminated: {}", e.getMessage());
            }
        }, "pcap-loop");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        if (handle != null && handle.isOpen()) {
            handle.close();
        }
    }
}