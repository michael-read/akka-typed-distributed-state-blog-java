package client.artifactstate;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Source;
import client.artifactstate.ArtifactStateProto.ArtifactCommand;
import client.artifactstate.ArtifactStateProto.StreamedResponse;
import io.grpc.StatusRuntimeException;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// tag::clientStream[]
class ArtifactStateStream {

    void doWork(String[] args) {
        ActorSystem system = ActorSystem.create("ArtifactAndUserClient");
        Materializer materializer = SystemMaterializer.get(system).materializer();
        GrpcClientSettings settings = GrpcClientSettings.fromConfig("client.ArtifactStateService", system);
        ArtifactStateServiceClient client = null;

        try {
            client = ArtifactStateServiceClient.create(settings, system);

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

            AtomicInteger sent = new AtomicInteger();
            Source<ArtifactCommand, NotUsed> requestStream =
                Source.fromIterator(lastNames::iterator)
                      .map(name -> {
                        String command = commands.get(random.nextInt(2));

                        int artifactId = random.nextInt(100);
                        ArtifactCommand.Builder artifactCommand = ArtifactCommand.newBuilder();
                        artifactCommand.setUserId(name);
                        artifactCommand.setArtifactId(artifactId);
                        artifactCommand.setCommand(command);
                        ArtifactCommand data = artifactCommand.build();
                        sent.getAndIncrement();
                        System.out.printf("transmitting data for user Id %d for UserId:%s ArtifactID:%d", sent.get(), data.getUserId(), data.getArtifactId());
                        return data;
                    });

            Source<StreamedResponse, NotUsed> responseStream = client.commandsStreamed(requestStream);

            AtomicInteger received = new AtomicInteger();
            CompletionStage<Done> done =
                    responseStream.runForeach((reply) -> {
                        received.getAndIncrement();
                        System.out.printf("received response for (%d): Success %b", received.get(), reply.getSuccess());
                    }, materializer);

            done.toCompletableFuture().get(60, TimeUnit.SECONDS);
            System.out.printf("requests sent %d, replies received %d%n", sent.get(), received.get());

        } catch (StatusRuntimeException e) {
            System.out.println("Status: " + e.getStatus());
        } catch (Exception e)  {
            e.printStackTrace();
        } finally {
            if (client != null) client.close();
            System.out.println("finally done.");
            system.terminate();
        }
    }

    public static void main(String[] args) {
        new ArtifactStateStream().doWork(args);
    }

}
// end::clientStream[]