package tech.ydb.examples;

import tech.ydb.core.grpc.GrpcTransport;
import tech.ydb.examples.batch_upload.BatchUpload;
import tech.ydb.examples.bulk_upsert.BulkUpsert;
import tech.ydb.examples.suites.TableWithPartitioningSettings;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.TableClient;
import tech.ydb.table.rpc.grpc.GrpcTableRpc;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alexandr Gorshenin
 */
@DisabledIfSystemProperty(named = "DISABLE_INTEGRATION_TESTS", matches = "TRUE")
public class ExamplesTest {
    private static final Logger log = LoggerFactory.getLogger(ExamplesTest.class);

    private static String[] args = new String[] {};
    private static YdbDockerContainer container = null;

    @BeforeAll
    public static void setUpYDB() {
        String ydbDatabase = System.getenv("YDB_DATABASE");
        String ydbEndpoint = System.getenv("YDB_ENDPOINT");

        if (ydbEndpoint != null) {
            log.info("set up reciept YDB instance -e {} -d {}", ydbEndpoint, ydbDatabase);
            args = new String[] {"-e", ydbEndpoint, "-d", ydbDatabase};
        } else {
            log.info("set up YDB docker container");
            container = YdbDockerContainer.createAndStart();
            args = new String[] {"-e", container.nonSecureEndpoint(), "-d", container.database()};
        }
    }

    @AfterAll
    public static void tearDownYDB() {
        if (container != null) {
            log.info("tear down YDB docker container");
            container.stop();
        }
    }

    @Test
    public void testBatchUpload() {
        Assertions.assertEquals(0, BatchUpload.test(args), "Batch upload test");
    }

    @Test
    public void testBulkUpsert() {
        Assertions.assertEquals(0, BulkUpsert.test(args), "Bulk upsert test");
    }

    @Test
    public void testTableWithPartitioningSettings() {
        try (TableClient client = createTableClient()) {
            SessionRetryContext ctx = SessionRetryContext.create(client).build();
            (new TableWithPartitioningSettings(ctx, args[3])).run();
        }
    }

    private TableClient createTableClient() {
        GrpcTransport transport = GrpcTransport
                .forEndpoint(args[1], args[3])
                .build();

        return TableClient
                .newClient(GrpcTableRpc.ownTransport(transport))
                .build();
    }
}
