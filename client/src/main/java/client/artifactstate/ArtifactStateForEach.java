package client.artifactstate;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import client.artifactstate.ArtifactStateProto.ArtifactAndUser;
import client.artifactstate.ArtifactStateProto.CommandResponse;
import io.grpc.StatusRuntimeException;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

// tag::clientForEach[]
class ArtifactStateForEach {

    public <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> futuresList) {
        CompletableFuture<Void> allFuturesResult =
                CompletableFuture.allOf(futuresList.toArray(new CompletableFuture[0]));
        return allFuturesResult.thenApply(v ->
                futuresList.stream().
                        map(CompletableFuture::join).
                        collect(Collectors.<T>toList())
        );
    }

    CompletableFuture<CommandResponse> singleRequestReply(ArtifactStateServiceClient client, ArtifactAndUser data, int cnt, String command) {
        System.out.printf("transmitting data for (%d) for UserId:%s ArtifactID:%d\n", cnt, data.getUserId(), data.getArtifactId());
        CompletionStage<CommandResponse> reply = null;
        switch(command) {
            case "SetArtifactReadByUser":
                reply = client.setArtifactReadByUser(data);
            case "SetArtifactAddedToUserFeed":
                reply = client.setArtifactAddedToUserFeed(data);
            case "SetArtifactRemovedFromUserFeed":
                reply = client.setArtifactRemovedFromUserFeed(data);
        }
        if (reply != null) {
            reply.thenAccept(msg -> System.out.printf("received response for (%d): Success %b\n", cnt, msg.getSuccess()))
                    .exceptionally(ex -> {
                        System.out.printf("Something went wrong, Error (%d): %s%n\n", cnt, ex.getMessage());
                        return null;
                    });
        }
        return (CompletableFuture<CommandResponse>) reply;
    }

    void doWork(String[] args) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("SetArtifactReadByUser");
        commands.add("SetArtifactAddedToUserFeed");
        commands.add("SetArtifactRemovedFromUserFeed");

        ArrayList<String> lastNames = new ArrayList<>();

        try {
            File myObj = new File("./test-data/lastnames.csv");
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                lastNames.add(data);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

        Random random = new Random();

        ActorSystem system = ActorSystem.create("ArtifactAndUserClient");
        GrpcClientSettings settings = GrpcClientSettings.fromConfig("client.ArtifactStateService", system);
        ArtifactStateServiceClient client = null;
        List<CompletableFuture<CommandResponse>> replies = new ArrayList<>();

        try {
            client = ArtifactStateServiceClient.create(settings, system);

            for (int i = 0; i < 1000; i++) {
                String command = commands.get(random.nextInt(2));
                String userId = lastNames.get(random.nextInt(1000));
                int artifactId = random.nextInt(100);
                ArtifactAndUser.Builder artifactAndUserBuilder = ArtifactAndUser.newBuilder();
                artifactAndUserBuilder.setUserId(userId);
                artifactAndUserBuilder.setArtifactId(artifactId);
                ArtifactAndUser artifactAndUser = artifactAndUserBuilder.build();
                replies.add(singleRequestReply(client, artifactAndUser, i, command));
            }
            System.out.printf("requests sent %d%n", replies.size());

        } catch (StatusRuntimeException e) {
            System.out.println("Status: " + e.getStatus());
        } catch (Exception e)  {
            e.printStackTrace();
        } finally {
            allOf(replies).join();
            if (client != null) client.close();
            System.out.println("finally done.");
            system.terminate();
        }
    }

    public static void main(String[] args) {
        new ArtifactStateForEach().doWork(args);
    }

}
// end::clientForEach[]