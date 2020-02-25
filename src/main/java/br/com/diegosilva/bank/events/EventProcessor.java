package br.com.diegosilva.bank.events;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.stream.KillSwitches;
import akka.stream.SharedKillSwitch;
import br.com.diegosilva.bank.CborSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General purpose event processor infrastructure. Not specific to the ShoppingCart domain.
 */
public class EventProcessor {
    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);


    public enum Ping implements CborSerializable {
        INSTANCE
    }

    public static EntityTypeKey<Ping> entityKey(String eventProcessorId) {
        return EntityTypeKey.create(Ping.class, eventProcessorId);
    }

    public static <Event> void init(
            ActorSystem<?> system,
            EventProcessorSettings settings) {

        EntityTypeKey<Ping> eventProcessorEntityKey = entityKey(settings.id);

        ClusterSharding.get(system)
                .init(Entity.of(eventProcessorEntityKey, entityContext ->
                EventProcessor.create())
                .withRole("read-model"));

        KeepAlive.init(system, eventProcessorEntityKey);
    }

    public static Behavior<Ping> create() {

        return Behaviors.setup(context -> {
            SharedKillSwitch killSwitch = KillSwitches.shared("eventProcessorSwitch");

//            eventProcessorStream.runQueryStream(killSwitch);

            log.info("-----------Evento processado----------------");

            return Behaviors.receive(Ping.class)
                    .onMessage(Ping.class, msg -> {
                        log.info("----------- Recebu Ping -------------");
                        return Behaviors.same();
                    })
                    .onSignal(PostStop.class, sig -> {
                        killSwitch.shutdown();
                        return Behaviors.same();
                    })
                    .build();
        });
    }


}
