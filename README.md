This project contains a sample illustrating an CQRS design with [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html), [Akka Cluster Singleton](https://doc.akka.io/docs/akka/2.6/typed/cluster-singleton.html), [Akka Persistence](https://doc.akka.io/docs/akka/2.6/typed/persistence.html) and [Akka Persistence Query](https://doc.akka.io/docs/akka/2.6/persistence-query.html).

## Overview

This sample application implements a CQRS-ES design that will side-effect in the read model on selected events persisted to Cassandra by the write model. In this sample, the side-effect is logging a line. A more practical example would be to send a message to a Kafka topic or update a relational database.

## Write model

The write model is a bank.

The implementation is based on a sharded actor: each `actors.account.BankAccount` is an [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html) entity. The entity actor `BankAccount` is an [EventSourcedBehavior](https://doc.akka.io/docs/akka/2.6/typed/persistence.html).

## Read model

The read model is implemented in such a way that 'load' is sharded over a number of processors. This number is `event-processor.parallelism`.

The implementation is resilient: it uses an *Akka Cluster Singleton* in combination with *Akka Cluster Sharding* to always keep the event processors alive.

## Running the sample code

1. Start the infrastructure ambiente running docker-compose:

```
docker-compose
```

2. Start a node that runs the write model:

```
mvn compile -Dakka.cluster.roles.0=write-model exec:java -Dexec.mainClass="br.com.diegosilva.bank.Main" -Dexec.args="2551"
```

3. Start a node that runs the read model:

```
mvn compile -Dakka.cluster.roles.0=read-model exec:java -Dexec.mainClass="br.com.diegosilva.bank.Main" -Dexec.args="2552"
```

4. More write or read nodes can be started by defining roles and port:

```
mvn compile -Dakka.cluster.roles.0=write-model exec:java -Dexec.mainClass="br.com.diegosilva.bank.Main" -Dexec.args="2553"
mvn compile -Dakka.cluster.roles.0=read-model exec:java -Dexec.mainClass="br.com.diegosilva.bank.Main" -Dexec.args="2554"
``` 

Try it with curl:

 ```
 # add item to cart
 curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart1", "itemId":"socks", "quantity":3}' http://127.0.0.1:8051/shopping/carts

 # get cart
 curl http://127.0.0.1:8051/shopping/carts/cart1

 # update quantity of item
 curl -X PUT -H "Content-Type: application/json" -d '{"cartId":"cart1", "itemId":"socks", "quantity":5}' http://127.0.0.1:8051/shopping/carts

 # check out cart
 curl -X POST -H "Content-Type: application/json" -d '{}' http://127.0.0.1:8051/shopping/carts/cart1/checkout
 ```

 or same `curl` commands to port 8052.
