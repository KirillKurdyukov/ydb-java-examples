package tech.ydb.examples.simple;

import java.util.UUID;

import tech.ydb.core.rpc.RpcTransport;
import tech.ydb.table.SchemeClient;
import tech.ydb.table.description.DescribePathResult;
import tech.ydb.table.description.ListDirectoryResult;
import tech.ydb.table.rpc.grpc.GrpcSchemeRpc;


/**
 * @author Sergey Polovko
 */
public class Scheme extends SimpleExample {

    @Override
    void run(RpcTransport transport, String pathPrefix) {
        String directoryPath = pathPrefix + UUID.randomUUID().toString();
        SchemeClient schemeClient = SchemeClient.newClient(GrpcSchemeRpc.useTransport(transport)).build();

        schemeClient.makeDirectory(directoryPath)
            .join()
            .expect("cannot make directory: " + directoryPath);

        for (int i = 0; i < 3; i++) {
            String subDirectory = directoryPath + '/' + i;
            schemeClient.makeDirectory(subDirectory)
                .join()
                .expect("cannot make directory: " + subDirectory);
        }

        {
            DescribePathResult result = schemeClient.describePath(directoryPath)
                .join()
                .expect("cannot describe path: " + directoryPath);

            System.out.println("--[self]---------------------------");
            System.out.println(result.getSelf());
            System.out.println("-----------------------------------");
        }

        {
            ListDirectoryResult result = schemeClient.listDirectory(directoryPath)
                .join()
                .expect("cannot describe directory: " + directoryPath);

            System.out.println("--[self]---------------------------");
            System.out.println(result.getSelf());
            System.out.println("--[children]-----------------------");
            System.out.println(result.getChildren());
            System.out.println("-----------------------------------");
        }

        for (int i = 0; i < 3; i++) {
            String subDirectory = directoryPath + '/' + i;
            schemeClient.removeDirectory(subDirectory)
                .join()
                .expect("cannot remove directory: " + subDirectory);
        }

        schemeClient.removeDirectory(directoryPath)
            .join()
            .expect("cannot remove directory: " + directoryPath);

    }

    public static void main(String[] args) {
        new Scheme().doMain();
    }
}
