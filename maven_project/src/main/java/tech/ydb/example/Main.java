package tech.ydb.example;

import tech.ydb.auth.iam.CloudAuthProvider;
import tech.ydb.core.Result;
import tech.ydb.core.Status;
import tech.ydb.core.auth.AuthProvider;
import tech.ydb.core.grpc.GrpcTransport;
import tech.ydb.table.Session;
import tech.ydb.table.TableClient;
import tech.ydb.table.description.TableColumn;
import tech.ydb.table.description.TableDescription;
import tech.ydb.table.query.Params;
import tech.ydb.table.result.ResultSetReader;
import tech.ydb.table.rpc.grpc.GrpcTableRpc;
import tech.ydb.table.transaction.TxControl;
import tech.ydb.table.values.PrimitiveType;
import yandex.cloud.sdk.auth.provider.ComputeEngineCredentialProvider;
import yandex.cloud.sdk.auth.provider.CredentialProvider;
import yandex.cloud.sdk.auth.provider.IamTokenCredentialProvider;
import yandex.cloud.sdk.auth.provider.OauthCredentialProvider;
import yandex.cloud.sdk.auth.provider.ApiKeyCredentialProvider;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.stream.Collectors;

import static tech.ydb.table.values.PrimitiveValue.float32;
import static tech.ydb.table.values.PrimitiveValue.utf8;

public final class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java -jar example.jar <endpoint> <database>");
        }
        String endpoint = args[0];
        String database = args[1];

        CredentialProvider credentialProvider;
        String iamToken = System.getenv("YDB_ACCESS_TOKEN_CREDENTIALS");
        if (iamToken == null) {
            iamToken = System.getenv("IAM_TOKEN"); // Deprecated name
        }
        String saKeyFile = System.getenv("YDB_SERVICE_ACCOUNT_KEY_FILE_CREDENTIALS");
        if (saKeyFile == null) {
            saKeyFile = System.getenv("SA_KEY_FILE"); // Deprecated name
        }
        String oauthToken = System.getenv("YC_TOKEN");
        if (oauthToken == null) {
            oauthToken = System.getenv("OAUTH_TOKEN"); // Deprecated name
        }
        if (iamToken != null) {
            credentialProvider = IamTokenCredentialProvider.builder()
                    .token(iamToken)
                    .build();
        } else if (oauthToken != null) {
            credentialProvider = OauthCredentialProvider.builder()
                    .oauth(oauthToken)
                    .build();
        } else if (saKeyFile != null) {
            credentialProvider = ApiKeyCredentialProvider.builder()
                    .fromFile(Paths.get(saKeyFile))
                    .build();
        } else {
            credentialProvider = ComputeEngineCredentialProvider.builder()
                    .build();
        }
        AuthProvider authProvider = CloudAuthProvider.newAuthProvider(credentialProvider);
        GrpcTransport transport = GrpcTransport.forEndpoint(endpoint, database)
                .withAuthProvider(authProvider)
                .withSecureConnection()
                .build();
        TableClient tableClient = TableClient.newClient(GrpcTableRpc.useTransport(transport))
                .build();
        Result<Session> sessionResult = tableClient.getOrCreateSession(Duration.ofSeconds(10))
                .join();
        Session session = sessionResult.expect("ok");

        TableDescription pets = TableDescription.newBuilder()
                .addNullableColumn("species", PrimitiveType.utf8())
                .addNullableColumn("name", PrimitiveType.utf8())
                .addNullableColumn("color", PrimitiveType.utf8())
                .addNullableColumn("price", PrimitiveType.float32())
                .setPrimaryKeys("species", "name")
                .build();

        Status createStatus = session.createTable(database + "/" + "java_example", pets).join();
        createStatus.expect("ok");

        TableDescription desc = session.describeTable(database + "/java_example").join().expect("ok");
        System.out.println("Columns reported by describeTable: " + desc.getColumns().stream().map(TableColumn::getName).collect(Collectors.toList()));

        String upsertQuery = String.format(
                "PRAGMA TablePathPrefix(\"%s\");\n" +
                        "\n" +
                        "DECLARE $species AS Utf8;\n" +
                        "DECLARE $name AS Utf8;\n" +
                        "DECLARE $color AS Utf8;\n" +
                        "DECLARE $price AS Float;\n" +
                        "UPSERT INTO java_example (species, name, color, price) VALUES\n" +
                        "($species, $name, $color, $price);",
                database);

        Params params = Params.of("$species", utf8("cat"), "$name", utf8("Tom"), "$color", utf8("black"), "$price", float32(10.0f));
        TxControl txControl = TxControl.serializableRw().setCommitTx(true);
        session.executeDataQuery(upsertQuery, txControl, params).join().expect("ok");

        String selectQuery = String.format(
                "PRAGMA TablePathPrefix(\"%s\");\n" +
                        "\n" +
                        "DECLARE $species AS Utf8;\n" +
                        "DECLARE $name AS Utf8;\n" +
                        "SELECT * FROM java_example\n" +
                        "WHERE species = $species AND name = $name;",
                database);
        txControl = TxControl.onlineRo();
        ResultSetReader rsReader = session.executeDataQuery(selectQuery, txControl, params).join().expect("ok").getResultSet(0);
        System.out.println();
        System.out.println("Result of select query:");
        while (rsReader.next()) {
            System.out.printf("species: %s%n", rsReader.getColumn("species").getUtf8());
            System.out.printf("name: %s%n", rsReader.getColumn("name").getUtf8());
            System.out.printf("color: %s%n", rsReader.getColumn("color").getUtf8());
            System.out.printf("price: %f%n", rsReader.getColumn("price").getFloat32());
            System.out.println();
        }

        session.dropTable(database + "/java_example").join().expect("ok");
    }
}
