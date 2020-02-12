import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import br.com.diegosilva.bank.actors.account.BankAccount;
import br.com.diegosilva.bank.routes.BankAccountRoutes;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import static br.com.diegosilva.bank.BankServer.startHttpServer;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("port number is required to start");
        } else {
            String portString = args[0];
            int port = Integer.parseInt(portString);
            int httpPort = Integer.parseInt("80" + portString.substring(portString.length() - 2));
            startNode(port, httpPort);
        }
    }

    private static void startNode(int port, int httpPort) {
        ActorSystem<Void> system = ActorSystem.create(Guardian.create(), "Bank", config(port, httpPort));
    }

    private static Config config(int port, int httpPort) {
        return ConfigFactory.parseString(
                "akka.remote.artery.canonical.port = " + port + "\n" +
                        "bank.http.port =" + httpPort + "\n")
                .withFallback(ConfigFactory.load());
    }
}

class Guardian {
    static Behavior<Void> create() {
        return Behaviors.setup(context -> {
            ActorSystem<?> system = context.getSystem();
//            EventProcessorSettings settings = EventProcessorSettings.create(system);
            int httpPort = system.settings().config().getInt("bank.http.port");

            BankAccount.init(system);

//            if (Cluster.get(system).selfMember().hasRole("read-model")) {
//                EventProcessor.init(
//                        system,
//                        settings,
//                        tag -> new ShoppingCartEventProcessorStream(system, settings.id, tag));
//            }

            startHttpServer(new BankAccountRoutes(system).shopping(), httpPort, system);
            return Behaviors.empty();
        });
    }
}
