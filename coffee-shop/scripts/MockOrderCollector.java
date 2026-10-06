import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MockOrderCollector {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9090;
        int responseStatus = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        Set<String> processedEvents = ConcurrentHashMap.newKeySet();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/orders", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("POST")) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String eventId = exchange.getRequestHeaders().getFirst("Idempotency-Key");
                if (eventId == null || eventId.isBlank()) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (responseStatus >= 200 && responseStatus < 300) {
                    if (processedEvents.add(eventId)) {
                        System.out.println("수신: " + body);
                    } else {
                        System.out.println("이미 처리한 이벤트: " + eventId);
                    }
                } else {
                    System.out.println("실패 응답: " + responseStatus + ", 이벤트: " + eventId);
                }
                exchange.sendResponseHeaders(responseStatus, -1);
            } finally {
                exchange.close();
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println("Mock 수집 서버: http://127.0.0.1:" + port + "/orders, 응답: " + responseStatus);
    }
}
