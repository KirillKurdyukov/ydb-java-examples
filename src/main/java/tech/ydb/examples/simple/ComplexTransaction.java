package tech.ydb.examples.simple;

import tech.ydb.core.rpc.RpcTransport;
import tech.ydb.table.Session;
import tech.ydb.table.TableClient;
import tech.ydb.table.description.TableDescription;
import tech.ydb.table.query.DataQueryResult;
import tech.ydb.table.rpc.grpc.GrpcTableRpc;
import tech.ydb.table.transaction.Transaction;
import tech.ydb.table.transaction.TransactionMode;
import tech.ydb.table.transaction.TxControl;
import tech.ydb.table.types.PrimitiveType;


/**
 * @author Sergey Polovko
 */
public class ComplexTransaction extends SimpleExample {

    @Override
    void run(RpcTransport transport, String pathPrefix) {
        String tablePath = pathPrefix + getClass().getSimpleName();
        String prevSessionId;

        try (TableClient tableClient = TableClient.newClient(GrpcTableRpc.useTransport(transport)).build()) {
            Session session = tableClient.getOrCreateSession()
                .join()
                .expect("cannot create session");

            prevSessionId = session.getId();

            session.dropTable(tablePath)
                .join();

            {
                TableDescription tableDescription = TableDescription.newBuilder()
                    .addNullableColumn("key", PrimitiveType.uint32())
                    .addNullableColumn("value", PrimitiveType.string())
                    .setPrimaryKey("key")
                    .build();

                session.createTable(tablePath, tableDescription)
                    .join()
                    .expect("cannot create table");
            }

            Transaction transaction = session.beginTransaction(TransactionMode.SERIALIZABLE_READ_WRITE)
                .join()
                .expect("cannot create transaction");

            {
                String query = "UPSERT INTO [" + tablePath + "] (key, value) VALUES (1, 'one');";
                DataQueryResult result = session.executeDataQuery(query, TxControl.id(transaction))
                    .join()
                    .expect("query failed");
                System.out.println("--[insert1]-------------------");
                DataQueryResults.print(result);
                System.out.println("------------------------------");
            }

            {
                String query = "UPSERT INTO [" + tablePath + "] (key, value) VALUES (2, 'two');";
                DataQueryResult result = session.executeDataQuery(query, TxControl.id(transaction))
                    .join()
                    .expect("query failed");
                System.out.println("--[insert2]-------------------");
                DataQueryResults.print(result);
                System.out.println("------------------------------");
            }

            {
                String query = "SELECT * FROM [" + tablePath + "];";
                DataQueryResult result = session.executeDataQuery(query, TxControl.onlineRo().setCommitTx(true))
                    .join()
                    .expect("query failed");
                System.out.println("--[before commit]-------------");
                DataQueryResults.print(result);
                System.out.println("------------------------------");
            }

            transaction.commit()
                .join()
                .expect("cannot commit transaction");

            {
                String query = "SELECT * FROM [" + tablePath + "];";
                DataQueryResult result = session.executeDataQuery(query, TxControl.onlineRo().setCommitTx(true))
                    .join()
                    .expect("query failed");
                System.out.println("--[after commit]-------------");
                DataQueryResults.print(result);
                System.out.println("------------------------------");
            }

            tableClient.releaseSession(session)
                .join()
                .expect("cannot release session");

            Session session2 = tableClient.getOrCreateSession()
                .join()
                .expect("cannot get or create session");

            if (!prevSessionId.equals(session2.getId())) {
                throw new IllegalStateException("get non pooled session");
            }
        }
    }

    public static void main(String[] args) {
        new ComplexTransaction().doMain();
    }
}
