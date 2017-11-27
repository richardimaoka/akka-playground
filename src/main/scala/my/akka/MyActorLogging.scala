package my.akka

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.event.Logging.MDC
import akka.event.{DiagnosticLoggingAdapter, LogSource, Logging, LoggingReceive}
import com.typesafe.config.ConfigFactory
import my.akka.wrapper.Wrap

/**
  * Logging // akka.event.Logging, object only
  *   Main entry point for Akka logging: log levels and message types. Obtain an implementation of the Logging trait with
  *   suitable and efficient methods for generating log events.
  *   def apply() returns LoggingAdapter
  *
  * LoggingAdapter // akka.event.LoggingAdapter, trait
  *   Logging wrapper, evaluate toString only if the log level is enabled. Obtaining an implementation from the Logging object.
  *   def error   | isErrorEnabled   | notifyError
  *   def warning | isWarningEnabled | notifyWarning
  *   def info    | isInfoEnabled    | notifyInfo
  *   def debug   | isDebugEnabled   | notifyDebug
  *
  * Logger
  *   slf4J's Logger, used inside akka.event.slf4j
  *
  * Slf4Logger //akka.event.slf4j, extends Actor
  *   to be specified in the config, and instantiated in EventStream -> LoggingBus.startDefaultLoggers()
  *     akka {
  *       loggers = ["akka.event.slf4j.Slf4jLogger"]
  *       logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
  *     }
  */
class MyActor extends Actor {
  val log = Logging(context.system, this)
  override def preStart() = {
    log.debug("Starting")
  }
  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.error(reason, "Restarting due to [{}] when processing [{}]",
      reason.getMessage, message.getOrElse(""))
  }
  def receive = {
    case "test" => log.info("Received test")
    case x => log.warning("Received unknown message: {}", x)
  }
}

class MyActorMixedIn extends Actor with ActorLogging {
  /**
    * trait ActorLogging { this: Actor ⇒
    *   ...
    *   // Logging main entry point is used to get LoggingAdapter
    *   // this works as `implicit LogSource[Actor]` must be defined in akka (where? I dunno)
    *   _log = akka.event.Logging(context.system, this)
    * }
    */
  override def preStart() = {
    log.debug("Starting")
  }
  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.error(reason, "Restarting due to [{}] when processing [{}]",
      reason.getMessage, message.getOrElse(""))
  }
  def receive = {
    case "test" => log.info("Received test")
    case x => log.warning("Received unknown message: {}", x)
  }
}

class MyDebugActor extends Actor {
  val log = Logging(context.system, this)
  override def preStart() = {
    log.debug("Starting")
  }
  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.error(reason, "Restarting due to [{}] when processing [{}]",
      reason.getMessage, message.getOrElse(""))
  }
  def receive = {
    case x =>
      log.debug(s"Received ${x}")
      log.info(s"Received ${x}")
  }
}

class MyLoggingReceiveActor extends Actor {
  def receive = LoggingReceive {
    case _ =>
  }
}

class MyUnhandledActor extends Actor {
  def receive = {
    case _: Int =>
  }
}

class MyDiagnosticActor extends Actor {
  val log: DiagnosticLoggingAdapter = Logging(this)

  override def preStart(): Unit = {
    super.preStart()
    val mdc = Map("requestId" -> 1234, "visitorId" -> 5678)
    log.mdc(mdc)

    // Log something
    log.info("Starting new request")

    log.clearMDC()
  }

  def receive = Actor.emptyBehavior
}

final case class Req(work: String, visitorId: Int)

class MdcActorMixin extends Actor with akka.actor.DiagnosticActorLogging {
  var reqId = 0

  override def mdc(currentMessage: Any): MDC = {
    reqId += 1
    val always = Map("requestId" -> reqId)
    val perMessage = currentMessage match {
      case r: Req => Map("visitorId" -> r.visitorId)
      case _ => Map()
    }
    always ++ perMessage
  }

