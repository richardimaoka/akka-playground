package my.akka

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.{Backoff, BackoffSupervisor, ask}

import scala.concurrent.Await
import scala.concurrent.duration._


class Child extends Actor {
  println(s"[Child]: created.         (path = ${this.self.path}, instance = ${this})")

  override def preStart(): Unit = {
    println(s"[Child]: preStart called. (path = ${this.self.path}, instance = ${this})")
    super.preStart()
  }

  override def postStop(): Unit = {
    println(s"[Child]: postStop called. (path = ${this.self.path}, instance = ${this})")
    super.postStop()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    println(s"[Child]: preRestart called with ($reason, $message). (path = ${this.self.path}, instance = ${this})")
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    println(s"[Child]: postRestart called with ($reason). (path = ${this.self.path}, instance = ${this})")
    super.postRestart(reason)
  }

  def receive = {
    case "boom" =>
      throw new Exception("kaboom")
    case "get ref" =>
      sender() ! self
    case a: Any =>
      println(s"[Child]: received ${a}")
  }
}

object Child {
  def props: Props
    = Props(new Child)

  def backOffOnFailureProps: Props
    = BackoffSupervisor.props(
    Backoff.onFailure(
      Child.props,
      childName = "myEcho",
      minBackoff = 1.seconds,
      maxBackoff = 30.seconds,
      randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
    ))

  def backOffOnStopProps: Props
  = BackoffSupervisor.props(
    Backoff.onStop(
      Child.props,
      childName = "myEcho",
      minBackoff = 1.seconds,
      maxBackoff = 10.seconds,
      randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
    ))
}

object BackoffSuperVisorApp {
  def defaultSuperVisorCase(): Unit = {
    println(
      """
        |default ---------------------------
      """.stripMargin)

    val system = ActorSystem("app")
    try{
      /**
       * Let's see if "hello" message is received by the child
       */
      val child = system.actorOf(Child.props, "child")
      Thread.sleep(100)
      child ! "hello"
      //[Child]: received hello

      /**
       * Now restart the child with an exception within its receive method
       * and see if the `child` ActorRef is still valid (i.e. ActorRef incarnation remains same)
       */
      child ! "boom"
      Thread.sleep(200)

      child ! "hello after normal exception"
      //[Child]: received hello after normal exception

      /**
       * PoisonPill causes the child actor to `Stop`, different from restart.
       * The ActorRef incarnation gets updated.
       */
      child ! PoisonPill
      Thread.sleep(200)

      /**
       * This causes delivery to deadLetter, since the "incarnation" of ActorRef `child` became obsolete
       * after child is "Stopped"
       *
       * An incarnation is tied to an ActorRef (NOT to its internal actor instance)
       * and the same incarnation means "you can keep using the same ActorRef"
       */
      child ! "hello after PoisonPill"
      // [akka://app/user/parent/child-1] Message [java.lang.String] without sender to Actor[akka://app/user/child#-767539042]
      //   was not delivered. [1] dead letters encountered.

      Thread.sleep(200)
    }
    finally{
      system.terminate()
      Thread.sleep(500)
    }
  }

  def backOffOnStopCase(): Unit ={
    println(
      """
        |backoff onStop ---------------------------
      """.stripMargin)

    val system = ActorSystem("app")
    try{
      /**
       * Let's see if "hello" message is forwarded to the child
       * by the backoff supervisor onStop
       */
      implicit val futureTimeout: akka.util.Timeout = 1.second
      val backoffSupervisorActor = system.actorOf(Child.backOffOnStopProps, "child")
      Thread.sleep(100)

      backoffSupervisorActor ! "hello to backoff supervisor" //forwarded to child
      //[Child]: received hello to backoff supervisor

      /**
       * Now "Restart" the child with an exception from its receive method.
       * As with the default supervisory strategy, the `child` ActorRef remains valid. (i.e. incarnation kept same)
       */
      val child = Await.result(backoffSupervisorActor ? "get ref", 1.second).asInstanceOf[ActorRef]
      child ! "boom"
      Thread.sleep(2000)

      child ! "hello to child after normal exception"
      //[Child]: received hello to child after normal exception

      /**
       * Backoff Supervisor can still forward the message
       */
      backoffSupervisorActor ! "hello to backoffSupervisorActor after normal exception"
      //[Child]: received hello to backoffSupervisorActor after normal exception

      Thread.sleep(200)

      /**
       * PoisonPill causes the child actor to `Stop`, different from restart.
       * The `child` ActorRef incarnation gets updated.
       */
      child ! PoisonPill
      Thread.sleep(2000)

      child ! "hello to child ref after PoisonPill"
      //delivered to deadLetters

      /**
       * Backoff Supervisor can forward the message to its child with the new incarnation
       */
      backoffSupervisorActor ! "hello to backoffSupervisorActor after PoisonPill"
      //[Child]: received hello to backoffSupervisorActor after PoisonPill

      Thread.sleep(200)
    }
    finally{
      system.terminate()
      Thread.sleep(500)
    }
  }

  def backOffOnFailureCase(): Unit ={
    println(
      """
        |backoff onFailure ---------------------------
      """.stripMargin)

    val system = ActorSystem("app")
    try{
      /**
       * Let's see if "hello" message is forwarded to the child
       * by the backoff supervisor onFailure
       */
      implicit val futureTimeout: akka.util.Timeout = 1.second
      val backoffSupervisorActor = system.actorOf(Child.backOffOnFailureProps, "child")
      Thread.sleep(100)

      backoffSupervisorActor ! "hello to backoff supervisor" //forwarded to child
      //[Child]: received hello to backoff supervisor

      /**
       * Now "Stop" the child with an exception from its receive method.
       * You'll see the difference between "Restart" and "Stop" from here:
       */
      val child = Await.result(backoffSupervisorActor ? "get ref", 1.second).asInstanceOf[ActorRef]
      child ! "boom"
      Thread.sleep(2000)

      /**
       * Note that this is after normal exception, not after PoisonPill,
       * but child is completely "Stopped" and its ActorRef "incarnation" became obsolete
       *
       * So, the message to the `child` ActorRef is delivered to deadLetters
       */
      child ! "hello to child after normal exception"
      //causes delivery to deadLetter

      /**
       * Backoff Supervisor can still forward the message to the new child ActorRef incarnation
       */
      backoffSupervisorActor ! "hello to backoffSupervisorActor after normal exception"
      //[Child]: received hello to backoffSupervisorActor after normal exception

      /**
       * You can get a new ActorRef which represents the new incarnation
       */
      val newChildRef = Await.result(backoffSupervisorActor ? "get ref", 1.second).asInstanceOf[ActorRef]
      newChildRef ! "hello to new child ref after normal exception"
      //[Child]: received hello to new child ref after normal exception

      Thread.sleep(200)

      /**
       * No matter whether the supervisory strategy is default or backoff,
       * PoisonPill causes the actor to "Stop", not "Restart"
       */
      newChildRef ! PoisonPill
      Thread.sleep(3000)

      newChildRef ! "hello to new child ref after PoisonPill"
      //delivered to deadLetters

      Thread.sleep(200)
    }
    finally{
      system.terminate()
      Thread.sleep(500)
    }
  }

  def main(args: Array[String]): Unit ={
    defaultSuperVisorCase()
    backOffOnStopCase()
    backOffOnFailureCase()
  }
}
