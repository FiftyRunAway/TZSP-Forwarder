package cu.forwarder.config;

import picocli.CommandLine;

@CommandLine.Command(name = "forwarder", mixinStandardHelpOptions = true,
        description = "Forwarder — перехват трафика и пересылка в TZSP по UDP.")
public class AppConfig implements Runnable {

    @CommandLine.Option(names = {"-l", "--list"}, description = "Вывести список доступных интерфейсов и выйти.", defaultValue = "false")
    public boolean listInterfaces;

    @CommandLine.Option(names = {"-i", "--interface"}, description = "Имя интерфейса для захвата (например: Ethernet0).", required = false)
    public String iface;

    @CommandLine.Option(names = {"-f", "--filter"}, description = "BPF фильтр для pcap (например: \"tcp and port 80\").", defaultValue = "")
    public String filter;

    @CommandLine.Option(names = {"-s", "--send-to"}, description = "Исходящий режим: адрес и порт для отправки TZSP (формат IP:port).", required = false)
    public String sendTo;

    @CommandLine.Option(names = {"-p", "--listen-port"}, description = "Входящий режим: UDP порт для прослушивания (например: 9999).", required = false)
    public Integer listenPort;

    @CommandLine.Option(names = {"-t", "--timeout"}, description = "Таймаут стриминга для клиента в секундах (по умолчанию: ${DEFAULT-VALUE}).", defaultValue = "60")
    public int timeoutSeconds;

    @CommandLine.Option(names = {"-m", "--max-clients"}, description = "Максимум одновременных клиентов в входящем режиме (по умолчанию: ${DEFAULT-VALUE}).", defaultValue = "50")
    public int maxClients;

    public boolean incomingMode() {
        return listenPort != null;
    }

    public boolean outgoingMode() {
        return sendTo != null && !sendTo.isEmpty();
    }

    @Override
    public void run() { }

    public static AppConfig parse(String[] args) {
        AppConfig cfg = new AppConfig();
        CommandLine cmd = new CommandLine(cfg);
        cmd.parseArgs(args);
        if (cmd.isUsageHelpRequested()) {
            cmd.usage(System.out);
            System.exit(0);
        }
        return cfg;
    }
}