  def receive: Receive = {
    case r: Req => {
      log.info(s"Starting new request: ${r.work}")
    }
  }
}

object MyActorLogging {

  def basics(): Unit ={
    val system = ActorSystem("MyActorLogging")
    try{
      val actor1 = system.actorOf(Props(new MyActor), "actor1")
      val actor2 = system.actorOf(Props(new MyActor), "actor2")
      actor1 ! "test"
      //[INFO] [10/31/2017 05:32:24.279] [MyActorLogging-akka.actor.default-dispatcher-3] [akka://MyActorLogging/user/actor1] Received test
      actor2 ! "test"
      //[INFO] [10/31/2017 05:32:24.279] [MyActorLogging-akka.actor.default-dispatcher-4] [akka://MyActorLogging/user/actor2] Received test

      val args = Array("The", "brown", "fox", "jumps", 42)
      system.log.info("five parameters: {}, {}, {}, {}, {}", args)
      //[INFO] [11/02/2017 06:57:17.831] [run-main-1] [akka.actor.ActorSystemImpl(MyActorLogging)] five parameters: The, brown, fox, jumps, 42

      system.log.debug("five parameters: {}, {}, {}, {}, {}", args)
      //prints out nothing as the default debug level is not debug
      println(system.log.isDebugEnabled)
    } finally {
      system.terminate()
    }

  }

  def printName(): Unit ={
    println(Logging.simpleName(this))
    //MyActorLogging$

    println(Logging.messageClassName("some message"))
    //java.lang.String
  }

  def logLevel(): Unit ={
    println(Logging.LogLevel(0))
    println(Logging.LogLevel(1))
    println(Logging.LogLevel(2))
    println(Logging.LogLevel(3))
    println(Logging.LogLevel(4))
    println(Logging.LogLevel(5))
    println(Logging.LogLevel(6))
    // LogLevel(0)
    // LogLevel(1)
    // LogLevel(2)
    // LogLevel(3)
    // LogLevel(4)
    // LogLevel(5)
    // LogLevel(6)
    println(Logging.ErrorLevel)   // LogLevel(1)
    println(Logging.WarningLevel) // LogLevel(2)
    println(Logging.InfoLevel)    // LogLevel(3)
    println(Logging.DebugLevel)   // LogLevel(4)

    // oops this is not possible, as `OffLevel` is private
    // println(Logging.OffLevel)
  }

  def logEvent(): Unit ={
    println(Logging.Debug("logSource", this.getClass, "message"))
    println(Logging.Debug("logSource", this.getClass, "message", Logging.emptyMDC))
  }

  def levelFor(): Unit = {
    println(Logging.levelFor(""))

    //This doesn't compile since MyActorLogging doesn't extend LogEvent
    //println(Logging.levelFor(MyActorLogging.getClass))

    // println(Logging.levelFor(Logging.Debug("source", this.getClass, "message" ))
    //    println(Logging.levelFor(Logging.WarningLevel))
    //    println(Logging.levelFor(Logging.ErrorLevel))
    //    println(Logging.levelFor(Logging.InfoLevel))
  }

  def config1(): Unit ={
    val config =
      """
        |akka {
        |  log-dead-letters = 10
        |  log-dead-letters-during-shutdown = on
        |}
      """.stripMargin

    val system = ActorSystem("system1", ConfigFactory.parseString(config))
    try{
      val ref = system.actorOf(Props[MyActor], "actor-bound-to-die")
      ref ! PoisonPill

      system.terminate()
      for(_ <- 1 to 100)
        ref ! "Hellow world"
      // Only up to 10x of the following message shown due to log-dead-letters = 10
      // [INFO] [11/10/2017 07:15:05.642] [system1-akka.actor.default-dispatcher-5]
      //   [akka://system1/user/actor-bound-to-die] Message [java.lang.String] without sender
      //   to Actor[akka://system1/user/actor-bound-to-die#1238404946] was not delivered.
      //   [1] dead letters encountered. This logging can be turned off or adjusted with configuration settings 'akka.log-dead-letters' and 'akka.log-dead-letters-during-shutdown'.
    } finally {
      system.terminate()
    }
  }

