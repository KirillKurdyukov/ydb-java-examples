package tech.ydb.demo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alexandr Gorshenin
 */
public class DemoTest {
    private static final Logger log = LoggerFactory.getLogger(DemoTest.class);

    private static Application app = null;
    private static YdbDockerContainer container = null;
    private static URI appURI;

    private static byte[] readResource(String path) throws IOException {
        return DemoTest.class.getClassLoader()
                .getResourceAsStream(path)
                .readAllBytes();
    }

    private static byte[] readIndexHtml() throws IOException {
        return readResource("webapp/index.html");
    }

    @BeforeAll
    public static void setUpYDB() throws Exception {
        PortsGenerator portGenerator = new PortsGenerator();

        String ydbDatabase = System.getenv("YDB_DATABASE");
        String ydbEndpoint = System.getenv("YDB_ENDPOINT");

        int testPort = portGenerator.findAvailablePort();
        List<String> args = new ArrayList<>();
        args.add("-p");
        args.add(String.valueOf(testPort));

        if (ydbEndpoint != null) {
            log.info("set up reciept YDB instance -e {} -d {}", ydbEndpoint, ydbDatabase);
            args.addAll(Arrays.asList("-e", ydbEndpoint, "-d", ydbDatabase));
        } else {
            log.info("set up YDB docker container");
            container = YdbDockerContainer.createAndStart(portGenerator);
            args.addAll(Arrays.asList(
                    "-e", container.secureEndpoint(),
                    "-d", container.database(),
                    "-c", container.pemCertPath()
            ));
        }

        app = new Application(AppParams.parseArgs(args.toArray(new String[0])));
        app.start();

        appURI = URI.create("http://localhost:" + String.valueOf(testPort));
    }

    @AfterAll
    public static void tearDownYDB() throws InterruptedException {
        app.close();
        app.join();

        if (container != null) {
            log.info("tear down YDB docker container");
            container.stop();
        }
    }

    @Test
    public void testCreateShortUrl() throws IOException, InterruptedException {
        // Check that GET / return index.html
        String index = httpGET("");
        Assertions.assertArrayEquals(readIndexHtml(), index.getBytes(), "index.html body incorrect");

        // Send invalid payload to /url
        httpPOST("/url", null, 400, "check payload validation - null");
        httpPOST("/url", "{}", 400, "check payload validation - empty");
        httpPOST("/url", "{ 'sourc': '" + appURI + "'}", 400, "check payload validation - syntax");

        // Send request to short current app url
        String created = httpPOST("/url", "{ 'source': '" + appURI + "'}");
        JsonElement json = JsonParser.parseString(created);

        Assertions.assertNotNull(json, "empty json response");
        Assertions.assertTrue(json.isJsonObject() && json.getAsJsonObject().has("hash"), "invalid json response");

        String hash = json.getAsJsonObject().get("hash").getAsString();

        // Create and send hash request to redirect to index.html
        httpGETRedirect("/" + hash, appURI.toString());
    }

    private String readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private String httpGET(String path) throws IOException, InterruptedException {
        URL url = appURI.resolve(path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        try {
            Assertions.assertEquals(200, conn.getResponseCode(), "response wrong " + path);
            return readFully(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private void httpGETRedirect(String path, String target) throws IOException, InterruptedException {
        URL url = appURI.resolve(path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);
        conn.connect();

        String location = conn.getHeaderField("location");
        Assertions.assertEquals(302, conn.getResponseCode(), "response wrong " + path);
        Assertions.assertEquals(target, location, "redirect target");
        conn.disconnect();
    }

    private void httpPOST(String path, String body, int statusCode, String msg)
            throws IOException, InterruptedException {
        URL url = appURI.resolve(path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        if (body != null) {
            conn.setDoOutput(true);
            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                wr.write(body.getBytes());
            }
        }
        conn.connect();
        Assertions.assertEquals(statusCode, conn.getResponseCode(), msg);
        conn.disconnect();
    }

    private String httpPOST(String path, String body) throws IOException, InterruptedException {
        URL url = appURI.resolve(path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        if (body != null) {
            conn.setDoOutput(true);
            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                wr.write(body.getBytes());
            }
        }
        conn.connect();

        try {
            Assertions.assertEquals(200, conn.getResponseCode(), "response wrong " + path);
            return readFully(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }
}
