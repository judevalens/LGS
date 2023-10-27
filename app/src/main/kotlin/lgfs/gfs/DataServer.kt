package lgfs.gfs

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.*
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.concurrent.CompletionStage


class DataServer(val context: ActorContext<Command>, val actorSystem: ActorSystem<Any>) :
    AbstractBehavior<DataServer.Command>(context) {
    interface Command
    class IncomingConnection(val socket: Socket) : Command

    init {
        val server = ServerSocket()
        val tcpThread = Thread {
            while (true) {
                val incomingConnection = server.accept();
                val res: CompletionStage<Command> = AskPattern.ask(
                    context.self,
                    {
                        IncomingConnection(incomingConnection)
                    },
                    Duration.ofMinutes(10000),
                    actorSystem.scheduler()
                )
            }
        }
    }

    fun startTCPServer() {
        val socker = ServerSocket()
    }

    private fun onIncomingConnection(msg: IncomingConnection): Behavior<Command> {

        return Behaviors.same()
    }

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(IncomingConnection::class.java, this::onIncomingConnection)
            .build()
    }
}