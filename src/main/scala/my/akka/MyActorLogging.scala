package my.akka

import akka.actor.{Actor, ActorLogging, ActorSelectionMessage, ActorSystem, PoisonPill, Props}
import akka.event.{LogSource, Logging}
import com.typesafe.config.ConfigFactory
import my.akka.wrapper.Wrap

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

object MyActorLogging {
  val system = ActorSystem("MyActorLogging")

  def basics(): Unit ={
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
    val config = """
      |akka {
      |  log-dead-letters = 10
      |  log-dead-letters-during-shutdown = on
      |}
    """.stripMargin

    val system1 = ActorSystem("system1", ConfigFactory.parseString(config))
    try{
      val ref1 = system1.actorOf(Props[MyActor], "actor-bound-to-die")
      ref1 ! PoisonPill

      for(_ <- 1 to 100)
        ref1 ! "Hellow world"
      // Only 10x of the following message shown
      // [INFO] [11/10/2017 07:15:05.642] [system1-akka.actor.default-dispatcher-5]
      //   [akka://system1/user/actor-bound-to-die] Message [java.lang.String] without sender
      //   to Actor[akka://system1/user/actor-bound-to-die#1238404946] was not delivered.
      //   [1] dead letters encountered. This logging can be turned off or adjusted with configuration settings 'akka.log-dead-letters' and 'akka.log-dead-letters-during-shutdown'.
    } finally {
      system1.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    try {
      Wrap("basics")(basics())
      Wrap("printName")(printName())
      Wrap("logLevel")(logLevel())
      Wrap("logEvent")(logEvent())
      Wrap("levelFor")(levelFor())
      Wrap("config1")(config1())
    } finally {
      system.terminate()
    }
  }
}
