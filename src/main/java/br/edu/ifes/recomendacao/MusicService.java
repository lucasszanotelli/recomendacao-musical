package br.edu.ifes.recomendacao;

import java.util.*;

/** Monitor protege os dados centralizados em memória durante consultas e alterações. */
public final class MusicService {
    public static final List<String> ARTISTS = List.of("The Beatles", "Queen", "Pink Floyd", "Led Zeppelin", "Nirvana", "Metallica", "Iron Maiden", "U2", "Coldplay", "Imagine Dragons", "Legião Urbana", "Titãs", "Charlie Brown Jr.", "Skank", "Capital Inicial");
    private final Map<String, int[]> users = new TreeMap<>();
    public MusicService() {
        String[] names = {"Ana", "Joao", "Bruno", "Carla", "Diego", "Elisa", "Fabio", "Gabriela", "Helena", "Igor"};
        int[][] ratings = {
            {4,3,4,3,0,1,0,3,4,4,2,0,3,2,3},
            {4,3,4,3,4,1,2,3,4,4,2,4,3,2,3},
            {4,4,4,4,3,2,3,3,2,1,3,3,2,2,3},
            {2,3,1,1,2,1,1,4,4,4,3,2,3,4,2},
            {1,3,3,4,4,4,4,2,1,2,1,2,4,1,2},
            {3,3,2,2,3,1,1,3,4,3,4,4,4,4,4},
            {4,4,4,4,4,3,3,4,2,2,2,3,3,2,2},
            {2,2,1,1,3,1,0,3,4,4,4,3,4,4,3},
            {4,3,4,3,3,1,4,3,4,4,2,3,3,2,3},
            {1,2,3,4,4,4,4,1,1,1,2,3,4,2,3}};
        for (int i = 0; i < names.length; i++) users.put(names[i], ratings[i]);
    }
    public static double distance(int[] a, int[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("Tamanhos diferentes");
        int sum = 0, common = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != 0 && b[i] != 0) {
            sum += (a[i] - b[i]) * (a[i] - b[i]); common++;
        }
        return common == 0 ? Double.POSITIVE_INFINITY : Math.sqrt(sum);
    }
    public synchronized List<String> users() { return List.copyOf(users.keySet()); }
    public synchronized int[] profile(String name) {
        if (!users.containsKey(name)) throw new IllegalArgumentException("Usuário não encontrado");
        return users.get(name).clone();
    }
    public synchronized void register(String name) {
        if (!name.matches("[\\p{L}\\p{N}_-]{1,30}")) throw new IllegalArgumentException("Nome: 1 a 30 letras, números, _ ou -, sem espaços");
        if (users.containsKey(name)) throw new IllegalArgumentException("Usuário já existe");
        users.put(name, new int[15]);
    }
    public synchronized void rate(String name, int artist, int rating) {
        if (artist < 1 || artist > 15 || rating < 0 || rating > 4) throw new IllegalArgumentException("Artista: 1 a 15; nota: 0 a 4");
        profile(name);
        users.get(name)[artist - 1] = rating;
    }
    public record Neighbor(String name, double distance, int common) { }
    public record Recommendation(int artist, String name, double score) { }
    public synchronized List<Neighbor> neighbors(String name) {
        int[] target = profile(name);
        List<Neighbor> result = new ArrayList<>();
        users.forEach((other, ratings) -> {
            if (other.equals(name)) return;
            double distance = distance(target, ratings);
            int common = 0;
            for (int i = 0; i < 15; i++) if (target[i] != 0 && ratings[i] != 0) common++;
            if (Double.isFinite(distance)) result.add(new Neighbor(other, distance, common));
        });
        result.sort(Comparator.comparingDouble(Neighbor::distance).thenComparing(Comparator.comparingInt(Neighbor::common).reversed()).thenComparing(Neighbor::name));
        return List.copyOf(result.subList(0, Math.min(3, result.size())));
    }
    public synchronized List<Recommendation> recommend(String name) {
        int[] target = profile(name);
        List<Neighbor> neighbors = neighbors(name);
        List<Recommendation> result = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            if (target[i] != 0) continue;
            double sum = 0, weights = 0;
            for (Neighbor neighbor : neighbors) {
                int rating = users.get(neighbor.name())[i];
                if (rating == 0) continue;
                double weight = 1 / (1 + neighbor.distance());
                sum += weight * rating; weights += weight;
            }
            if (weights > 0 && sum / weights >= 3) result.add(new Recommendation(i + 1, ARTISTS.get(i), sum / weights));
        }
        result.sort(Comparator.comparingDouble(Recommendation::score).reversed().thenComparingInt(Recommendation::artist));
        return List.copyOf(result);
    }
}
