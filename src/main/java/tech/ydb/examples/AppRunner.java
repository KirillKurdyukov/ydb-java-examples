package tech.ydb.examples;

import java.nio.file.Files;
import java.nio.file.Paths;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import tech.ydb.core.auth.TokenAuthProvider;
import tech.ydb.core.grpc.GrpcTransport;


/**
 * @author Sergey Polovko
 */
public class AppRunner {

    private static final class Args {
        @Parameter(names = { "-e", "--endpoint" }, description = "YDB endpoint", required = true, help = true)
        String endpoint;

        @Parameter(names = { "-d", "--database" }, description = "YDB database name", required = true, help = true)
        String database;

        @Parameter(names = { "-p", "--path" }, description = "Base path for tables", help = true)
        String path;

        @Parameter(names = { "-c", "--cert" }, description = "Path to PEM certificate", help = true)
        String certPath;
    }

    public static void run(String appName, App.Factory appFactory, String... params) {
        Args args = new Args();
        JCommander jc = JCommander.newBuilder()
            .addObject(args)
            .build();
        jc.setProgramName(appName);

        try {
            jc.parse(params);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            StringBuilder sb = new StringBuilder();
            jc.usage(sb);
            System.err.println(sb.toString());
            System.exit(1);
        }

        String ydbToken = System.getenv("YDB_TOKEN");
        if (ydbToken == null || ydbToken.isEmpty()) {
            System.err.println("Please provide token with YDB_TOKEN environment variable");
            System.exit(1);
        }

        GrpcTransport.Builder transportBuilder = GrpcTransport.forEndpoint(args.endpoint, args.database)
            .withAuthProvider(new TokenAuthProvider(ydbToken));

        if (args.certPath != null) {
            try {
                transportBuilder.withSecureConnection(Files.readAllBytes(Paths.get(args.certPath)));
            } catch (Exception e) {
                System.err.println("Cannot read certificate from file " + args.certPath + ": " + e.getMessage());
                System.exit(1);
            }
        }

        try (GrpcTransport transport = transportBuilder.build()) {
            String path = args.path == null ? args.database : args.path;
            try (App example = appFactory.newApp(transport, path)) {
                example.run();
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}
