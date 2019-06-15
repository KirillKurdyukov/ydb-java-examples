package tech.ydb.examples;

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

        try (GrpcTransport transport = GrpcTransport.forEndpoint(args.endpoint, args.database)
                .withAuthProvider(new TokenAuthProvider(ydbToken))
                .build())
        {
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
