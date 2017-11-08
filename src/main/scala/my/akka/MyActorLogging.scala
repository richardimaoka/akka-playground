package my.akka

import akka.actor.{Actor, ActorLogging, ActorSelectionMessage, ActorSystem, Props}
import akka.event.Logging
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

  def main(args: Array[String]): Unit = {
    try {
      Wrap("basics")(basics())
      Wrap("printName")(printName())
      Wrap("logLevel")(logLevel())
    } finally {
      system.terminate()
    }
  }
}
