package br.edu.ifes.recomendacao;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class MusicClient {
    public static void run(String host, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(15000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.println("Conectado a " + host + ":" + port);
            System.out.println("Comandos: ARTISTAS | USUARIOS | CADASTRAR nome | PERFIL nome | AVALIAR nome artista nota | RECOMENDAR nome | SAIR");
            while (true) {
                System.out.print("> ");
                String command = console.readLine();
                if (command == null) break;
                out.println(command);
                String response;
                while ((response = in.readLine()) != null && !response.equals("FIM")) System.out.println(response);
                if (response == null) { System.out.println("Servidor desconectado."); break; }
                if (command.trim().equalsIgnoreCase("SAIR")) break;
            }
        }
    }
}