  def config2(): Unit ={
    val config =
      """
        |akka {
        |  log-dead-letters = 10
        |  log-dead-letters-during-shutdown = off
        |}
      """.stripMargin

    val system = ActorSystem("system2", ConfigFactory.parseString(config))
    try{
      val ref = system.actorOf(Props[MyActor], "actor-bound-to-die")
      ref ! PoisonPill

      system.terminate()
      for(_ <- 1 to 100)
        ref ! "Hellow world"
      // No deadletter log message due to log-dead-letters-during-shutdown = off
    } finally {
      system.terminate()
    }
  }

  def config3(): Unit ={
    val config =
      """
        |akka {
        |  loglevel = "DEBUG"
        |}
      """.stripMargin

    val system = ActorSystem("system3", ConfigFactory.parseString(config))
    try{
      val ref1 = system.actorOf(Props[MyDebugActor], "actor-bound-to-die")
      for(_ <- 1 to 3)
        ref1 ! "Hellow world"
      // [DEBUG] [11/11/2017 17:26:18.931] [run-main-30] [EventStream(akka://system3)] logger log1-Logging$DefaultLogger started
      // [DEBUG] [11/11/2017 17:26:18.931] [run-main-30] [EventStream(akka://system3)] Default Loggers started
      // [DEBUG] [11/11/2017 17:26:18.963] [system3-akka.actor.default-dispatcher-5] [akka://system3/user/actor-bound-to-die] Starting
      // [DEBUG] [11/11/2017 17:26:18.963] [system3-akka.actor.default-dispatcher-5] [akka://system3/user/actor-bound-to-die] Received Hellow world
      // [INFO]  [11/11/2017 17:26:18.963] [system3-akka.actor.default-dispatcher-5] [akka://system3/user/actor-bound-to-die] Received Hellow world
      // [DEBUG] [11/11/2017 17:26:18.963] [system3-akka.actor.default-dispatcher-5] [akka://system3/user/actor-bound-to-die] Received Hellow world
      // [INFO]  [11/11/2017 17:26:18.963] [system3-akka.actor.default-dispatcher-5] [akka://system3/user/actor-bound-to-die] Received Hellow world
      // [DEBUG] [11/11/2017 17:26:18.963] [system3-akka.actor.default-dispatcher-5] [akka://system3/user/actor-bound-to-die] Received Hellow world
      // [INFO]  [11/11/2017 17:26:18.963] [system3-akka.actor.default-dispatcher-5] [akka://system3/user/actor-bound-to-die] Received Hellow world
      // [DEBUG] [11/11/2017 17:26:19.053] [system3-akka.actor.default-dispatcher-6] [EventStream] shutting down: StandardOutLogger started
      Thread.sleep(100)
    } finally {
      system.terminate()
    }
  }

  def config3_1(): Unit ={
    val config =
      """
        |akka {
        |  loglevel = "DEBUG"
        |}
      """.stripMargin

    val system = ActorSystem("system3", ConfigFactory.parseString(config))
    //[DEBUG] [11/11/2017 17:40:35.687] [run-main-3d] [EventStream(akka://system3)] logger log1-Logging$DefaultLogger started
    //[DEBUG] [11/11/2017 17:40:35.687] [run-main-3d] [EventStream(akka://system3)] Default Loggers started
    //[DEBUG] [11/11/2017 17:40:35.689] [system3-akka.actor.default-dispatcher-5] [EventStream] shutting down: StandardOutLogger started
    system.terminate()
  }

