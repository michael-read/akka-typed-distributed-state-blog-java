# Testing With Yugabyte on Docker

## Start Yugabyte in the background
```
docker-compose -f docker-compose-yugabyte.yml up -d
```

## Create the required tables in Yugabyte DB
1. Connect to Yugabyte from another terminal window.
```
docker exec -it yb-tserver-n1 /home/yugabyte/bin/ysqlsh -h yb-tserver-n1
```
2. Follow Creating the Schema [here](https://doc.akka.io/docs/akka-persistence-r2dbc/current/getting-started.html#creating-the-schema).

## Run the tests MultiNodeIntegrationTest (single cluster) / MultiDCNodeIntegrationTest (multi cluster)

- Run the test with your IDE

or

- Run with Maven
```
mvn test
```
