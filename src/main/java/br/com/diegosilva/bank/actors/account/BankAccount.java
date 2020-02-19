package br.com.diegosilva.bank.actors.account;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import br.com.diegosilva.bank.CborSerializable;
import br.com.diegosilva.bank.domain.TransactionType;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Duration;


public class BankAccount
        extends EventSourcedBehaviorWithEnforcedReplies<BankAccount.Command, BankAccount.Event, BankAccountState> {

    public interface Command extends CborSerializable {
    }

    public interface Confirmation extends CborSerializable {
    }

    public static class Accepted implements Confirmation {
        public final Object summary;

        @JsonCreator
        public Accepted(Object summary) {
            this.summary = summary;
        }
    }

    public static class Rejected implements Confirmation {
        public final String reason;

        @JsonCreator
        public Rejected(String reason) {
            this.reason = reason;
        }
    }


    public static class CreateAccount implements Command {
        public final String number;
        public final String name;
        public final String uid;
        public final BankAccountState.Transaction transaction;
        public final ActorRef<Confirmation> replyTo;

        @JsonCreator
        public CreateAccount(String number,
                             String name,
                             String uid,
                             BankAccountState.Transaction transaction,
                             ActorRef<Confirmation> replyTo) {
            this.number = number;
            this.name = name;
            this.uid = uid;
            this.transaction = transaction;
            this.replyTo = replyTo;
        }
    }

    public static class AddTransaction implements Command {
        public final BankAccountState.Transaction transaction;
        public final ActorRef<Confirmation> replyTo;

        @JsonCreator
        public AddTransaction(BankAccountState.Transaction transaction, ActorRef<Confirmation> replyTo) {
            this.transaction = transaction;
            this.replyTo = replyTo;
        }
    }

    public static class Get implements Command {
        public final ActorRef<BankAccountState> replyTo;

        @JsonCreator
        public Get(ActorRef<BankAccountState> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public interface Event extends CborSerializable {
    }

    public static final class AccountCreated implements Event {

        public final String number;
        public final String name;
        public final String uid;
        public final BankAccountState.Transaction transaction;


        public AccountCreated(String number, String name, String uid, BankAccountState.Transaction transaction) {
            this.number = number;
            this.name = name;
            this.uid = uid;
            this.transaction = transaction;
        }
    }

    public static class TransactionAdded implements Event {
        public final BankAccountState.Transaction transaction;

        @JsonCreator
        public TransactionAdded(BankAccountState.Transaction transaction) {
            this.transaction = transaction;
        }
    }

    public static EntityTypeKey<Command> ENTITY_TYPE_KEY =
            EntityTypeKey.create(Command.class, "BankAccount");

    public static void init(ActorSystem<?> system) {
        ClusterSharding.get(system).init(Entity.of(ENTITY_TYPE_KEY, entityContext
                -> BankAccount.create(entityContext.getEntityId()))
                .withRole("write-model"));
    }

    public static Behavior<Command> create(String cartId) {
        return new BankAccount(cartId);
    }

    private final String accountId;

    private BankAccount(String accountId) {
        super(PersistenceId.of(ENTITY_TYPE_KEY.name(), accountId),
                SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(5), 0.1));
        this.accountId = accountId;
    }

    @Override
    public BankAccountState emptyState() {
        return new BankAccountState();
    }

    private final AccountCommandHandlers accountCommandHandlers = new AccountCommandHandlers();

    @Override
    public CommandHandlerWithReply<Command, Event, BankAccountState> commandHandler() {
        CommandHandlerWithReplyBuilder<Command, Event, BankAccountState> b =
                newCommandHandlerWithReplyBuilder();

        b.forAnyState()
                .onCommand(CreateAccount.class, accountCommandHandlers::createAccount)
                .onCommand(AddTransaction.class, accountCommandHandlers::addTransaction)
                .onCommand(Get.class, this::onGet);
        ;

        return b.build();
    }


    private ReplyEffect<Event, BankAccountState> onGet(BankAccountState state, Get cmd) {
        return Effect().reply(cmd.replyTo, state);
    }

    private class AccountCommandHandlers {
        public ReplyEffect<Event, BankAccountState> createAccount(BankAccountState state, CreateAccount cmd) {
            if (state.isCreated()) {
                return Effect().reply(cmd.replyTo, new Rejected(
                        "This account is aready created"));
            }
            return Effect().persist(new AccountCreated(accountId, cmd.name, cmd.uid, cmd.transaction))
                    .thenReply(cmd.replyTo, updated -> new Accepted(updated));
        }

        public ReplyEffect<Event, BankAccountState> addTransaction(BankAccountState state, AddTransaction cmd) {
            if (!state.isCreated()) {
                return Effect().reply(cmd.replyTo, new Rejected(
                        "This account is not valid."));
            }

            if (cmd.transaction.type == TransactionType.D) {
                if (!state.hasMoney(cmd.transaction.amount)) {
                    return Effect().reply(cmd.replyTo, new Rejected(
                            "You have not enough money to do this."));
                }
            }
            return Effect().persist(new TransactionAdded(cmd.transaction))
                    .thenReply(cmd.replyTo, updated -> new Accepted(updated));
        }
    }


    @Override
    public EventHandler<BankAccountState, Event> eventHandler() {
        return newEventHandlerBuilder().forAnyState()
                .onEvent(AccountCreated.class, (state, event) -> state.createAccount(event.number, event.name, event.uid, event.transaction))
                .onEvent(TransactionAdded.class, (state, event) -> state.processTransaction(event.transaction))
                .build();
    }


    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(100, 3);
    }
}