  def config4(): Unit ={
    val config =
      """
        |akka {
        |  # Log the complete configuration at INFO level when the actor system is started.
        |  # This is useful when you are uncertain of what configuration is used.
        |  log-config-on-start = on
        |}
      """.stripMargin

    val system4 = ActorSystem("system4", ConfigFactory.parseString(config))
    system4.terminate()
  }

  def config5(): Unit ={
    val config =
      """
        |akka {
        |  loglevel = "DEBUG"
        |  actor {
        |    debug {
        |      # enable function of LoggingReceive, which is to log any received message at
        |      # DEBUG level
        |      receive = on
        |    }
        |  }
        |}
      """.stripMargin
    val system = ActorSystem("system5", ConfigFactory.parseString(config))
    try{
      val ref = system.actorOf(Props[MyLoggingReceiveActor], "actor-bound-to-die")
      for(_ <- 1 to 3)
        ref ! "Hellow world"
      // [DEBUG] [11/11/2017 17:37:16.163] [run-main-36] [EventStream(akka://system5)] logger log1-Logging$DefaultLogger started
      // [DEBUG] [11/11/2017 17:37:16.163] [run-main-36] [EventStream(akka://system5)] Default Loggers started
      // [DEBUG] [11/11/2017 17:37:16.179] [system5-akka.actor.default-dispatcher-4] [akka://system5/user/actor-bound-to-die] received handled message Hellow world from Actor[akka://system5/deadLetters]
      // [DEBUG] [11/11/2017 17:37:16.179] [system5-akka.actor.default-dispatcher-4] [akka://system5/user/actor-bound-to-die] received handled message Hellow world from Actor[akka://system5/deadLetters]
      // [DEBUG] [11/11/2017 17:37:16.179] [system5-akka.actor.default-dispatcher-4] [akka://system5/user/actor-bound-to-die] received handled message Hellow world from Actor[akka://system5/deadLetters]
      // [DEBUG] [11/11/2017 17:37:16.323] [system5-akka.actor.default-dispatcher-3] [EventStream] shutting down: StandardOutLogger started
      Thread.sleep(150)
    } finally {
      system.terminate()
    }
  }

  def config6(): Unit ={
    val config =
      """
        |akka {
        |  loglevel = "DEBUG"
        |  actor {
        |    debug {
        |      # enable DEBUG logging of actor lifecycle changes
        |      lifecycle = on
        |    }
        |  }
        |}
      """.stripMargin

    val system = ActorSystem("system6", ConfigFactory.parseString(config))
    try{
      val ref = system.actorOf(Props[MyActor], "actor-bound-to-die")
      ref ! PoisonPill
      //[DEBUG] [11/11/2017 17:47:19.246] [run-main-42] [EventStream(akka://system6)] logger log1-Logging$DefaultLogger started
      //[DEBUG] [11/11/2017 17:47:19.246] [run-main-42] [EventStream(akka://system6)] Default Loggers started
      //[DEBUG] [11/11/2017 17:47:19.246] [system6-akka.actor.default-dispatcher-2] [akka://system6/system] now supervising Actor[akka://system6/system/deadLetterListener#-2067965489]
      //[DEBUG] [11/11/2017 17:47:19.246] [system6-akka.actor.default-dispatcher-3] [akka://system6/system/deadLetterListener] started (akka.event.DeadLetterListener@54655846)
      //[DEBUG] [11/11/2017 17:47:19.247] [system6-akka.actor.default-dispatcher-2] [akka://system6/system] now supervising Actor[akka://system6/system/eventStreamUnsubscriber-7#1233977000]
      //[DEBUG] [11/11/2017 17:47:19.247] [system6-akka.actor.default-dispatcher-3] [akka://system6/system/eventStreamUnsubscriber-7] started (akka.event.EventStreamUnsubscriber@1167dcae)
      //[DEBUG] [11/11/2017 17:47:19.247] [system6-akka.actor.default-dispatcher-5] [akka://system6/system/deadLetterListener] now watched by Actor[akka://system6/system/eventStreamUnsubscriber-7#1233977000]
      //[DEBUG] [11/11/2017 17:47:19.247] [system6-akka.actor.default-dispatcher-4] [akka://system6/system/log1-Logging$DefaultLogger] now watched by Actor[akka://system6/system/eventStreamUnsubscriber-7#1233977000]
      //[DEBUG] [11/11/2017 17:47:19.247] [system6-akka.actor.default-dispatcher-4] [akka://system6/user] now supervising Actor[akka://system6/user/actor-bound-to-die#-1376776858]
      //[DEBUG] [11/11/2017 17:47:19.247] [system6-akka.actor.default-dispatcher-4] [akka://system6/user] stopping
      //[DEBUG] [11/11/2017 17:47:19.247] [system6-akka.actor.default-dispatcher-5] [akka://system6/user/actor-bound-to-die] Starting
      //[DEBUG] [11/11/2017 17:47:19.247] [system6-akka.actor.default-dispatcher-5] [akka://system6/user/actor-bound-to-die] started (my.akka.MyActor@460bab94)
      //[DEBUG] [11/11/2017 17:47:19.247] [system6-akka.actor.default-dispatcher-5] [akka://system6/user/actor-bound-to-die] stopped
      //[DEBUG] [11/11/2017 17:47:19.247] [system6-akka.actor.default-dispatcher-4] [akka://system6/user] stopped
      //[DEBUG] [11/11/2017 17:47:19.248] [system6-akka.actor.default-dispatcher-2] [EventStream] shutting down: StandardOutLogger started
    } finally {
      system.terminate()
    }
  }

