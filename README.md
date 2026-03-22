# TZSP Forwarder для MacOS
## Собрать проект (+ нужен установленный Npcap драйвер):

``` bash
./gradlew clean build
```

### Команда:

``` bash
java -jar build/libs/tzsp-forwarder-1.0.jar forwarder --help
```

### Вывод:

```
Usage: forwarder [-hlV] [-f=<filter>] -i=<iface> [-m=<maxClients>]
                 [-p=<listenPort>] [-s=<sendTo>] [-t=<timeoutSeconds>]
Forwarder — перехват трафика и пересылка в TZSP по UDP.
  -f, --filter=<filter>     BPF фильтр для pcap (например: "tcp and port 80").
  -h, --help                Show this help message and exit.
  -i, --interface=<iface>   Имя интерфейса для захвата (например: Ethernet0).
  -l, --list                Вывести список доступных интерфейсов и выйти.
  -m, --max-clients=<maxClients>
                            Максимум одновременных клиентов в входящем режиме
                              (по умолчанию: 50).
  -p, --listen-port=<listenPort>
                            Входящий режим: UDP порт для прослушивания
                              (например: 9999).
  -s, --send-to=<sendTo>    Исходящий режим: адрес и порт для отправки TZSP
                              (формат IP:port).
  -t, --timeout=<timeoutSeconds>
                            Таймаут стриминга для клиента в секундах (по
                              умолчанию: 60).
  -V, --version             Print version information and exit.
```

- [BPF filters](https://www.tcpdump.org/manpages/pcap-filter.7.html)

## Показать список интерфейсов:
``` bash
java -jar build/libs/tzsp-forwarder-1.0.jar --list
```

## Исходящий режим:
``` bash
java -jar build/libs/tzsp-forwarder-1.0.jar --interface "en0" --send-to 127.0.0.1:37008
```

## Входящий режим:
``` bash
java -jar build/libs/tzsp-forwarder-1.0.jar --interface="en0" --listen-port=37008 --timeout=60 --max-clients=45
```

