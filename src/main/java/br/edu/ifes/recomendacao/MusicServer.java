package br.edu.ifes.recomendacao;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MusicServer implements AutoCloseable {
    private final ServerSocket listener;
    private final MusicService service;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    public MusicServer(int port) throws IOException { this(port, new MusicService()); }
    public MusicServer(int port, MusicService service) throws IOException {
        this.service = service;
        listener = new ServerSocket(port);
    }
    public int port() { return listener.getLocalPort(); }
    public void serve() throws IOException {
        while (!listener.isClosed()) {
            try {
                Socket socket = listener.accept();
                clients.add(socket);
                workers.submit(() -> handle(socket));
            } catch (SocketException e) { if (!listener.isClosed()) throw e; }
        }
    }
    private void handle(Socket socket) {
        System.out.println("Cliente conectado: " + socket.getRemoteSocketAddress());
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                try { for (String response : execute(line)) out.println(response); }
                catch (IllegalArgumentException e) { out.println("ERRO " + e.getMessage()); }
                out.println("FIM");
                if (line.trim().equalsIgnoreCase("SAIR")) break;
            }
        } catch (IOException e) {
            if (!listener.isClosed()) System.err.println("Conexão encerrada: " + e.getMessage());
        } finally { clients.remove(socket); }
    }
    private List<String> execute(String line) {
        String[] p = line.trim().split("\\s+");
        String command = p[0].toUpperCase(Locale.ROOT);
        int expected = switch (command) {
            case "ARTISTAS", "USUARIOS", "SAIR" -> 1;
            case "CADASTRAR", "PERFIL", "RECOMENDAR" -> 2;
            case "AVALIAR" -> 4;
            default -> throw new IllegalArgumentException("Comando desconhecido");
        };
        if (p.length != expected) throw new IllegalArgumentException("Quantidade de argumentos inválida");
        List<String> result = new ArrayList<>();
        switch (command) {
            case "ARTISTAS" -> { for (int i = 0; i < 15; i++) result.add((i + 1) + " " + MusicService.ARTISTS.get(i)); }
            case "USUARIOS" -> result.addAll(service.users());
            case "CADASTRAR" -> { service.register(p[1]); result.add("OK Usuário cadastrado"); }
            case "PERFIL" -> result.add(Arrays.toString(service.profile(p[1])));
            case "AVALIAR" -> { service.rate(p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3])); result.add("OK Avaliação registrada"); }
            case "RECOMENDAR" -> {
                // Vizinhos e recomendações são calculados sobre a mesma versão dos dados.
                synchronized (service) {
                    for (var n : service.neighbors(p[1])) result.add(String.format(Locale.ROOT, "VIZINHO %s distancia=%.3f artistas_em_comum=%d", n.name(), n.distance(), n.common()));
                    for (var r : service.recommend(p[1])) result.add(String.format(Locale.ROOT, "RECOMENDACAO %d %s nota_estimada=%.3f", r.artist(), r.name(), r.score()));
                    if (result.stream().noneMatch(s -> s.startsWith("RECOMENDACAO"))) result.add("Sem recomendações: avalie mais artistas ou não há candidatos com nota estimada >= 3.");
                }
            }
            case "SAIR" -> result.add("OK Até logo");
            default -> throw new IllegalStateException(command);
        }
        return result;
    }
    @Override public void close() {
        try { listener.close(); } catch (IOException ignored) { }
        for (Socket client : clients) try { client.close(); } catch (IOException ignored) { }
        workers.shutdownNow();
    }
}