  def config7(): Unit ={
    val config =
      """
        |akka {
        |  loglevel = "DEBUG"
        |  actor {
        |    debug {
        |      # enable DEBUG logging of unhandled messages
        |      unhandled = on
        |    }
        |  }
        |}
      """.stripMargin

    val system = ActorSystem("system7", ConfigFactory.parseString(config))
    try{
      val ref = system.actorOf(Props[MyUnhandledActor], "actor-bound-to-die")
      ref ! "1"
      ref ! "2"
      //[DEBUG] [11/11/2017 18:00:40.080] [run-main-50] [EventStream(akka://system7)] logger log1-Logging$DefaultLogger started
      //[DEBUG] [11/11/2017 18:00:40.081] [run-main-50] [EventStream(akka://system7)] Default Loggers started
      //[DEBUG] [11/11/2017 18:00:40.092] [system7-akka.actor.default-dispatcher-2] [akka://system7/user/actor-bound-to-die] unhandled message from Actor[akka://system7/deadLetters]: 1
      //[DEBUG] [11/11/2017 18:00:40.092] [system7-akka.actor.default-dispatcher-2] [akka://system7/user/actor-bound-to-die] unhandled message from Actor[akka://system7/deadLetters]: 2
      //[DEBUG] [11/11/2017 18:00:40.138] [system7-akka.actor.default-dispatcher-2] [EventStream] shutting down: StandardOutLogger started
      Thread.sleep(50)
    } finally {
      system.terminate()
    }
  }

