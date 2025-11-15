package cu.forwarder;

import cu.forwarder.capture.CaptureModule;
import cu.forwarder.config.AppConfig;
import cu.forwarder.dispatch.Dispatcher;
import cu.forwarder.server.ListenerServer;
import org.pcap4j.core.PcapNativeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        AppConfig cfg = new AppConfig();
        CommandLine cmd = new CommandLine(cfg);
        CommandLine.ParseResult pr = cmd.parseArgs(args);

        if (pr.isUsageHelpRequested()) {
            cmd.usage(System.out);
            return;
        }
        if (cfg.listInterfaces) {
            // вывести список интерфейсов и выйти
            try {
                CaptureModule cm = new CaptureModule(cfg, null);
                cm.listInterfacesAndExit();
            } catch (PcapNativeException e) {
                log.error("Failed to list interfaces: {}", e.getMessage());
            }
            return;
        }

        // Валидация режимов
        if (!cfg.incomingMode() && !cfg.outgoingMode()) {
            log.error("Не указан режим: ни --send-to (outgoing), ни --listen-port (incoming).");
            cmd.usage(System.out);
            return;
        }
        if (cfg.incomingMode() && cfg.outgoingMode()) {
            log.warn("Оба режима указаны; приложение будет работать и в outgoing, и в incoming режиме одновременно.");
        }

        Dispatcher dispatcher = new Dispatcher(cfg);

        // если входящий режим — запустить ListenerServer
        if (cfg.incomingMode()) {
            ListenerServer ls = new ListenerServer(cfg, dispatcher);
            ls.start();
        }

        // Запустить capture
        CaptureModule capture = new CaptureModule(cfg, dispatcher);
        capture.startCapture();

        // don't exit
        Thread.currentThread().join();
    }
}