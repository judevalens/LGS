package lgfs.api.grpc

import Gfs
import MasterServiceGrpcKt
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import lgfs.api.MasterApi
import lgfs.gfs.FileMetadata
import lgfs.gfs.FileProtocol
import org.slf4j.LoggerFactory


class MasterServiceImpl(private val masterGfs: MasterApi) : MasterServiceGrpcKt.MasterServiceCoroutineImplBase() {
    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun createFile(request: Gfs.CreateFileReq): Gfs.CreateFileRes {
        val reqIdElement = currentCoroutineContext()[TagInterceptor.ReqIdKey]
        val reqId = reqIdElement!!.keyStr
        logger.info("reqId: {}, received create file request", reqId)
        val res = masterGfs
            .createFile(reqId, FileMetadata(request.fileName, false, request.fileSize.toLong()))
            .await() as? FileProtocol.CreateFileRes
        res?.let { createFileRes ->
            val grpcChunks = mutableListOf<Gfs.Chunk>()
            createFileRes.chunks?.forEach {
                grpcChunks.add(
                    Gfs.Chunk.newBuilder().setChunkHandle(it.handle).setChunkIndex(it.index).build()
                )
            }
            return Gfs.CreateFileRes.newBuilder()
                .setIsSuccessful(createFileRes.successful)
                .addAllChunks(grpcChunks)
                .build()
        }

        return Gfs.CreateFileRes.newBuilder().setIsSuccessful(false).build()
    }

    override suspend fun deleteFile(request: Gfs.DeleteFileReq): Gfs.Status {
        val reqIdElement = currentCoroutineContext()[TagInterceptor.ReqIdKey]
        val reqId = reqIdElement!!.keyStr
        logger.info("reqId: {}, received delete file request: {}", reqId, request.fileName)
        val res = masterGfs.deleteFile(reqId, request.fileName).await() as? FileProtocol.DeleteFileRes
        return Gfs.Status.newBuilder().setCode(Status.OK.code.value()).setStatus(Status.OK.code.toString()).build()
    }

    override suspend fun getLease(request: Gfs.LeaseGrantReq): Gfs.LeaseGrantRes {
        val reqIdElement = currentCoroutineContext()[TagInterceptor.ReqIdKey]
        val reqId = reqIdElement!!.keyStr

        logger.info("reqId: {}, received lease grant request", reqId)

        val res = masterGfs.getLease(reqId, request.chunkHandlesList).await() as? FileProtocol.LeaseGrantRes
        val leases = ArrayList<Gfs.Lease>()

        res?.let {
            it.leases.forEach { lease ->
                val grpcLease = Gfs.Lease.newBuilder()
                    .setChunkHandle(lease.chunkId)
                    .setPrimary(lease.primary)
                    .addAllReplicas(lease.replicas)
                    .setGrantedAt(lease.ts)
                    .setDuration(lease.duration)
                    .build()
                leases.add(grpcLease)
            }
            return Gfs.LeaseGrantRes.newBuilder().addAllLeases(leases).build()
        }
        //TODO handle incorrect datatype
        return super.getLease(request)
    }

    val port = 7009

    val server: Server = ServerBuilder
        .forPort(port)
        .intercept(TagInterceptor(context))
        .addService(this)
        .build()

    fun startServer() {
        server.start()
    }
}