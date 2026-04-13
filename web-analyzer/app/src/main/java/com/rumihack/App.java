package com.rumihack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class App {

    // Cambia la URL aqui para probar distintos sitios
    private static final String URL = "https://www.youtube.com/live/TgMtUBZ0lpM";

    // Ruta de yt-dlp
    private static final String YTDLP = "/usr/local/bin/yt-dlp";

    public static void main(String[] args) {

        System.out.println("===========================================");
        System.out.println(" Web Media Analyzer - rumihack");
        System.out.println(" URL: " + URL);
        System.out.println("===========================================\n");

        if (!ytDlpDisponible()) {
            System.out.println("ERROR: yt-dlp no encontrado en: " + YTDLP);
            return;
        }

        System.out.println("Analizando, por favor espera...\n");

        List<String> urls = extraerUrls(URL);

        if (urls.isEmpty()) {
            System.out.println("No se encontraron URLs.");
        } else {
            System.out.println("URLs encontradas: " + urls.size() + "\n");
            for (int i = 0; i < urls.size(); i++) {
                System.out.println("[" + (i + 1) + "] " + urls.get(i));
            }
        }
    }

    public static List<String> extraerUrls(String url) {
        List<String> urls = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                YTDLP,
                "--get-url",
                "--no-playlist",
                "--no-update",
                "--extractor-args", "generic:impersonate=chrome",
                url
            );

            pb.redirectErrorStream(false);
            Process proceso = pb.start();

            BufferedReader stdout = new BufferedReader(
                new InputStreamReader(proceso.getInputStream())
            );
            BufferedReader stderr = new BufferedReader(
                new InputStreamReader(proceso.getErrorStream())
            );

            Thread hiloErrores = new Thread(() -> {
                try {
                    String linea;
                    while ((linea = stderr.readLine()) != null) {
                        errores.add(linea.trim());
                    }
                } catch (Exception e) {
                    // ignorar
                }
            });
            hiloErrores.start();

            String linea;
            while ((linea = stdout.readLine()) != null) {
                linea = linea.trim();
                if (!linea.isEmpty()) {
                    urls.add(linea);
                }
            }

            hiloErrores.join();
            proceso.waitFor();

            if (urls.isEmpty()) {
                System.out.println("--- Detalle ---");
                for (String e : errores) {
                    if (!e.startsWith("WARNING")) {
                        System.out.println(e);
                    }
                }
                System.out.println("---------------\n");
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        return urls;
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
        } catch (Exception e) {
            // no encontrado
        }
        return false;
    }
}