  def config8(): Unit ={
    val config =
      """
        |akka {
        |  loglevel = "DEBUG"
        |  actor {
        |    debug {
        |      # enable DEBUG logging of subscription changes on the eventStream
        |      event-stream = on
        |    }
        |  }
        |}
      """.stripMargin

    val system = ActorSystem("system8", ConfigFactory.parseString(config))
    try{
      val ref = system.actorOf(Props[MyActor], "actor-bound-to-die")
      ref ! PoisonPill
      //[DEBUG] [run-main-46] [EventStream(akka://system7)] logger log1-Logging$DefaultLogger started
      //[DEBUG] [run-main-46] [EventStream(akka://system7)] Default Loggers started
      //[DEBUG] [run-main-46] [EventStream] unsubscribing StandardOutLogger from all channels
      //[DEBUG] [system7-akka.actor.default-dispatcher-4] [EventStream] subscribing Actor[akka://system7/system/deadLetterListener#-1563292194] to channel class akka.actor.DeadLetter
      //[DEBUG] [system7-akka.actor.default-dispatcher-3] [EventStreamUnsubscriber] registering unsubscriber with akka.event.EventStream@1c28c63
      //[DEBUG] [system7-akka.actor.default-dispatcher-3] [EventStream] initialized unsubscriber to: Actor[akka://system7/system/eventStreamUnsubscriber-8#-1590578735], registering 2 initial subscribers with it
      //[DEBUG] [system7-akka.actor.default-dispatcher-3] [EventStreamUnsubscriber] watching Actor[akka://system7/system/log1-Logging$DefaultLogger#2087616275] in order to unsubscribe from EventStream when it terminates
      //[DEBUG] [system7-akka.actor.default-dispatcher-3] [EventStreamUnsubscriber] watching Actor[akka://system7/system/deadLetterListener#-1563292194] in order to unsubscribe from EventStream when it terminates
      //[DEBUG] [system7-akka.actor.default-dispatcher-3] [akka://system7/user/actor-bound-to-die] Starting
      //[DEBUG] [system7-akka.actor.default-dispatcher-5] [EventStream] subscribing StandardOutLogger to channel class akka.event.Logging$Error
    } finally {
      system.terminate()
    }
  }

