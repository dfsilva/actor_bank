package br.com.diegosilva.bank.actors;

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
import java.time.Instant;
import java.util.*;


public class BankAccount
  extends EventSourcedBehaviorWithEnforcedReplies<BankAccount.Command, BankAccount.Event, BankAccountState> {

  /**
   * This interface defines all the commands that the ShoppingCart persistent actor supports.
   */
  public interface Command extends CborSerializable {
  }

  /**
   * A command to add an item to the cart.
   *
   * It can reply with `Confirmation`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  public static class AddItem implements Command {
    public final String itemId;
    public final int quantity;
    public final ActorRef<Confirmation> replyTo;

    public AddItem(String itemId, int quantity, ActorRef<Confirmation> replyTo) {
      this.itemId = itemId;
      this.quantity = quantity;
      this.replyTo = replyTo;
    }
  }

  /**
   * A command to remove an item from the cart.
   */
  public static class RemoveItem implements Command {
    public final String itemId;
    public final ActorRef<Confirmation> replyTo;

    @JsonCreator
    public RemoveItem(String itemId, ActorRef<Confirmation> replyTo) {
      this.itemId = itemId;
      this.replyTo = replyTo;
    }
  }


  /**
   * A command to checkout the shopping cart.
   *
   * The reply type is the {@link Confirmation}, which will be returned when the events have been
   * emitted.
   */
  public static class Checkout implements Command {
    public final ActorRef<Confirmation> replyTo;

    @JsonCreator
    public Checkout(ActorRef<Confirmation> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /**
   * Summary of the shopping cart state, used in reply messages.
   */
  public static final class Summary implements CborSerializable {
    public final Map<String, Integer> items;
    public final boolean checkedOut;


    public Summary(Map<String, Integer> items, boolean checkedOut) {
      // Summary is included in messages and should therefore be immutable
      this.items = Collections.unmodifiableMap(new HashMap<>(items));
      this.checkedOut = checkedOut;
    }
  }

  public interface Confirmation extends CborSerializable {}

  public static class Accepted implements Confirmation {
    public final Summary summary;

    @JsonCreator
    public Accepted(Summary summary) {
      this.summary = summary;
    }
  }

  public static class Rejected implements Confirmation {
    public final String reason;

    public Rejected(String reason) {
      this.reason = reason;
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

  public static final class ItemRemoved implements Event {
    public final String cartId;
    public final String itemId;

    public ItemRemoved(String cartId, String itemId) {
      this.cartId = cartId;
      this.itemId = itemId;
    }

    @Override
    public String toString() {
      return "ItemRemoved(" + cartId + "," + itemId + ")";
    }
  }

  public static final class ItemQuantityAdjusted implements Event {
    public final String cartId;
    public final String itemId;
    public final int quantity;

    public ItemQuantityAdjusted(String cartId, String itemId, int quantity) {
      this.cartId = cartId;
      this.itemId = itemId;
      this.quantity = quantity;
    }

    @Override
    public String toString() {
      return "ItemQuantityAdjusted(" + cartId + "," + itemId + "," + quantity + ")";
    }
  }

  public static class CheckedOut implements Event {

    public final String cartId;
    public final Instant eventTime;

    public CheckedOut(String cartId, Instant eventTime) {
      this.cartId = cartId;
      this.eventTime = eventTime;
    }

    @Override
    public String toString() {
      return "CheckedOut(" + cartId + "," + eventTime + ")";
    }
  }

  public static EntityTypeKey<Command> ENTITY_TYPE_KEY =
    EntityTypeKey.create(Command.class, "ShoppingCart");

  public static void init(ActorSystem<?> system, EventProcessorSettings eventProcessorSettings) {
    ClusterSharding.get(system).init(Entity.of(ENTITY_TYPE_KEY, entityContext -> {
        int n = Math.abs(entityContext.getEntityId().hashCode() % eventProcessorSettings.parallelism);
        String eventProcessorTag = eventProcessorSettings.tagPrefix + "-" + n;
        return BankAccount.create(entityContext.getEntityId(), Collections.singleton(eventProcessorTag));
      })
      .withRole("write-model"));
  }

  public static Behavior<Command> create(String cartId, Set<String> eventProcessorTags) {
    return new BankAccount(cartId, eventProcessorTags);
  }

  private final String cartId;
  private final Set<String> eventProcessorTags;

  private BankAccount(String cartId, Set<String> eventProcessorTags) {
    super(PersistenceId.of(ENTITY_TYPE_KEY.name(), cartId),
      SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(5), 0.1));
    this.cartId = cartId;
    this.eventProcessorTags = eventProcessorTags;
  }

  @Override
  public State emptyState() {
    return new State();
  }

  private final CheckedOutCommandHandlers checkedOutCommandHandlers = new CheckedOutCommandHandlers();
  private final OpenShoppingCartCommandHandlers openShoppingCartCommandHandlers = new OpenShoppingCartCommandHandlers();

  @Override
  public CommandHandlerWithReply<Command, Event, State> commandHandler() {
    CommandHandlerWithReplyBuilder<Command, Event, State> b =
      newCommandHandlerWithReplyBuilder();

    b.forState(state -> !state.isCheckedOut())
      .onCommand(AddItem.class, openShoppingCartCommandHandlers::onAddItem)
      .onCommand(RemoveItem.class, openShoppingCartCommandHandlers::onRemoveItem)
      .onCommand(AdjustItemQuantity.class, openShoppingCartCommandHandlers::onAdjustItemQuantity)
      .onCommand(Checkout.class, openShoppingCartCommandHandlers::onCheckout);

    b.forState(state -> state.isCheckedOut())
      .onCommand(AddItem.class, checkedOutCommandHandlers::onAddItem)
      .onCommand(RemoveItem.class, checkedOutCommandHandlers::onRemoveItem)
      .onCommand(AdjustItemQuantity.class, checkedOutCommandHandlers::onAdjustItemQuantity)
      .onCommand(Checkout.class, checkedOutCommandHandlers::onCheckout);

    b.forAnyState()
      .onCommand(Get.class, this::onGet);

    return b.build();
  }

  private ReplyEffect<Event, State> onGet(State state, Get cmd) {
    return Effect().reply(cmd.replyTo, state.toSummary());
  }

  private class OpenShoppingCartCommandHandlers {

    public ReplyEffect<Event, State> onAddItem(State state, AddItem cmd) {
      if (state.hasItem(cmd.itemId)) {
        return Effect().reply(cmd.replyTo, new Rejected(
          "Item '" + cmd.itemId + "' was already added to this shopping cart"));
      } else if (cmd.quantity <= 0) {
        return Effect().reply(cmd.replyTo, new Rejected("Quantity must be greater than zero"));
      } else {
        return Effect().persist(new ItemAdded(cartId, cmd.itemId, cmd.quantity))
          .thenReply(cmd.replyTo, updatedCart -> new Accepted(updatedCart.toSummary()));
      }
    }

    public ReplyEffect<Event, State> onRemoveItem(State state, RemoveItem cmd) {
      if (state.hasItem(cmd.itemId)) {
        return Effect().persist(new ItemRemoved(cartId, cmd.itemId))
          .thenReply(cmd.replyTo, updatedCart -> new Accepted(updatedCart.toSummary()));
      } else {
        return Effect().reply(cmd.replyTo, new Accepted(state.toSummary()));
      }
    }

    public ReplyEffect<Event, State> onAdjustItemQuantity(State state, AdjustItemQuantity cmd) {
      if (cmd.quantity <= 0) {
        return Effect().reply(cmd.replyTo, new Rejected("Quantity must be greater than zero"));
      } else if (state.hasItem(cmd.itemId)) {
        return Effect().persist(new ItemQuantityAdjusted(cartId, cmd.itemId, cmd.quantity))
          .thenReply(cmd.replyTo, updatedCart -> new Accepted(updatedCart.toSummary()));
      } else {
        return Effect().reply(cmd.replyTo, new Rejected(
          "Cannot adjust quantity for item '" + cmd.itemId + "'. Item not present on cart"));
      }
    }

    public ReplyEffect<Event, State> onCheckout(State state, Checkout cmd) {
      if (state.isEmpty()) {
        return Effect().reply(cmd.replyTo, new Rejected("Cannot checkout an empty shopping cart"));
      } else {
        return Effect().persist(new CheckedOut(cartId, Instant.now()))
          .thenReply(cmd.replyTo, updatedCart -> new Accepted(updatedCart.toSummary()));
      }
    }
  }

  private class CheckedOutCommandHandlers {
    ReplyEffect<Event, State> onAddItem(AddItem cmd) {
      return Effect().reply(cmd.replyTo, new Rejected("Can't add an item to an already checked out shopping cart"));
    }

    ReplyEffect<Event, State> onRemoveItem(RemoveItem cmd) {
      return Effect().reply(cmd.replyTo, new Rejected("Can't remove an item from an already checked out shopping cart"));
    }

    ReplyEffect<Event, State> onAdjustItemQuantity(AdjustItemQuantity cmd) {
      return Effect().reply(cmd.replyTo, new Rejected("Can't adjust item on an already checked out shopping cart"));
    }

    ReplyEffect<Event, State> onCheckout(Checkout cmd) {
      return Effect().reply(cmd.replyTo, new Rejected("Can't checkout already checked out shopping cart"));
    }
  }

  @Override
  public EventHandler<State, Event> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
      .onEvent(ItemAdded.class, (state, event) -> state.updateItem(event.itemId, event.quantity))
      .onEvent(ItemRemoved.class, (state, event) -> state.removeItem(event.itemId))
      .onEvent(ItemQuantityAdjusted.class, (state, event) -> state.updateItem(event.itemId, event.quantity))
      .onEvent(CheckedOut.class, (state, event) -> state.checkout(event.eventTime))
      .build();
  }

  @Override
  public Set<String> tagsFor(Event event) {
    return eventProcessorTags;
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    // enable snapshotting
    return RetentionCriteria.snapshotEvery(100, 3);
  }
}
