package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  WebServer.java - the local web interface of Pokemon Randomizer YAO.   --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.uprfvx.random.Settings;
import com.uprfvx.random.Version;
import com.uprfvx.random.yao.EvolutionEditor;
import com.uprfvx.random.yao.YaoSettings;
import com.uprfvx.romio.RootPath;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.romio.RomOpener;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * A small web server on the loopback address only (never reachable from the network) that serves the page and a JSON
 * interface to the program: pick a game, see its options, run the randomization, watch it, get the result. Every call
 * to the interface needs the random token of this run, and the {@code Host} header must be the loopback address, so a
 * web page opened in another tab of the browser cannot talk to it.
 */
public final class WebServer {

    private static final int MAX_BODY = 4 * 1024 * 1024;

    // Added for YAO (11/10/2026): a dropped ROM is uploaded as a raw request body, not JSON, so it is streamed
    // straight to a temp file instead of going through readBody()'s MAX_BODY-limited, fully-buffered read. 3DS
    // games are the largest this program opens (well under 4 GB); this cap is generous headroom above that.
    private static final long MAX_UPLOAD = 8_000_000_000L;

    private final HttpServer server;
    private final String token;
    private final int port;
    private final SettingsSchema schema = new SettingsSchema();
    private final RomOpener opener = new RomOpener();
    private final RomSession session;
    private final JobRunner jobs = new JobRunner();