  def config9(): Unit ={
    val config =
      """
        |akka {
        |  loglevel = "DEBUG"
        |  actor {
        |    debug {
        |      receive = on
        |      lifecycle = on
        |      unhandled = on
        |      event-stream = on
        |    }
        |  }
        |}
      """.stripMargin

    val system = ActorSystem("system8", ConfigFactory.parseString(config))
    try{
      val refMy = system.actorOf(Props[MyActor], "my-actor")
      val refUnhandled = system.actorOf(Props[MyUnhandledActor], "my-unhandled")
      val refLoggingReceive = system.actorOf(Props[MyLoggingReceiveActor], "my-logging-receive")

      refMy ! "1"
      refMy ! "2"

      refUnhandled ! "3"
      refUnhandled ! "4"

      refLoggingReceive ! "5"
      refLoggingReceive ! "6"

      refMy ! PoisonPill
      refUnhandled ! PoisonPill
      refLoggingReceive ! PoisonPill
      //[DEBUG]  [run-main-54] [EventStream(akka://system8)] logger log1-Logging$DefaultLogger started
      //[DEBUG]  [run-main-54] [EventStream] subscribing Actor[akka://system8/system/UnhandledMessageForwarder#-1327394363] to channel class akka.actor.UnhandledMessage
      //[DEBUG]  [run-main-54] [EventStream(akka://system8)] Default Loggers started
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [akka://system8/system] now supervising Actor[akka://system8/system/UnhandledMessageForwarder#-1327394363]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/system/UnhandledMessageForwarder] started (akka.event.LoggingBus$$anon$3@707f32c6)
      //[DEBUG]  [run-main-54] [EventStream] unsubscribing StandardOutLogger from all channels
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/system] now supervising Actor[akka://system8/system/deadLetterListener#1877428677]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [EventStream] subscribing Actor[akka://system8/system/deadLetterListener#1877428677] to channel class akka.actor.DeadLetter
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [akka://system8/system/deadLetterListener] started (akka.event.DeadLetterListener@2995e96c)
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [EventStreamUnsubscriber] registering unsubscriber with akka.event.EventStream@2daa6886
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [EventStream] initialized unsubscriber to: Actor[akka://system8/system/eventStreamUnsubscriber-10#-372857631], registering 3 initial subscribers with it
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [akka://system8/system/eventStreamUnsubscriber-10] started (akka.event.EventStreamUnsubscriber@68a8e357)
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/system] now supervising Actor[akka://system8/system/eventStreamUnsubscriber-10#-372857631]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [EventStreamUnsubscriber] watching Actor[akka://system8/system/log1-Logging$DefaultLogger#-1396537556] in order to unsubscribe from EventStream when it terminates
      //[DEBUG]  [system8-akka.actor.default-dispatcher-2] [akka://system8/system/log1-Logging$DefaultLogger] now watched by Actor[akka://system8/system/eventStreamUnsubscriber-10#-372857631]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [EventStreamUnsubscriber] watching Actor[akka://system8/system/UnhandledMessageForwarder#-1327394363] in order to unsubscribe from EventStream when it terminates
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [EventStreamUnsubscriber] watching Actor[akka://system8/system/deadLetterListener#1877428677] in order to unsubscribe from EventStream when it terminates
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/system/UnhandledMessageForwarder] now watched by Actor[akka://system8/system/eventStreamUnsubscriber-10#-372857631]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/system/deadLetterListener] now watched by Actor[akka://system8/system/eventStreamUnsubscriber-10#-372857631]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/user] now supervising Actor[akka://system8/user/my-actor#-703367913]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/user] now supervising Actor[akka://system8/user/my-unhandled#-1412574564]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [akka://system8/user/my-actor] Starting
      //[DEBUG]  [system8-akka.actor.default-dispatcher-5] [akka://system8/user/my-unhandled] started (my.akka.MyUnhandledActor@6658f9c4)
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [akka://system8/user/my-actor] started (my.akka.MyActor@5fd21007)
      //[DEBUG]  [system8-akka.actor.default-dispatcher-5] [akka://system8/user/my-logging-receive] started (my.akka.MyLoggingReceiveActor@689f1b7f)
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/user] now supervising Actor[akka://system8/user/my-logging-receive#1067809181]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/user/my-unhandled] unhandled message from Actor[akka://system8/deadLetters]: 3
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/user/my-logging-receive] received handled message 5 from Actor[akka://system8/deadLetters]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/user/my-logging-receive] received handled message 6 from Actor[akka://system8/deadLetters]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-5] [akka://system8/user/my-unhandled] stopped
      //[DEBUG]  [system8-akka.actor.default-dispatcher-3] [akka://system8/user/my-logging-receive] stopped
      //[DEBUG]  [system8-akka.actor.default-dispatcher-6] [akka://system8/user/my-unhandled] unhandled message from Actor[akka://system8/deadLetters]: 4
      //[WARN]   [system8-akka.actor.default-dispatcher-4] [akka://system8/user/my-actor] Received unknown message: 1
      //[WARN]   [system8-akka.actor.default-dispatcher-4] [akka://system8/user/my-actor] Received unknown message: 2
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [akka://system8/user/my-actor] stopped
      //[DEBUG]  [system8-akka.actor.default-dispatcher-8] [akka://system8/user] stopped
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [EventStream] subscribing StandardOutLogger to channel class akka.event.Logging$Error      Thread.sleep(50)
    } finally {
      system.terminate()
    }
  }

  def logSource(): Unit ={
    object MyType {
      implicit val logSource: LogSource[MyType] = new LogSource[MyType] {
        def genString(o: MyType): String = o.getClass.getName + " bah hahah "
        override def getClazz(o: MyType): Class[_] = o.getClass
      }
    }

    class MyType(system: ActorSystem) {
      import MyType._
      import akka.event.Logging

      //def apply[T: LogSource](system: ActorSystem, logSource: T): LoggingAdapter
      val log = Logging(system, this /*: MyType*/)
    }

    val system = ActorSystem("systemLogSource")
    try{
      val mytype = new MyType(system)
      mytype.log.info("message")
      //[INFO] [11/12/2017 00:58:54.872] [run-main-5] [my.akka.MyActorLogging$MyType$3 bah hahah ] message

      Logging(system, "some string").info("message")
      //[INFO] [11/12/2017 01:03:59.254] [run-main-b] [some string(akka://systemLogSource)] message

      class MyType2
      //def apply[T: LogSource](system: ActorSystem, logSource: T): LoggingAdapter
      // Compile error: Cannot find LogSource for MyType2 please see ScalaDoc for LogSource for how to obtain or construct one.
      // val log = Logging(system, new MyType2)

      //Cannot find LogSource for Int please see ScalaDoc for LogSource for how to obtain or construct one.
      //Logging(system, 100).info("message")
      //[INFO] [11/12/2017 01:03:59.254] [run-main-b] [some string(akka://systemLogSource)] message

    } finally {
      system.terminate()
    }
  }

  def slf4j(): Unit = {
    val config =
      """
        |akka {
        |  loggers = ["akka.event.slf4j.Slf4jLogger"]
        |  loglevel = "DEBUG"
        |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
        |}
      """.stripMargin
    val system = ActorSystem("slf4jsystem", ConfigFactory.parseString(config))
    try{
      val log = Logging(system.eventStream, "my.nice.string")
      log.info("hello worold")
      log.debug("hello worold")
      //07:10:25.896 [slf4jsystem-akka.actor.default-dispatcher-2] INFO akka.event.slf4j.Slf4jLogger - Slf4jLogger started
      //07:10:25.901 [slf4jsystem-akka.actor.default-dispatcher-2] DEBUG akka.event.EventStream - logger log1-Slf4jLogger started
      //07:10:25.905 [slf4jsystem-akka.actor.default-dispatcher-2] DEBUG akka.event.EventStream - Default Loggers started
      //
      // compared to akka default logging style, defined in StdOutLogger, which is used by
      //   class DefaultLogger extends Actor with StdOutLogger with RequiresMessageQueue[LoggerMessageQueueSemantics]
      //[DEBUG]  [system8-akka.actor.default-dispatcher-4] [akka://system8/user/my-actor] stopped

      val actor = system.actorOf(Props(new MyActor), "actor2")
      actor ! "test"
      Thread.sleep(50)
    } finally {
      system.terminate()
    }
  }

  def mdc(): Unit = {
    val config =
      """
        |akka {
        |  loggers = ["akka.event.slf4j.Slf4jLogger"]
        |  loglevel = "DEBUG"
        |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
        |}
      """.stripMargin
    val system = ActorSystem("mdcsystem", ConfigFactory.parseString(config))
    try{
      val actor = system.actorOf(Props(new MyDiagnosticActor), "actor2")
      actor ! "test"
      // if you insert %X{requestId} to logback.xml, something like below will be generated
      //   <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
      //     <encoder>
      //       <pattern>
      //         %-5level %logger{36} [req: %X{requestId}, visitor: %X{visitorId}] - %msg%n
      //       </pattern>
      //     </encoder>
      //   </appender>
      // 09:55:41.542 [mdcsystem-akka.actor.default-dispatcher-5] 1234 INFO  my.akka.MyDiagnosticActor - Starting new request
      Thread.sleep(50)
    } finally {
      system.terminate()
    }
  }
  def main(args: Array[String]): Unit = {
    //      Wrap("basics")(basics())
    //      Wrap("printName")(printName())
    //      Wrap("logLevel")(logLevel())
    //      Wrap("logEvent")(logEvent())
    //      Wrap("levelFor")(levelFor())
    //      Wrap("config1")(config1())
    //      Wrap("config2")(config2())
    //      Wrap("config3")(config3())
    //      Wrap("config3_1")(config3_1())
    //      //Wrap("config4")(config4())
    //      Wrap("config5")(config5())
    //      Wrap("config6")(config6())
    //      Wrap("config7")(config7())
    //      Wrap("config8")(config8())
    //      Wrap("config9")(config9())
    Wrap("logSource")(logSource())
    Wrap("slf4j")(slf4j)
    Wrap("mdc")(mdc)
  }
}
