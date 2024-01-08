package lgfs.gfs.chunk

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import lgfs.gfs.FileProtocol
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.Socket

class ChunkServiceActor(context: ActorContext<FileProtocol>) : AbstractBehavior<FileProtocol>(context) {
	private val chunkService = ChunkService()
	private val logger: Logger = LoggerFactory.getLogger(this::class.java)

	class HandleIncomingTCPConnection(val socket: Socket) : FileProtocol
	class PayloadData(val mutationId: ByteArray, val payload: ByteArray) : FileProtocol

	companion object {
		fun create(): Behavior<FileProtocol> {
			return Behaviors.setup {
				ChunkServiceActor(it);
			}
		}
	}

	init {
		val server = TCPConnectionHandler(context.self)
		server.startDataServer()
	}

	override fun createReceive(): Receive<FileProtocol> {
		return newReceiveBuilder()
			.onMessage(FileProtocol.Mutations::class.java, this::onMutations)
			.onMessage(FileProtocol.CommitMutation::class.java, this::onCommitMutation)
			.onMessage(FileProtocol.LeaseGrantRes::class.java, this::onLeaseGrant)
			.onMessage(PayloadData::class.java, this::onPayloadData)
			.build()
	}

	private fun onMutations(msg: FileProtocol.Mutations): Behavior<FileProtocol> {
		logger.info("req id: {}, Received mutations from client with id: {}", msg.reqId, msg.mutations[0].clientId)
		chunkService.addMutations(msg.mutations)
		return Behaviors.same()
	}

	private fun onCommitMutation(msg: FileProtocol.CommitMutation): Behavior<FileProtocol> {
		chunkService.commitMutation(msg.clientId, msg.chunkHandle, msg.replicas)
		return Behaviors.same()
	}


	private fun onPayloadData(msg: PayloadData): Behavior<FileProtocol> {
		logger.info("Received payload data, payload id: {}", String(msg.mutationId))
		chunkService.handlePayloadData(String(msg.mutationId), msg.payload)
		return Behaviors.same()
	}

	private fun onLeaseGrant(msg: FileProtocol.LeaseGrantRes): Behavior<FileProtocol> {
		logger.info("req id: {}, Processing lease grant", msg.reqId)
		chunkService.handleLeaseGrant(msg.leases)
		return Behaviors.same()
	}
}