# PSQL Cheats

## Connect to Host

```
psql -h localhost -U postgres
```

## List Tables
```
\dt
```

## Create a Postgres Schema (namespaces)
```
CREATE SCHEMA akka;
```

## List Schemas (namespaces)
```
\dn
```

## Which schema is the default?

To see which schema is currently the default, run the following.
```
SHOW search_path;
```

## Use Schema (namespace)
To set akka as the default schema in this session, do the following.
```
SET search_path=akka;
```

## Creating the Akka Persistence Schema [here](https://doc.akka.io/docs/akka-persistence-r2dbc/current/getting-started.html#creating-the-schema).

