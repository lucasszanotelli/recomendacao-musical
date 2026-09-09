package br.edu.ifes.recomendacao;

public final class App {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Uso: java -jar target/recomendacao-musical-1.0-SNAPSHOT.jar servidor [portaTCP] [portaWeb] | cliente [host] [porta]");
            return;
        }
        switch (args[0]) {
            case "servidor" -> {
                MusicService service = new MusicService();
                try (MusicServer server = new MusicServer(args.length > 1 ? Integer.parseInt(args[1]) : 5000, service);
                     MusicWebServer web = new MusicWebServer(args.length > 2 ? Integer.parseInt(args[2]) : 8080, service)) {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> { web.close(); server.close(); }));
                    web.start();
                    System.out.println("Servidor TCP na porta " + server.port());
                    System.out.println("Abra no navegador: http://localhost:" + web.port());
                    server.serve();
                }
            }
            case "cliente" -> MusicClient.run(args.length > 1 ? args[1] : "localhost", args.length > 2 ? Integer.parseInt(args[2]) : 5000);
            default -> throw new IllegalArgumentException("Modo esperado: servidor ou cliente");
        }
    }
}
