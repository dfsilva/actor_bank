package br.com.diegosilva.bank.routes;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import akka.serialization.jackson.JacksonObjectMapperProvider;
import br.com.diegosilva.bank.actors.account.BankAccount;
import br.com.diegosilva.bank.actors.account.BankAccountState;
import br.com.diegosilva.bank.domain.TransactionType;
import br.com.diegosilva.bank.routes.dto.CreateAccount;
import br.com.diegosilva.bank.routes.dto.MakeTransaction;
import br.com.diegosilva.bank.utils.UUIDGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

public class BankAccountRoutes {

    private final ActorSystem<?> system;
    private final ClusterSharding sharding;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public BankAccountRoutes(ActorSystem<?> system) {
        this.system = system;
        sharding = ClusterSharding.get(system);
        timeout = system.settings().config().getDuration("bank.askTimeout");
        objectMapper = JacksonObjectMapperProvider.get(Adapter.toClassic(system))
                .getOrCreate("jackson-json", Optional.empty());
    }


    public Route bank() {
        return pathPrefix("bank", () ->
                concat(
                        pathPrefix("account", () ->
                                concat(
                                        post(() ->
                                                entity(
                                                        Jackson.unmarshaller(objectMapper, CreateAccount.class),
                                                        data -> onConfirmationReply(createAccount(data)))),
                                        pathPrefix(PathMatchers.segment(), (String accountId) ->
                                                concat(
                                                        get(() -> onSuccess(getAccount(accountId), accountState -> {
                                                            if (accountState == null)
                                                                return complete(StatusCodes.NOT_FOUND);
                                                            else
                                                                return complete(StatusCodes.OK, accountState, Jackson.marshaller(objectMapper));
                                                        }))
                                                )
                                        )
                                )
                        ),
                        pathPrefix("transaction", () ->
                                concat(
                                        post(() ->
                                                entity(
                                                        Jackson.unmarshaller(objectMapper, MakeTransaction.class),
                                                        data -> onConfirmationReply(makeTransaction(data))))
                                )
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
        EntityRef<BankAccount.Command> entityRef =
                sharding.entityRefFor(BankAccount.ENTITY_TYPE_KEY, data.number);
        return entityRef.ask(replyTo -> new BankAccount.CreateAccount(data.number, data.name, data.uid,
                new BankAccountState.Transaction(UUIDGenerator.generateType4UUID().toString(), new Date().getTime(),
                        data.ammount, data.uid, data.uid, TransactionType.C), replyTo), timeout);
    }

    private CompletionStage<BankAccount.Confirmation> makeTransaction(MakeTransaction data) {
        EntityRef<BankAccount.Command> entityRef = sharding.entityRefFor(BankAccount.ENTITY_TYPE_KEY, data.from);
        return entityRef.ask(replyTo -> new BankAccount.AddTransaction(new BankAccountState.Transaction(UUIDGenerator.generateType4UUID().toString(),
                new Date().getTime(),
                data.ammount, data.from, data.to, data.type), replyTo), timeout);
    }

    private CompletionStage<BankAccountState> getAccount(String account) {
        EntityRef<BankAccount.Command> entityRef = sharding.entityRefFor(BankAccount.ENTITY_TYPE_KEY, account);
        return entityRef.ask(BankAccount.Get::new, timeout);
    }
}
