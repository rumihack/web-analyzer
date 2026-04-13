package com.rumihack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class App {

    private static final String URL   = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String YTDLP = "/usr/local/bin/yt-dlp";
    private static final String DENO  = "/home/angel/.deno/bin/deno";

    public static void main(String[] args) {

        System.out.println("===========================================");
        System.out.println(" Web Media Analyzer - rumihack");
        System.out.println(" URL: " + URL);
        System.out.println("===========================================\n");

        if (!ytDlpDisponible()) {
            System.out.println("ERROR: yt-dlp no encontrado.");
            return;
        }

        System.out.println("Analizando, por favor espera...\n");

        String json = obtenerJson();
        if (json == null) {
            System.out.println("No se pudo obtener informacion del video.");
            return;
        }

        parsearYMostrar(json);
    }

    private static String obtenerJson() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                YTDLP,
                "--dump-json",
                "--no-playlist",
                "--no-update",
                "--js-runtimes", "deno:" + DENO,
                URL
            );
            pb.redirectErrorStream(false);
            Process proceso = pb.start();

            new Thread(() -> {
                try {
                    new BufferedReader(
                        new InputStreamReader(proceso.getErrorStream())
                    ).lines().forEach(l -> {});
                } catch (Exception ignored) {}
            }).start();

            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(proceso.getInputStream())
            );
            String linea;
            while ((linea = reader.readLine()) != null) {
                sb.append(linea);
            }
            proceso.waitFor();
            return sb.length() > 0 ? sb.toString() : null;

        } catch (Exception e) {
            System.out.println("Error obteniendo JSON: " + e.getMessage());
            return null;
        }
    }

    private static void parsearYMostrar(String json) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);

            String titulo   = getString(root, "title");
            String duracion = getString(root, "duration_string");
            String uploader = getString(root, "uploader");

            System.out.println("TITULO   : " + titulo);
            System.out.println("CANAL    : " + uploader);
            System.out.println("DURACION : " + duracion);
            System.out.println();

            JsonArray formatos = root.getAsJsonArray("formats");
            if (formatos == null) {
                System.out.println("No se encontraron formatos.");
                return;
            }

            List<MediaItem> videos = new ArrayList<>();
            List<MediaItem> audios = new ArrayList<>();

            for (JsonElement el : formatos) {
                JsonObject fmt = el.getAsJsonObject();

                String ext     = getString(fmt, "ext");
                String vcodec  = getString(fmt, "vcodec");
                String acodec  = getString(fmt, "acodec");
                String itemUrl = getString(fmt, "url");
                int    height  = getInt(fmt, "height");
                double abr     = getDouble(fmt, "abr");
                long   size    = getLong(fmt, "filesize");
                if (size == 0) size = getLong(fmt, "filesize_approx");

                if (ext.equals("mhtml") || itemUrl.isEmpty()) continue;

                String tamano = size > 0
                    ? String.format("%.1f MB", size / 1048576.0)
                    : "?";

                boolean tieneVideo = !vcodec.equals("none") && !vcodec.isEmpty();
                boolean tieneAudio = !acodec.equals("none") && !acodec.isEmpty();

                if (tieneVideo && height >= 360) {
                    videos.add(new MediaItem("VIDEO", ext, height + "p", tamano, itemUrl));
                } else if (!tieneVideo && tieneAudio && abr > 0) {
                    audios.add(new MediaItem("AUDIO", ext,
                        String.format("%.0fk", abr), tamano, itemUrl));
                }
            }

            // Tabla resumen
            System.out.printf("%-6s %-8s %-8s %-10s %-10s%n",
                "NUM", "TIPO", "EXT", "CALIDAD", "TAMAÑO");
            System.out.println("----------------------------------------------");

            int num = 1;
            for (MediaItem v : videos) {
                System.out.printf("%-6s %-8s %-8s %-10s %-10s%n",
                    "[" + num++ + "]", v.tipo, v.formato, v.calidad, v.tamano);
            }
            for (MediaItem a : audios) {
                System.out.printf("%-6s %-8s %-8s %-10s %-10s%n",
                    "[" + num++ + "]", a.tipo, a.formato, a.calidad, a.tamano);
            }

            // URLs completas
            System.out.println();
            System.out.println("URLs completas:");
            System.out.println("----------------------------------------------");

            num = 1;
            for (MediaItem v : videos) {
                System.out.println("[" + num++ + "] VIDEO "
                    + v.formato + " " + v.calidad + " | " + v.tamano);
                System.out.println("    " + v.url);
                System.out.println();
            }
            for (MediaItem a : audios) {
                System.out.println("[" + num++ + "] AUDIO "
                    + a.formato + " " + a.calidad + " | " + a.tamano);
                System.out.println("    " + a.url);
                System.out.println();
            }

        } catch (Exception e) {
            System.out.println("Error parseando JSON: " + e.getMessage());
        }
    }

    static class MediaItem {
        String tipo, formato, calidad, tamano, url;

        MediaItem(String tipo, String formato, String calidad, String tamano, String url) {
            this.tipo    = tipo;
            this.formato = formato;
            this.calidad = calidad;
            this.tamano  = tamano;
            this.url     = url;
        }
    }

    private static String getString(JsonObject obj, String key) {
        try {
            JsonElement el = obj.get(key);
            return (el != null && !el.isJsonNull()) ? el.getAsString() : "";
        } catch (Exception e) { return ""; }
    }

    private static int getInt(JsonObject obj, String key) {
        try {
            JsonElement el = obj.get(key);
            return (el != null && !el.isJsonNull()) ? el.getAsInt() : 0;
        } catch (Exception e) { return 0; }
    }

    private static double getDouble(JsonObject obj, String key) {
        try {
            JsonElement el = obj.get(key);
            return (el != null && !el.isJsonNull()) ? el.getAsDouble() : 0;
        } catch (Exception e) { return 0; }
    }

    private static long getLong(JsonObject obj, String key) {
        try {
            JsonElement el = obj.get(key);
            return (el != null && !el.isJsonNull()) ? el.getAsLong() : 0;
        } catch (Exception e) { return 0; }
    }

    private static boolean ytDlpDisponible() {
        try {
            ProcessBuilder pb = new ProcessBuilder(YTDLP, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream())
            );
            String version = r.readLine();
            if (version != null && !version.isEmpty()) {
                System.out.println("yt-dlp version: " + version);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}