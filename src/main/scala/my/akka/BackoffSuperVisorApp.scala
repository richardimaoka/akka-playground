package my.akka

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.{Backoff, BackoffSupervisor, ask}
import my.akka.Parent.GetChildRef

import scala.concurrent.Await
import scala.concurrent.duration._

class Parent extends Actor {
  println(s"[Parent]  ${System.currentTimeMillis()}: created.         (path = ${this.self.path},         instance = ${this})")

  var children: Map[Int, ActorRef] = Map()

  for(i <- 1 to 2)
    children = children.updated(i, context.actorOf(Child.props(this.toString, i), "child-" + i))

  def receive = {
    case "boom" => throw new Exception("kaboom")
    case GetChildRef(i) =>
      val childRef = children(i)
      sender() ! childRef
  }

  override def preStart(): Unit = {
    println(s"[Parent]  ${System.currentTimeMillis()}: preStart called. (path = ${this.self.path},         instance = ${this})")
    super.preStart()
  }

  override def postStop(): Unit = {
    println(s"[Parent]  ${System.currentTimeMillis()}: postStop called. (path = ${this.self.path},         instance = ${this})")
    super.postStop()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    println(s"[Parent]  ${System.currentTimeMillis()}: preRestart called with (reason = $reason, message = $message). (path = ${this.self.path}, instance = ${this})")
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    println(s"[Parent]  ${System.currentTimeMillis()}: postRestart called with (reason = $reason). (path = ${this.self.path}, instance = ${this})")
    super.postRestart(reason)
  }
}

object Parent {
  case class GetChildRef(id: Int)
}

class Child(parent: String, id: Int) extends Actor {
  println(s"[Child($id)]${System.currentTimeMillis()}: created.         (path = ${this.self.path}, instance = ${this}, parent = ${parent})")

  def receive = {
    case a: Any =>
      println(s"[Child($id)]${System.currentTimeMillis()}: received ${a}")
  }

  override def preStart(): Unit = {
    println(s"[Child($id)]${System.currentTimeMillis()}: preStart called. (path = ${this.self.path}, instance = ${this}, parent = ${parent})")
    super.preStart()
  }

  override def postStop(): Unit = {
    println(s"[Child($id)]${System.currentTimeMillis()}: postStop called. (path = ${this.self.path}, instance = ${this}), parent = ${parent}")
    super.postStop()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    println(s"[Child($id)]${System.currentTimeMillis()}: preRestart called with ($reason, $message). (path = ${this.self.path}, instance = ${this}), parent = ${parent}")
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    println(s"[Child($id)]${System.currentTimeMillis()}: postRestart called with ($reason). (path = ${this.self.path}, instance = ${this}), parent = ${parent}")
    super.postRestart(reason)
  }
}

object Child {
  def props(parentInstanceName: String, id: Int): Props
    = Props(new Child(parentInstanceName, id))

  def backOffProps(parentInstanceName: String, id: Int): Props
    = BackoffSupervisor.props(
    Backoff.onFailure(
      Child.props(parentInstanceName, id),
      childName = "myEcho",
      minBackoff = 3.seconds,
      maxBackoff = 30.seconds,
      randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
    ))
}

object BackoffSuperVisorApp {
  def main(args: Array[String]): Unit ={
    val system = ActorSystem("app")
    try{
      implicit val futureTimeout: akka.util.Timeout = 1.second
      val parent = system.actorOf(Props[Parent], "parent")
      Thread.sleep(100)
      val child1 = Await.result(parent ? GetChildRef(1), 1.second).asInstanceOf[ActorRef]
      child1 ! "hello"
      //[Child(1)]1505661586660: received hello
      parent ! "boom"
      Thread.sleep(200)

      child1 ! "hello"
      /**
       * This causes delivery to deadLetter, since the "incarnation" of ActorRef child1 is updated
       * after the parent stopped child upon "kaboom" exception
       *
       *   [akka://app/user/parent/child-1] Message [java.lang.String] without sender to Actor[akka://app/user/parent/child-1#-767539042]
       *   was not delivered. [1] dead letters encountered.
       *
       * An incarnation is tied to an ActorRef, not its internal actor instance,
       * and the same incarnation means "you can keep using the same ActorRef"
       */
      parent ! "boom"
      parent ! PoisonPill
      Thread.sleep(200)
      parent ! "boom"
    }
    finally{
      system.terminate()
    }
  }
}
