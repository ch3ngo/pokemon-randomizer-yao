package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  WebLauncher.java - starts the local web interface and opens it.       --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Entry point of {@code java -jar PokemonRandomizerYAO.jar web}: starts the server on a free port of the loopback address, prints
 * the address and opens it in the default browser. {@code --port N} picks the first port to try and {@code --no-browser}
 * only prints the address. The program keeps running until the window it was started from is closed (or Ctrl+C).
 */
public final class WebLauncher {

    private WebLauncher() {
    }

    public static int run(String[] args) {
        int firstPort = 8765;
        boolean openBrowser = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> {
                    if (i + 1 < args.length) {
                        try {
                            firstPort = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("--port needs a number; using " + firstPort + ".");
                        }
                    }
                }
                case "--no-browser" -> openBrowser = false;
                case "-h", "--help" -> {
                    System.out.println("Usage: java -jar PokemonRandomizerYAO.jar web [--port <n>] [--no-browser]");
                    return 0;
                }
                default -> System.err.println("Unknown option " + args[i] + " ignored.");
            }
        }

        WebServer server = null;
        IOException last = null;
        for (int port = firstPort; port < firstPort + 50 && server == null; port++) {
            try {
                server = new WebServer(port);
            } catch (IOException e) {
                last = e;
            }
        }
        if (server == null) {
            System.err.println("The web interface could not start: " + (last == null ? "no free port" : last.getMessage()));
            return 1;
        }
        server.start();
        String url = server.url();
        System.out.println("Pokemon Randomizer YAO is running at:");
        System.out.println("  " + url);
        System.out.println("Keep this window open while you use it. Close it (or press Ctrl+C) to stop.");
        if (openBrowser) {
            open(url);
        }
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        server.stop();
        return 0;
    }

    private static void open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // try the platform commands below
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (isWsl()) {
                new ProcessBuilder("cmd.exe", "/c", "start", "", url.replace("&", "^&")).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException e) {
            System.out.println("Open the address above in your browser.");
        }
    }

    private static boolean isWsl() {
        try {
            return Files.readString(Path.of("/proc/version")).toLowerCase(Locale.ROOT).contains("microsoft");
        } catch (IOException e) {
            return false;
        }
    }
}
