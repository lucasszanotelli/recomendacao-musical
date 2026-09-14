package br.edu.ifes.recomendacao;

import junit.framework.TestCase;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class AppTest extends TestCase {
    public void testWebAndTcpShareRatings() throws Exception {
        MusicService service = new MusicService();
        try (MusicWebServer web = new MusicWebServer(0, service);
             MusicServer tcp = new MusicServer(0, service);
             ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
             java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient()) {
            web.start();
            Future<?> serving = pool.submit(() -> { tcp.serve(); return null; });
            String base = "http://localhost:" + web.port();
            var page = client.send(java.net.http.HttpRequest.newBuilder(URI.create(base + "/")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("Nirvana"));
            assertTrue(page.body().contains("Recomendação musical"));
            assertTrue(page.body().contains("type='radio'"));
            assertTrue(page.body().contains("Salvar todas as avaliações"));
            var batchUpdate = client.send(java.net.http.HttpRequest.newBuilder(URI.create(base + "/avaliar"))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("usuario=Ana&nota_1=2&nota_2=1&nota_3=0&nota_4=4&nota_5=3&nota_6=0&nota_7=0&nota_8=0&nota_9=0&nota_10=0&nota_11=0&nota_12=0&nota_13=0&nota_14=0&nota_15=0")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(303, batchUpdate.statusCode());
            assertEquals(2, service.profile("Ana")[0]);
            assertEquals(3, service.profile("Ana")[4]);
            var update = client.send(java.net.http.HttpRequest.newBuilder(URI.create(base + "/avaliar"))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("usuario=Ana&artista=5&nota=4")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(303, update.statusCode());
            try (Socket socket = new Socket("localhost", tcp.port())) {
                socket.setSoTimeout(5000);
                assertTrue(request(socket, "PERFIL Ana").getFirst().startsWith("[2, 1, 0, 4, 4"));
            }
            var invalid = client.send(java.net.http.HttpRequest.newBuilder(URI.create(base + "/avaliar"))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("usuario=Ana&artista=5&nota=9")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalid.statusCode());
            assertEquals(4, service.profile("Ana")[4]);
            var register = client.send(java.net.http.HttpRequest.newBuilder(URI.create(base + "/cadastrar"))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("usuario=Novo")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(303, register.statusCode());
            assertEquals(15, service.profile("Novo").length);
            tcp.close();
            serving.get(5, TimeUnit.SECONDS);
        }
    }

    public void testDistanceIgnoresUnknownRatings() {
        assertEquals(Math.sqrt(2), MusicService.distance(new int[]{4,3,0,4,2}, new int[]{3,2,4,4,0}), 1e-10);
        assertEquals(Math.sqrt(3), MusicService.distance(new int[]{4,3,4,3,0}, new int[]{4,2,3,4,1}), 1e-10);
        assertTrue(Double.isInfinite(MusicService.distance(new int[]{0,4}, new int[]{3,0})));
        assertEquals(0.0, MusicService.distance(new int[]{4,3}, new int[]{4,3}), 0.0);
    }
    public void testInitialDataAndRecommendations() {
        MusicService service = new MusicService();
        assertEquals(15, MusicService.ARTISTS.size());
        assertEquals(10, service.users().size());
        for (String name : service.users()) assertEquals(15, service.profile(name).length);
        assertEquals("Helena", service.neighbors("Ana").getFirst().name());
        assertFalse(service.recommend("Ana").isEmpty());
        for (var r : service.recommend("Ana")) {
            assertEquals(0, service.profile("Ana")[r.artist() - 1]);
            assertTrue(r.score() >= 3 && r.score() <= 4);
        }
        service.rate("Ana", 5, 4);
        assertTrue(service.recommend("Ana").stream().noneMatch(r -> r.artist() == 5));
        int[] copy = service.profile("Ana"); copy[0] = 0;
        assertEquals(4, service.profile("Ana")[0]);
    }
    public void testNewUserAndValidation() {
        MusicService service = new MusicService();
        service.register("Lucas");
        assertTrue(service.neighbors("Lucas").isEmpty());
        assertTrue(service.recommend("Lucas").isEmpty());
        for (int[] input : new int[][]{{0,3},{16,3},{1,-1},{1,5}}) {
            try { service.rate("Lucas", input[0], input[1]); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        try { service.register("Lucas"); fail(); } catch (IllegalArgumentException expected) { }
        try { service.profile("Inexistente"); fail(); } catch (IllegalArgumentException expected) { }
        for (int i = 1; i <= 15; i++) service.rate("Lucas", i, 3);
        assertTrue(service.recommend("Lucas").isEmpty());
    }
    private static List<String> request(Socket socket, String command) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out.println(command);
        List<String> result = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null && !line.equals("FIM")) result.add(line);
        if (line == null) throw new EOFException("Resposta incompleta");
        return result;
    }
    public void testConcurrentTcpClientsAndSharedState() throws Exception {
        try (MusicServer server = new MusicServer(0); ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> serving = pool.submit(() -> { server.serve(); return null; });
            try (Socket idle = new Socket("localhost", server.port())) {
                List<Future<?>> futures = new ArrayList<>();
                CountDownLatch ready = new CountDownLatch(12);
                CountDownLatch start = new CountDownLatch(1);
                for (int i = 0; i < 12; i++) {
                    final int id = i;
                    futures.add(pool.submit(() -> {
                        try (Socket socket = new Socket("localhost", server.port())) {
                            socket.setSoTimeout(5000);
                            ready.countDown();
                            assertTrue(start.await(5, TimeUnit.SECONDS));
                            assertTrue(request(socket, "CADASTRAR Teste" + id).getFirst().startsWith("OK"));
                            assertTrue(request(socket, "AVALIAR Teste" + id + " 1 4").getFirst().startsWith("OK"));
                            assertTrue(request(socket, "AVALIAR Teste" + id + " 1 9").getFirst().startsWith("ERRO"));
                            assertTrue(request(socket, "INVALIDO").getFirst().startsWith("ERRO"));
                            assertEquals(15, request(socket, "ARTISTAS").size());
                            assertFalse(request(socket, "RECOMENDAR Ana").isEmpty());
                            assertTrue(request(socket, "SAIR").getFirst().startsWith("OK"));
                        }
                        return null;
                    }));
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
                for (Future<?> future : futures) future.get(15, TimeUnit.SECONDS);
                idle.setSoTimeout(5000);
                assertEquals(22, request(idle, "USUARIOS").size());
                assertTrue(request(idle, "PERFIL Teste0").getFirst().startsWith("[4, 0"));
            } finally { server.close(); }
            serving.get(5, TimeUnit.SECONDS);
        }
    }
}
