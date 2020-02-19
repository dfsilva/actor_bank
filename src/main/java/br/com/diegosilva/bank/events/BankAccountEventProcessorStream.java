package br.com.diegosilva.bank.events;

import akka.Done;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.eventstream.EventStream;
import akka.persistence.typed.PersistenceId;
import br.com.diegosilva.bank.actors.account.BankAccount;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class BankAccountEventProcessorStream extends EventProcessorStream<BankAccount.Event> {

    public BankAccountEventProcessorStream(ActorSystem<?> system, String eventProcessorId) {
        super(system, eventProcessorId);
    }

    @Override
    protected CompletionStage<Object> processEvent(BankAccount.Event event, PersistenceId persistenceId, long sequenceNr) {
        log.info("EventProcessor() consumed {} from {} with seqNr {}", event, persistenceId, sequenceNr);
        system.eventStream().tell(new EventStream.Publish<>(event));
        return CompletableFuture.completedFuture(Done.getInstance());
    }

}
