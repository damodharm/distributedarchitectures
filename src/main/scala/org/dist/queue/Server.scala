package org.dist.queue

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

import org.dist.queue.log.LogManager
import org.dist.queue.network.SocketServer

class Server(config: Config, time: Time = SystemTime) {
  private var shutdownLatch = new CountDownLatch(1)
  var kafkaZooKeeper:KafkaZooKeeper = _
  var controller:Controller = _
  var replicaManager: ReplicaManager = _
  var logManager: LogManager = null
  var kafkaScheduler:KafkaScheduler = null

  var isShuttingDown:AtomicBoolean = _

  var socketServer:SocketServer = _
  var apis: KafkaApis = null

  def startup(): Unit = {
    isShuttingDown = new AtomicBoolean(false)
    kafkaScheduler = new KafkaScheduler(1)
    kafkaScheduler.startup()

    logManager = new LogManager(config,kafkaScheduler, time)
    logManager.startup()

    socketServer = new SocketServer(config.brokerId,
      config.hostName,
      config.port,
      config.numNetworkThreads,
      config.queuedMaxRequests,
      config.socketSendBufferBytes,
      config.socketReceiveBufferBytes,
      config.socketRequestMaxBytes)



    kafkaZooKeeper = new KafkaZooKeeper(config)
    kafkaZooKeeper.startup()

    controller = new Controller(config, kafkaZooKeeper.getZookeeperClient, socketServer)


    replicaManager = new ReplicaManager(config, time, kafkaZooKeeper.getZookeeperClient, logManager, isShuttingDown)
    replicaManager.startup()

    apis = new KafkaApis(replicaManager, kafkaZooKeeper.getZookeeperClient, config.brokerId, controller)
    socketServer.startup(apis)

    controller.startup()
  }
  def shutdown(): Unit = {
    shutdownLatch.countDown()
  }

  def awaitShutdown(): Unit = shutdownLatch.await()
}