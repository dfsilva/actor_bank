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
import br.com.diegosilva.bank.states.BankAccountState;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Duration;
import java.util.Map;


public class BankAccount
        extends EventSourcedBehaviorWithEnforcedReplies<BankAccount.Command, BankAccount.Event, BankAccountState> {

    public interface Command extends CborSerializable {
    }

    public interface Confirmation extends CborSerializable {
    }

    public static class Accepted implements Confirmation {
        public final Map<String, Object> summary;

        @JsonCreator
        public Accepted(Map<String, Object> summary) {
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
        public final String itemId;
        public final int quantity;
        public final ActorRef<Confirmation> replyTo;

        @JsonCreator
        public AddTransaction(String itemId, int quantity, ActorRef<Confirmation> replyTo) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.replyTo = replyTo;
        }
    }

    public static class Checkout implements Command {
        public final ActorRef<Confirmation> replyTo;

        @JsonCreator
        public Checkout(ActorRef<Confirmation> replyTo) {
            this.replyTo = replyTo;
        }
    }


    public interface Event extends CborSerializable {
    }

    public static final class ItemAdded implements Event {
        public final String cartId;
        public final String itemId;
        public final int quantity;

        public ItemAdded(String cartId, String itemId, int quantity) {
            this.cartId = cartId;
            this.itemId = itemId;
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return "ItemAdded(" + cartId + "," + itemId + "," + quantity + ")";
        }
    }

    public static EntityTypeKey<Command> ENTITY_TYPE_KEY =
            EntityTypeKey.create(Command.class, "BankAccount");

    public static void init(ActorSystem<?> system) {
        ClusterSharding.get(system).init(Entity.of(ENTITY_TYPE_KEY, entityContext -> {
            return BankAccount.create(entityContext.getEntityId());
        })
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

    private final CheckedOutCommandHandlers checkedOutCommandHandlers = new CheckedOutCommandHandlers();
    private final OpenShoppingCartCommandHandlers openShoppingCartCommandHandlers = new OpenShoppingCartCommandHandlers();

    @Override
    public CommandHandlerWithReply<Command, Event, BankAccountState> commandHandler() {
        CommandHandlerWithReplyBuilder<Command, Event, BankAccountState> b =
                newCommandHandlerWithReplyBuilder();

//    b.forState(state -> !state.isCheckedOut())
//      .onCommand(AddItem.class, openShoppingCartCommandHandlers::onAddItem)
//      .onCommand(RemoveItem.class, openShoppingCartCommandHandlers::onRemoveItem)
//      .onCommand(AdjustItemQuantity.class, openShoppingCartCommandHandlers::onAdjustItemQuantity)
//      .onCommand(Checkout.class, openShoppingCartCommandHandlers::onCheckout);
//
//    b.forState(state -> state.isCheckedOut())
//      .onCommand(AddItem.class, checkedOutCommandHandlers::onAddItem)
//      .onCommand(RemoveItem.class, checkedOutCommandHandlers::onRemoveItem)
//      .onCommand(AdjustItemQuantity.class, checkedOutCommandHandlers::onAdjustItemQuantity)
//      .onCommand(Checkout.class, checkedOutCommandHandlers::onCheckout);
//
//    b.forAnyState()
//      .onCommand(Get.class, this::onGet);

        return b.build();
    }


    private class OpenShoppingCartCommandHandlers {

//    public ReplyEffect<Event, State> onAddItem(State state, AddItem cmd) {
//      if (state.hasItem(cmd.itemId)) {
//        return Effect().reply(cmd.replyTo, new Rejected(
//          "Item '" + cmd.itemId + "' was already added to this shopping cart"));
//      } else if (cmd.quantity <= 0) {
//        return Effect().reply(cmd.replyTo, new Rejected("Quantity must be greater than zero"));
//      } else {
//        return Effect().persist(new ItemAdded(accountId, cmd.itemId, cmd.quantity))
//          .thenReply(cmd.replyTo, updatedCart -> new Accepted(updatedCart.toSummary()));
//      }
//    }

//    public ReplyEffect<Event, State> onRemoveItem(State state, RemoveItem cmd) {
//      if (state.hasItem(cmd.itemId)) {
//        return Effect().persist(new ItemRemoved(accountId, cmd.itemId))
//          .thenReply(cmd.replyTo, updatedCart -> new Accepted(updatedCart.toSummary()));
//      } else {
//        return Effect().reply(cmd.replyTo, new Accepted(state.toSummary()));
//      }
//    }
//
//    public ReplyEffect<Event, State> onAdjustItemQuantity(State state, AdjustItemQuantity cmd) {
//      if (cmd.quantity <= 0) {
//        return Effect().reply(cmd.replyTo, new Rejected("Quantity must be greater than zero"));
//      } else if (state.hasItem(cmd.itemId)) {
//        return Effect().persist(new ItemQuantityAdjusted(accountId, cmd.itemId, cmd.quantity))
//          .thenReply(cmd.replyTo, updatedCart -> new Accepted(updatedCart.toSummary()));
//      } else {
//        return Effect().reply(cmd.replyTo, new Rejected(
//          "Cannot adjust quantity for item '" + cmd.itemId + "'. Item not present on cart"));
//      }
//    }
//
//    public ReplyEffect<Event, State> onCheckout(State state, Checkout cmd) {
//      if (state.isEmpty()) {
//        return Effect().reply(cmd.replyTo, new Rejected("Cannot checkout an empty shopping cart"));
//      } else {
//        return Effect().persist(new CheckedOut(accountId, Instant.now()))
//          .thenReply(cmd.replyTo, updatedCart -> new Accepted(updatedCart.toSummary()));
//      }
//    }
    }

    private class CheckedOutCommandHandlers {
//    ReplyEffect<Event, State> onAddItem(AddItem cmd) {
//      return Effect().reply(cmd.replyTo, new Rejected("Can't add an item to an already checked out shopping cart"));
//    }
//
//    ReplyEffect<Event, State> onRemoveItem(RemoveItem cmd) {
//      return Effect().reply(cmd.replyTo, new Rejected("Can't remove an item from an already checked out shopping cart"));
//    }
//
//    ReplyEffect<Event, State> onAdjustItemQuantity(AdjustItemQuantity cmd) {
//      return Effect().reply(cmd.replyTo, new Rejected("Can't adjust item on an already checked out shopping cart"));
//    }
//
//    ReplyEffect<Event, State> onCheckout(Checkout cmd) {
//      return Effect().reply(cmd.replyTo, new Rejected("Can't checkout already checked out shopping cart"));
//    }
    }

    @Override
    public EventHandler<BankAccountState, Event> eventHandler() {
        return newEventHandlerBuilder().forAnyState()
//      .onEvent(ItemAdded.class, (state, event) -> state.updateItem(event.itemId, event.quantity))
//      .onEvent(ItemRemoved.class, (state, event) -> state.removeItem(event.itemId))
//      .onEvent(ItemQuantityAdjusted.class, (state, event) -> state.updateItem(event.itemId, event.quantity))
//      .onEvent(CheckedOut.class, (state, event) -> state.checkout(event.eventTime))
                .build();
    }

//  @Override
//  public Set<String> tagsFor(Event event) {
//    return eventProcessorTags;
//  }


    @Override
    public RetentionCriteria retentionCriteria() {
        // enable snapshotting
        return RetentionCriteria.snapshotEvery(100, 3);
    }
}