    public WebServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        this.port = server.getAddress().getPort();
        byte[] raw = new byte[16];
        new SecureRandom().nextBytes(raw);
        this.token = HexFormat.of().formatHex(raw);
        opener.setExtraMemoryAvailable(Runtime.getRuntime().maxMemory() >= 3_800_000_000L);
        this.session = new RomSession(schema, opener);
        server.setExecutor(Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "web");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return port;
    }

    /** The address to open in the browser (it carries the token of this run). */
    public String url() {
        return "http://127.0.0.1:" + port + "/?t=" + token;
    }

    // ---------------------------------------------------------------- dispatch

    private void handle(HttpExchange ex) throws IOException {
        try {
            if (!hostAllowed(ex)) {
                send(ex, 403, "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                if (!token.equals(ex.getRequestHeaders().getFirst("X-YAO-Token"))) {
                    sendJson(ex, 401, error("This page was not opened by the program's launcher. Use the address the "
                            + "program printed."));
                    return;
                }
                api(ex, path);
            } else {
                staticFile(ex, path);
            }
        } catch (Json.JsonException e) {
            sendJson(ex, 400, error("The request was not understood: " + e.getMessage()));
        } catch (RuntimeException | StackOverflowError | OutOfMemoryError e) {
            e.printStackTrace();
            sendJson(ex, 500, error("Something went wrong in the program: " + e));
        } finally {
            ex.close();
        }
    }

    private boolean hostAllowed(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("127.0.0.1:" + port) || h.equals("localhost:" + port);
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", message);
        return m;
    }

    // ---------------------------------------------------------------- static files

    private void staticFile(HttpExchange ex, String path) throws IOException {
        String name = path.equals("/") ? "index.html" : path.substring(1);
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        // development only: -Dyao.web.dir=<folder> serves the page files from disk, so they can be edited without a rebuild
        String devDir = System.getProperty("yao.web.dir");
        File devFile = devDir == null ? null : new File(devDir, name);
        try (InputStream in = devFile != null && devFile.isFile() ? new FileInputStream(devFile)
                : WebServer.class.getResourceAsStream(name)) {
            if (in == null || name.equals("settings-meta.json")) {
                send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                    : name.endsWith(".js") ? "text/javascript; charset=utf-8"
                    : name.endsWith(".css") ? "text/css; charset=utf-8"
                    : name.endsWith(".svg") ? "image/svg+xml"
                    : "application/octet-stream";
            send(ex, 200, type, in.readAllBytes());
        }
    }

    // ---------------------------------------------------------------- the JSON interface

    private void api(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();
        Map<String, String> query = query(ex.getRequestURI());
        switch (path) {
            case "/api/info" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ok", true);
                m.put("version", Version.LATEST.name);
                m.put("javaMaxMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
                m.put("threeDsReady", Runtime.getRuntime().maxMemory() >= 3_800_000_000L);
                m.put("os", System.getProperty("os.name", ""));
                m.put("folder", new File(".").getAbsoluteFile().getParent());
                m.put("running", jobs.anyRunning());
                m.put("rom", session.isLoaded() ? session.info() : null);
                Map<String, Object> notices = new LinkedHashMap<>();
                notices.put("codePatchEn", com.uprfvx.random.yao.CodePatchDisclaimer.ENGLISH);
                notices.put("codePatchEs", com.uprfvx.random.yao.CodePatchDisclaimer.SPANISH);
                notices.put("guideEn", com.uprfvx.random.yao.guide.GuideDisclaimer.ENGLISH);
                notices.put("guideEs", com.uprfvx.random.yao.guide.GuideDisclaimer.SPANISH);
                m.put("notices", notices);
                sendJson(ex, 200, m);
            }
            // Modified from YAO (11/10/2026): the old in-browser custom file picker (FileBrowser, "/api/fs") was
            // removed, replaced by a native OS file dialog opened server side (see NativeFileDialog) plus
            // drag-and-drop in the page itself (see "/api/upload" below).
            case "/api/native-dialog" -> {
                Json.Obj body = new Json.Obj(Json.parseObject(readBody(ex)));
                sendJson(ex, 200, NativeFileDialog.choose(body.string("mode", "open"), body.string("filter", ""),
                        body.string("title", ""), body.string("startDir", "")));
            }
            case "/api/upload" -> upload(ex);
            case "/api/rom" -> {
                if (method.equals("POST")) {
                    Json.Obj body = new Json.Obj(Json.parseObject(readBody(ex)));
                    sendJson(ex, 200, session.load(body.string("path", "")));
                } else {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ok", session.isLoaded());
                    m.put("game", session.isLoaded() ? session.info() : null);
                    sendJson(ex, 200, m);
                }
            }
            case "/api/schema" -> {
                Map<String, Object> m = session.isLoaded() ? session.schemaForRom() : schema.describe(null);
                sendJson(ex, 200, m);
            }
            case "/api/items" -> sendJson(ex, 200, listOf(session.items()));
            case "/api/species" -> sendJson(ex, 200, listOf(session.species()));
            case "/api/moves" -> sendJson(ex, 200, listOf(session.moves()));
            case "/api/trainer-progress" -> sendJson(ex, 200, listOf(session.trainerProgress()));
            case "/api/evolution-types" -> sendJson(ex, 200, listOf(session.evolutionTypes()));
            // Added (11/10/2026, web UI feedback item 12): the frontend used to guess at mutual exclusion with
            // its own "same from and to" check, which missed the actual rule (two evolutions of one Pokemon that
            // use the same trigger, per EvolutionEditor.overlaps). This asks the real rule instead, over whatever
            // evolution edits are currently configured in the web interface (not yet applied to a ROM), so the
            // frontend can highlight conflicting rows live as they are entered, before a run is ever started.
            case "/api/evolution-conflicts" -> sendJson(ex, 200, evolutionConflicts(new Json.Obj(Json.parseObject(readBody(ex)))));
            case "/api/settings/export" -> {
                Settings settings = buildSettings(new Json.Obj(Json.parseObject(readBody(ex))));
                Map<String, Object> m = new LinkedHashMap<>();
                try {
                    m.put("ok", true);
                    m.put("text", settings.toString());
                } catch (RuntimeException e) {
                    m = error("The settings could not be turned into text: " + e);
                }
                sendJson(ex, 200, m);
            }
            // Added (12/10/2026): the real .rnqs file bytes (base64), YAO block included, for the interface's download button.
            case "/api/settings/export-file" -> sendJson(ex, 200, exportSettingsFile(new Json.Obj(Json.parseObject(readBody(ex)))));
            case "/api/settings/import" -> sendJson(ex, 200, importSettings(new Json.Obj(Json.parseObject(readBody(ex)))));
            case "/api/player-sprites/export" -> {
                Json.Obj body = new Json.Obj(Json.parseObject(readBody(ex)));
                String folder = body.string("folder", "").strip();
                if (!session.isLoaded() || folder.isEmpty()) {
                    sendJson(ex, 200, error("Choose a game and a folder first."));
                    return;
                }
                List<String> lines = JobRunner.exportSprites(new File(session.path()).getAbsolutePath(), folder, new File(RootPath.path));
                boolean ok = lines.get(0).equals("exit 0");
                Map<String, Object> m = ok ? new LinkedHashMap<>() : error("The sprites could not be exported.");
                m.put("ok", ok);
                m.put("lines", lines.subList(1, lines.size()));
                sendJson(ex, 200, m);
            }
            case "/api/run" -> sendJson(ex, 200, run(new Json.Obj(Json.parseObject(readBody(ex)))));
            case "/api/job" -> {
                JobRunner.Job job = jobs.get(query.get("id"));
                if (job == null) {
                    sendJson(ex, 404, error("No such run."));
                    return;
                }
                int from = 0;
                try {
                    from = Integer.parseInt(query.getOrDefault("from", "0"));
                } catch (NumberFormatException ignored) {
                    // from the start
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ok", true);
                m.put("status", job.status);
                m.put("exitCode", job.exitCode);
                m.put("lines", job.linesFrom(from));
                m.put("next", job.lineCount());
                m.put("output", job.output);
                m.put("guide", job.guide != null && new File(job.guide).isFile() ? job.guide : null);
                m.put("log", job.logFile != null && new File(job.logFile).isFile() ? job.logFile : null);
                m.put("seconds", (System.currentTimeMillis() - job.startedAt) / 1000);
                sendJson(ex, 200, m);
            }
            case "/api/job/cancel" -> {
                Json.Obj body = new Json.Obj(Json.parseObject(readBody(ex)));
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ok", jobs.cancel(body.string("id", "")));
                sendJson(ex, 200, m);
            }
            case "/api/job/guide" -> {
                JobRunner.Job job = jobs.get(query.get("id"));
                File guide = job == null || job.guide == null ? null : new File(job.guide);
                if (guide == null || !guide.isFile()) {
                    sendJson(ex, 404, error("There is no guide for this run."));
                    return;
                }
                send(ex, 200, "text/html; charset=utf-8", Files.readAllBytes(guide.toPath()));
            }
            default -> sendJson(ex, 404, error("Unknown request " + path + "."));
        }
    }

    private static Map<String, Object> listOf(List<Object> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("items", items);
        return m;
    }

    /**
     * Added (11/10/2026, web UI feedback item 12). Checks a set of in-progress evolution edits (not yet applied
     * to a ROM, just what the web interface currently has filled in) against {@link EvolutionEditor#overlaps},
     * the exact same rule the randomizer itself uses to refuse giving one Pokemon two evolutions with the same
     * trigger. Returns the indices (into the submitted list) of every edit that conflicts with another edit of
     * the same "from" Pokemon.
     */
    private Map<String, Object> evolutionConflicts(Json.Obj body) {
        List<Object> entries = body.list("evolutions");
        int n = entries.size();
        int[] froms = new int[n];
        int[] extras = new int[n];
        EvolutionType[] types = new EvolutionType[n];
        for (int i = 0; i < n; i++) {
            if (entries.get(i) instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked") Json.Obj o = new Json.Obj((Map<String, Object>) m);
                froms[i] = o.integer("from", 0);
                extras[i] = o.integer("extra", 0);
                try {
                    types[i] = EvolutionType.valueOf(o.string("type", ""));
                } catch (IllegalArgumentException e) {
                    types[i] = null;
                }
            }
        }
        Set<Integer> conflicts = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) {
            if (froms[i] <= 0 || types[i] == null) {
                continue;
            }
            for (int j = i + 1; j < n; j++) {
                if (froms[j] != froms[i] || types[j] == null) {
                    continue;
                }
                if (EvolutionEditor.overlaps(types[i], extras[i], types[j], extras[j])) {
                    conflicts.add(i);
                    conflicts.add(j);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("conflicts", new ArrayList<>(conflicts));
        return out;
    }

    private Settings buildSettings(Json.Obj body) {
        Settings settings = schema.build(body.object("values").raw(), session.isLoaded() ? session.miscTweaksAvailable() : -1,
                session.isLoaded() ? session.romName() : "");
        // Modified (12/10/2026): the web interface hides the native "fully evolved by level" slider (it has its own
        // independent control), so the native level is set to a no-op (above any evolution level in the games):
        // ticking only "Make Evolutions Easier" no longer silently condenses evolutions to the old default level.
        settings.setMakeEvolutionsEasierLvl(100);
        YaoJson.apply(settings.getYaoSettings(), body.object("yao"));
        return settings;
    }

    /** Writes the settings as a real .rnqs file (see {@link Settings#writeToFileFormat}) and returns its bytes in base64. */
    private Map<String, Object> exportSettingsFile(Json.Obj body) {
        Settings settings = buildSettings(body);
        try {
            File temp = File.createTempFile("yao-settings-out", ".rnqs");
            try {
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    settings.writeToFileFormat(out);
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ok", true);
                m.put("file", Base64.getEncoder().encodeToString(Files.readAllBytes(temp.toPath())));
                return m;
            } finally {
                temp.delete();
            }
        } catch (IOException | RuntimeException e) {
            return error("The settings file could not be written: " + e);
        }
    }

    /** Reads a settings string or a settings file (base64 of its bytes) into the interface's values. */
    private Map<String, Object> importSettings(Json.Obj body) {
        Settings settings;
        try {
            if (body.has("file")) {
                byte[] bytes = Base64.getDecoder().decode(body.string("file", ""));
                File temp = File.createTempFile("yao-settings", ".rnqs");
                try {
                    Files.write(temp.toPath(), bytes);
                    try (FileInputStream in = new FileInputStream(temp)) {
                        settings = Settings.readFromFileFormat(in);
                    }
                } finally {
                    temp.delete();
                }
            } else {
                settings = Settings.fromString(body.string("text", "").strip());
            }
        } catch (RuntimeException | IOException e) {
            return error("Those settings could not be read: " + e.getMessage());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("values", schema.extract(settings));
        m.put("yao", YaoJson.extract(settings.getYaoSettings()));
        return m;
    }

    /** Added (12/10/2026): plain words on how to play the result, shown after the run. */
    static String howToUse(boolean threeDs, boolean folder, File location, String titleId) {
        if (!threeDs) {
            return "Open " + location.getName() + " in your emulator or flash cart. The original game was not changed.";
        }
        if (!folder) {
            return "Open " + location.getName() + " in Citra or Azahar (File, Load File). It runs on its own. Do not have "
                    + "a game update installed for this game in the emulator, the file already contains the game's code.";
        }
        String id = titleId == null || titleId.isBlank() ? "the 16 digit folder" : titleId;
        return "This folder only holds what changed, it is not a game on its own. On a 3DS with Luma3DS: copy the \"luma\" "
                + "folder inside " + location.getName() + " to the root of the SD card (it must end up as /luma/titles/" + id
                + "), turn on \"Enable game patching\" in the Luma menu (hold Select while switching the console on) and "
                + "start the game. In Citra or Azahar: right-click the game, \"Open Mods Location\", and copy what is inside "
                + id + " there (code.bin and romfs). In both cases the SAME game version as the one you randomized must be "
                + "installed (if you did not load a game update, none must be installed), otherwise the game crashes or "
                + "ignores the changes. To get a single file that opens in an emulator, save as a .cxi file instead.";
    }

    private Map<String, Object> run(Json.Obj body) {
        if (!session.isLoaded()) {
            return error("Choose a game first.");
        }
        if (jobs.anyRunning()) {
            return error("A randomization is already running.");
        }
        File rom = new File(session.path());
        boolean threeDs = session.generation() == 6 || session.generation() == 7;
        // Modified for YAO (12/10/2026): the destination is worked out here, once, by OutputPaths (adds the right
        // extension, treats a 3DS folder as a folder, never replaces the original) instead of passing the typed
        // text on and guessing later what the command line would write.
        OutputPaths.ThreeDsFormat format = OutputPaths.ThreeDsFormat.parse(body.string("threeDsFormat", "folder"));
        String titleId = null;
        if (threeDs) {
            try {
                titleId = com.uprfvx.romio.romhandlers.Abstract3DSRomHandler.getTitleIdFromFile(rom.getAbsolutePath());
            } catch (RuntimeException e) {
                titleId = null; // only used to notice an existing result
            }
        }
        OutputPaths.Resolved target;
        try {
            target = OutputPaths.resolve(body.string("output", ""), rom, threeDs,
                    String.valueOf(session.info().get("extension")), format, titleId);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
        if (target.existing != null && !body.bool("overwrite", false)) {
            Map<String, Object> m = error(target.directory ? "That folder already holds a randomized game." : "That file already exists.");
            m.put("exists", target.existing.getPath());
            m.put("resolvedOutput", target.location.getPath());
            return m;
        }
        try {
            OutputPaths.prepare(target);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
        String expected = target.location.getPath();
        Settings settings = buildSettings(body);
        YaoSettings yao = settings.getYaoSettings();
        yao.setCodePatchesAccepted(body.bool("acceptCodePatches", false));
        File settingsFile;
        try {
            settingsFile = File.createTempFile("yao-run", ".rnqs");
            settingsFile.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(settingsFile)) {
                settings.writeToFileFormat(out);
            }
        } catch (IOException | RuntimeException e) {
            return error("The settings could not be saved for the run: " + e);
        }
        String guide = body.bool("guide", false) ? target.companionBase + ".guide.html" : null;
        boolean log = body.bool("saveLog", true);
        String seed = body.string("seed", "").strip();
        List<String> command = JobRunner.command(rom.getAbsolutePath(), target.cliOutput, settingsFile.getAbsolutePath(), seed,
                threeDs, target.directory, yao.isCodePatchesAccepted(), guide, log);
        // The command line writes its log next to what it is given; for a LayeredFS folder that is the luma/titles
        // folder, which would end up on the SD card, so the log is moved next to the result when the run is done.
        File cliLog = new File(target.cliOutput + ".log");
        File finalLog = new File(target.companionBase + ".log");
        JobRunner.Job job = jobs.start(command, new File(RootPath.path), expected, guide, log ? finalLog.getPath() : null,
                log && !cliLog.equals(finalLog) ? () -> JobRunner.moveFile(cliLog, finalLog) : null);
        List<String> warnings = new ArrayList<>(yao.getWarnings());
        warnings.addAll(target.warnings);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("job", job.id);
        m.put("output", expected);
        // Added (12/10/2026): the real destination, what kind it is, what was changed from what was typed, and how
        // to use the result, so the interface can show them.
        m.put("resolvedOutput", expected);
        m.put("outputKind", target.directory ? "folder" : "file");
        m.put("outputWarnings", target.warnings);
        m.put("howToUse", howToUse(threeDs, target.directory, target.location, titleId));
        m.put("warnings", warnings);
        return m;
    }

    // ---------------------------------------------------------------- plumbing

    private static Map<String, String> query(URI uri) {
        Map<String, String> map = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }

    /**
     * Added for YAO (11/10/2026): a ROM dragged onto the page cannot be opened by the path the browser knows (a
     * browser never exposes the real filesystem path of a dropped file, only its name and bytes), so the page
     * instead POSTs the file's raw bytes here and gets back a real path on this machine to use for the rest of the
     * flow. The body is streamed straight to a temp file, not buffered in memory or run through readBody()'s
     * MAX_BODY limit, because ROMs (especially decrypted 3DS dumps) can be gigabytes, far past what should ever be
     * held as one in-memory byte array or JSON string.
     */
    private void upload(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, error("Use POST to upload a file."));
            return;
        }
        String rawName = ex.getRequestHeaders().getFirst("X-File-Name");
        String name = rawName == null || rawName.isBlank() ? "upload.bin"
                : URLDecoder.decode(rawName, StandardCharsets.UTF_8);
        // keep only a safe basename: no path separators, no traversal
        name = new File(name).getName();
        if (name.isBlank()) {
            name = "upload.bin";
        }
        int dot = name.lastIndexOf('.');
        String suffix = dot >= 0 && dot < name.length() - 1 ? name.substring(dot) : ".bin";
        File dir = new File(System.getProperty("java.io.tmpdir", "."), "yao-uploads");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            sendJson(ex, 500, error("A temporary folder for the upload could not be created."));
            return;
        }
        File temp = File.createTempFile("yao-drop-", suffix, dir);
        temp.deleteOnExit();
        long written = 0;
        boolean tooLarge = false;
        try (InputStream in = ex.getRequestBody(); OutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) != -1) {
                written += n;
                if (written > MAX_UPLOAD) {
                    tooLarge = true;
                    break;
                }
                out.write(buffer, 0, n);
            }
        }
        if (tooLarge) {
            temp.delete();
            sendJson(ex, 413, error("That file is larger than this program accepts (" + (MAX_UPLOAD / 1_000_000_000L)
                    + " GB)."));
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("path", temp.getAbsolutePath());
        sendJson(ex, 200, m);
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) {
                throw new Json.JsonException("The request is too large.");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange ex, int status, Object value) throws IOException {
        send(ex, status, "application/json; charset=utf-8", Json.write(value).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        Headers headers = ex.getResponseHeaders();
        headers.set("Content-Type", type);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; frame-ancestors 'none'");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    // unused but kept for tests: an empty stream
    static InputStream empty() {
        return new ByteArrayInputStream(new byte[0]);
    }
}
