package my.akka

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

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
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("MyActorLogging")
    try {
      val actor1 = system.actorOf(Props(new MyActor), "actor1")
      val actor2 = system.actorOf(Props(new MyActor), "actor2")
      actor1 ! "test"
      //[INFO] [10/31/2017 05:32:24.279] [MyActorLogging-akka.actor.default-dispatcher-3] [akka://MyActorLogging/user/actor1] Received test
      actor2 ! "test"
      //[INFO] [10/31/2017 05:32:24.279] [MyActorLogging-akka.actor.default-dispatcher-4] [akka://MyActorLogging/user/actor2] Received test

      val args = Array("The", "brown", "fox", "jumps", 42)
      system.log.debug("five parameters: {}, {}, {}, {}, {}", args)
      
    } finally {
      system.terminate()
    }
  }
}
