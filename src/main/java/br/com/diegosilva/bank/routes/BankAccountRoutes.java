package br.com.diegosilva.bank.routes;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.serialization.jackson.JacksonObjectMapperProvider;
import br.com.diegosilva.bank.actors.account.BankAccount;
import br.com.diegosilva.bank.actors.account.BankAccountState;
import br.com.diegosilva.bank.utils.UUIDGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

public class BankAccountRoutes {

    public static class CreateAccount {
        public final String uid;
        public final String name;
        public final BigDecimal startAmmount;

        public CreateAccount(String uid, String name, BigDecimal startAmmount) {
            this.uid = uid;
            this.name = name;
            this.startAmmount = startAmmount;
        }
    }


    private final ActorSystem<?> system;
    private final ClusterSharding sharding;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public BankAccountRoutes(ActorSystem<?> system) {
        this.system = system;
        sharding = ClusterSharding.get(system);
        timeout = system.settings().config().getDuration("bank.askTimeout");
        // Use Jackson ObjectMapper from Akka Jackson serialization
        objectMapper = JacksonObjectMapperProvider.get(Adapter.toClassic(system))
                .getOrCreate("jackson-json", Optional.empty());
    }


    public Route bank() {
        return pathPrefix("bank", () ->
                pathPrefix("account", () ->
                        concat(
                                post(() ->
                                        entity(
                                                Jackson.unmarshaller(objectMapper, CreateAccount.class),
                                                data -> onConfirmationReply(createAccount(data))))
                        )
                )
        );
    }

    private Route onConfirmationReply(CompletionStage<BankAccount.Confirmation> reply) {
        return onSuccess(reply, confirmation -> {
            if (confirmation instanceof BankAccount.Accepted)
                return complete(StatusCodes.OK, ((BankAccount.Accepted) confirmation).summary, Jackson.marshaller(objectMapper));
            else
                return complete(StatusCodes.BAD_REQUEST, ((BankAccount.Rejected) confirmation).reason);
        });
    }

    private CompletionStage<BankAccount.Confirmation> createAccount(CreateAccount data) {
        String accountNumber = UUID.randomUUID().toString();
        EntityRef<BankAccount.Command> entityRef =
                sharding.entityRefFor(BankAccount.ENTITY_TYPE_KEY, accountNumber);
        return entityRef.ask(replyTo -> new BankAccount.CreateAccount(accountNumber, data.name, data.uid,
                new BankAccountState.Transaction(UUIDGenerator.generateType4UUID().toString(), new Date().getTime(),
                        data.startAmmount, data.uid, data.uid, "D"), replyTo), timeout);
    }
}
