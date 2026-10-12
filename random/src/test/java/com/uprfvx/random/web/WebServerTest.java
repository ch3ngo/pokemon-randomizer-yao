package com.uprfvx.random.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WebServerTest {

    private static WebServer server;
    private static String token;
    private static String base;
    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    public static void start() throws Exception {
        server = new WebServer(0);
        server.start();
        base = "http://127.0.0.1:" + server.port();
        token = server.url().substring(server.url().indexOf("t=") + 2);
    }

    @AfterAll
    public static void stop() {
        server.stop();
    }

    private static HttpResponse<String> get(String path, boolean withToken) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).GET();
        if (withToken) {
            b.header("X-YAO-Token", token);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).header("X-YAO-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void thePageAndItsFilesAreServed() throws Exception {
        HttpResponse<String> page = get("/", false);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Pokemon Randomizer YAO"));
        assertEquals(200, get("/app.js", false).statusCode());
        assertEquals(200, get("/app.css", false).statusCode());
        assertTrue(page.headers().firstValue("Content-Security-Policy").isPresent());
    }

    @Test
    public void internalFilesAndOtherPathsAreNotServed() throws Exception {
        assertEquals(404, get("/settings-meta.json", false).statusCode());
        assertEquals(404, get("/..%2f..%2fetc%2fpasswd", false).statusCode());
        assertEquals(404, get("/nothing.txt", false).statusCode());
    }

    @Test
    public void theInterfaceNeedsTheTokenOfThisRun() throws Exception {
        assertEquals(401, get("/api/info", false).statusCode());
        HttpRequest wrong = HttpRequest.newBuilder(URI.create(base + "/api/info")).header("X-YAO-Token", "nope").GET().build();
        assertEquals(401, client.send(wrong, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200, get("/api/info", true).statusCode());
    }

    @Test
    public void infoSaysWhatTheProgramIs() throws Exception {
        Map<String, Object> info = Json.parseObject(get("/api/info", true).body());
        assertEquals(true, info.get("ok"));
        assertNotNull(info.get("version"));
        assertNotNull(((Map<?, ?>) info.get("notices")).get("codePatchEn"));
    }

    @Test
    public void schemaListsTheOptions() throws Exception {
        Map<String, Object> schema = Json.parseObject(get("/api/schema", true).body());
        assertFalse(((java.util.List<?>) schema.get("sections")).isEmpty());
    }

    @Test
    public void itemsAreEmptyBeforeAGameIsChosen() throws Exception {
        Map<String, Object> items = Json.parseObject(get("/api/items", true).body());
        assertTrue(((java.util.List<?>) items.get("items")).isEmpty());
    }

    // Modified from YAO (11/10/2026): the old in-browser custom file browser ("/api/fs", FileBrowser) was removed
    // and replaced by a native OS file dialog (NativeFileDialog, tested in NativeFileDialogTest) plus the
    // drag-and-drop upload endpoint below.
    @Test
    public void uploadStreamsBytesToATempFileAndReturnsItsPath() throws Exception {
        byte[] bytes = new byte[200_000]; // bigger than a trivial buffer, to exercise more than one read() loop
        new java.util.Random(1).nextBytes(bytes);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/upload"))
                .header("X-YAO-Token", token)
                .header("X-File-Name", java.net.URLEncoder.encode("My Game.nds", java.nio.charset.StandardCharsets.UTF_8))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        Map<String, Object> body = Json.parseObject(res.body());
        assertEquals(true, body.get("ok"));
        String path = (String) body.get("path");
        assertNotNull(path);
        File uploaded = new File(path);
        assertTrue(uploaded.isFile());
        assertEquals(bytes.length, uploaded.length());
        assertTrue(uploaded.getName().endsWith(".nds"), "keeps the original extension: " + uploaded.getName());
        assertArrayEquals(bytes, Files.readAllBytes(uploaded.toPath()));
        uploaded.delete();
    }

    @Test
    public void uploadRejectsNonPostRequests() throws Exception {
        HttpResponse<String> res = get("/api/upload", true);
        assertEquals(405, res.statusCode());
    }

    @Test
    public void aMissingGameIsReportedNotThrown() throws Exception {
        Map<String, Object> r = Json.parseObject(post("/api/rom", "{\"path\":\"/definitely/not/here.nds\"}").body());
        assertEquals(false, r.get("ok"));
        assertNotNull(r.get("error"));
        Map<String, Object> notAGame = Json.parseObject(post("/api/rom", "{\"path\":\"" + System.getProperty("java.home").replace("\\", "\\\\") + "\"}").body());
        assertEquals(false, notAGame.get("ok"));
    }

    @Test
    public void aRunWithoutAGameIsRefused() throws Exception {
        Map<String, Object> r = Json.parseObject(post("/api/run", "{\"output\":\"x\"}").body());
        assertEquals(false, r.get("ok"));
    }

    @Test
    public void badRequestsGetAnErrorNotACrash() throws Exception {
        HttpResponse<String> r = post("/api/rom", "{not json");
        assertEquals(400, r.statusCode());
        assertEquals(404, get("/api/unknown", true).statusCode());
        assertEquals(404, get("/api/job?id=nothing", true).statusCode());
    }

    @Test
    public void settingsExportAndImportRoundTrip() throws Exception {
        String body = "{\"values\":{\"startersMod\":\"COMPLETELY_RANDOM\"},\"yao\":{\"shinyPercent\":12.5,\"eggBox\":{\"count\":4,\"legendary\":\"none\"}}}";
        Map<String, Object> exported = Json.parseObject(post("/api/settings/export", body).body());
        assertEquals(true, exported.get("ok"), String.valueOf(exported));
        Map<String, Object> imported = Json.parseObject(post("/api/settings/import",
                "{\"text\":\"" + exported.get("text") + "\"}").body());
        assertEquals(true, imported.get("ok"));
        assertEquals("COMPLETELY_RANDOM", ((Map<?, ?>) imported.get("values")).get("startersMod"));
        Map<?, ?> yao = (Map<?, ?>) imported.get("yao");
        assertEquals(12.5, ((Number) yao.get("shinyPercent")).doubleValue());
        assertEquals(4, ((Number) ((Map<?, ?>) yao.get("eggBox")).get("count")).intValue());
        Map<String, Object> bad = Json.parseObject(post("/api/settings/import", "{\"text\":\"garbage\"}").body());
        assertEquals(false, bad.get("ok"));
    }

    @Test
    public void settingsFileExportAndImportRoundTrip() throws Exception {
        String body = "{\"values\":{\"startersMod\":\"COMPLETELY_RANDOM\"},\"yao\":{\"shinyPercent\":12.5,\"eggBox\":{\"count\":4,\"legendary\":\"none\"}}}";
        Map<String, Object> exported = Json.parseObject(post("/api/settings/export-file", body).body());
        assertEquals(true, exported.get("ok"), String.valueOf(exported));
        assertTrue(java.util.Base64.getDecoder().decode((String) exported.get("file")).length > 0);
        Map<String, Object> imported = Json.parseObject(post("/api/settings/import",
                "{\"file\":\"" + exported.get("file") + "\"}").body());
        assertEquals(true, imported.get("ok"), String.valueOf(imported));
        assertEquals("COMPLETELY_RANDOM", ((Map<?, ?>) imported.get("values")).get("startersMod"));
        Map<?, ?> yao = (Map<?, ?>) imported.get("yao");
        assertEquals(12.5, ((Number) yao.get("shinyPercent")).doubleValue());
        assertEquals(4, ((Number) ((Map<?, ?>) yao.get("eggBox")).get("count")).intValue());
    }
}
