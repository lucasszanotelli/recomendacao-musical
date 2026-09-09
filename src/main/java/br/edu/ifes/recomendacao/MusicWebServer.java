package br.edu.ifes.recomendacao;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Interface HTTP e servidor TCP compartilham a mesma instância sincronizada do serviço. */
public final class MusicWebServer implements AutoCloseable {
    private final HttpServer server;
    private final MusicService service;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public MusicWebServer(int port, MusicService service) throws IOException {
        this.service = service;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(workers);
        server.createContext("/", this::handle);
    }
    public int port() { return server.getAddress().getPort(); }
    public void start() { server.start(); }
    @Override public void close() { server.stop(0); workers.shutdownNow(); }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            try {
                if (exchange.getRequestMethod().equals("GET") && path.equals("/")) {
                    String name = params(exchange.getRequestURI().getRawQuery()).getOrDefault("usuario", "Ana");
                    send(exchange, 200, page(name));
                } else if (exchange.getRequestMethod().equals("POST") && (path.equals("/avaliar") || path.equals("/cadastrar"))) {
                    byte[] body = exchange.getRequestBody().readNBytes(4097);
                    if (body.length > 4096) { send(exchange, 413, "Pedido muito grande"); return; }
                    Map<String, String> form = params(new String(body, StandardCharsets.UTF_8));
                    String name = form.getOrDefault("usuario", "");
                    if (path.equals("/cadastrar")) service.register(name);
                    else service.rate(name, Integer.parseInt(form.getOrDefault("artista", "")), Integer.parseInt(form.getOrDefault("nota", "")));
                    exchange.getResponseHeaders().set("Location", "/?usuario=" + URLEncoder.encode(name, StandardCharsets.UTF_8));
                    exchange.sendResponseHeaders(303, -1);
                } else {
                    send(exchange, 404, "<h1>Página não encontrada</h1><a href='/'>Voltar</a>");
                }
            } catch (IllegalArgumentException e) {
                send(exchange, 400, "<!doctype html><html lang='pt-BR'><meta charset='utf-8'><h1>Não foi possível concluir</h1><p>"
                        + escape(e.getMessage()) + "</p><a href='/'>Voltar ao início</a></html>");
            }
        }
    }
    private static Map<String, String> params(String encoded) {
        Map<String, String> result = new HashMap<>();
        if (encoded != null && !encoded.isEmpty()) for (String pair : encoded.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static void send(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    private String page(String name) {
        synchronized (service) {
            int[] ratings = service.profile(name);
            StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Recomendação musical</title><style>
                *{box-sizing:border-box}body{margin:0;background:#f1f5f4;color:#162f2c;font:16px system-ui,sans-serif}
                main{max-width:1060px;margin:auto;padding:36px 20px}h1{font-size:clamp(28px,5vw,44px);margin:10px 0}
                h2{font-size:22px}p{line-height:1.6}header{margin-bottom:28px}.tag{color:#286c5e;font-weight:700;letter-spacing:2px;font-size:12px}
                section{background:white;border:1px solid #dce6e2;border-radius:16px;padding:24px;margin-bottom:20px}
                .grid{display:grid;grid-template-columns:1.3fr 1fr;gap:20px}.toolbar{display:flex;flex-wrap:wrap;gap:20px;align-items:end}
                form{display:flex;gap:8px;align-items:center;flex-wrap:wrap}input,select,button{font:inherit;padding:10px;border-radius:8px;border:1px solid #b6cbc2;max-width:100%}
                button{background:#14634f;color:white;border:0;cursor:pointer}button:hover{background:#0c493a}label{font-weight:600}
                table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:12px 4px;border-bottom:1px solid #e7efeb}th{font-size:13px;color:#536b62}
                .card{border-left:4px solid #25856c;padding:8px 16px;background:#f1f8f5;margin:12px 0}.muted{color:#526b62;font-size:14px}
                @media(max-width:760px){.grid{grid-template-columns:1fr}section{padding:16px}td form{gap:4px}}
                </style></head><body><main><header><span class="tag">DESCUBRA SEU PRÓXIMO SOM</span>
                <h1>Recomendação musical</h1><p>Avalie seus artistas e descubra sugestões a partir de quem tem gostos parecidos com os seus.</p></header>
                <section class="toolbar"><form method="get" action="/"><label for="perfil">Perfil</label><select id="perfil" name="usuario">
                """);
            for (String user : service.users()) html.append("<option value=\"").append(escape(user)).append("\"")
                    .append(user.equals(name) ? " selected" : "").append(">").append(escape(user)).append("</option>");
            html.append("""
                </select><button>Abrir perfil</button></form>
                <form method="post" action="/cadastrar"><label for="novo">Novo usuário</label>
                <input id="novo" name="usuario" maxlength="30" placeholder="Seu nome, sem espaços" required><button>Cadastrar</button></form></section>
                <div class="grid"><section><h2>Avaliações de
                """).append(escape(name)).append("</h2><p class='muted'>0 = não conheço · 1 = não gosto · 2 = gosto muito pouco · 3 = gosto · 4 = gosto muito</p><table><thead><tr><th>Artista / banda</th><th>Sua nota</th></tr></thead><tbody>");
            for (int i = 0; i < 15; i++) {
                html.append("<tr><td>").append(escape(MusicService.ARTISTS.get(i))).append("</td><td><form method='post' action='/avaliar'>")
                    .append("<input type='hidden' name='usuario' value=\"").append(escape(name)).append("\"><input type='hidden' name='artista' value='").append(i + 1)
                    .append("'><select name='nota' aria-label='Nota para ").append(escape(MusicService.ARTISTS.get(i))).append("'>");
                for (int n = 0; n <= 4; n++) html.append("<option").append(ratings[i] == n ? " selected" : "").append(">").append(n).append("</option>");
                html.append("</select><button>Salvar</button></form></td></tr>");
            }
            html.append("</tbody></table></section><div><section><h2>Para você ouvir</h2><p class='muted'>Sugestões para artistas que você ainda não avaliou.</p>");
            var recommendations = service.recommend(name);
            if (recommendations.isEmpty()) html.append("<p>Ainda não há sugestões. Avalie mais artistas; se você já avaliou todos, não há novos candidatos.</p>");
            for (var r : recommendations) html.append("<div class='card'><strong>").append(escape(r.name())).append("</strong><p>Nota estimada: ")
                    .append(String.format(Locale.forLanguageTag("pt-BR"), "%.2f", r.score())).append(" / 4</p></div>");
            html.append("</section><section><h2>Gostos mais próximos</h2><p class='muted'>Quanto menor a distância, mais parecidas são as avaliações em comum.</p>");
            var neighbors = service.neighbors(name);
            if (neighbors.isEmpty()) html.append("<p>Avalie um artista para começar a comparar seu perfil.</p>");
            for (var n : neighbors) html.append("<p><strong>").append(escape(n.name())).append("</strong><br><span class='muted'>Distância: ")
                    .append(String.format(Locale.forLanguageTag("pt-BR"), "%.3f", n.distance())).append(" · ").append(n.common()).append(" artistas em comum</span></p>");
            return html.append("</section></div></div><p class='muted'>As alterações são compartilhadas com os outros clientes. Os dados são reiniciados quando o servidor é encerrado.</p></main></body></html>").toString();
        }
    }
}
