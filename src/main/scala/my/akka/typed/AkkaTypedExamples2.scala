package my.akka.typed

import akka.NotUsed
import akka.typed._
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.Actor.same
import my.akka.wrapper.Wrap

import scala.io.StdIn


object Child2 {
  val behavior: Behavior[String] = Actor.immutable[String] { (ctx, msg) =>
    println(s"received msg = ${msg}")
    same
  }
}

object ChildAppendingString {
  //State can be held as a parameter to the def which returns behavior
  def behavior(currentString: String = ""): Behavior[String] = Actor.immutable[String] { (ctx, msg) =>
    val str = msg + ", " + currentString
    println(s"current string = ${str}")
    behavior(str)
  }
}

object AkkaTypedExamples2 {
  def runChild2(): Unit ={
    val main: Behavior[akka.NotUsed] = Actor.deferred { ctx =>
      /***
       * This code block is executed when it is used as the guardian for the ActorSystem,
       * since the code block is the "factory"
       * (i.e.) child gets messages "hey1" ~ "hey7" received
       *
       *  def deferred[T](***factory***: ActorContext[T] ⇒ Behavior[T]): Behavior[T] =
       *    Behavior.DeferredBehavior( ***factory*** )
       *
       *
       * On the other hand, Actor.immutable does not execute the code block in that situation,
       * since the code block passed to Actor.immutable is just an onMessage callback.
       *
       *   def immutable[T](***onMessage***: (ActorContext[T], T) ⇒ Behavior[T]): Immutable[T] =
       *     new Immutable(***onMessage***)
       */
      val child = ctx.spawn(Child2.behavior, "child")
      child ! "hey 1"
      child ! "hey 2"
      child ! "hey 3"
      child ! "hey 4"
      child ! "hey 5"
      child ! "hey 6"
      child ! "hey 7"

      Actor.immutable[akka.NotUsed] {
        (_, _) ⇒ Actor.unhandled
      } onSignal {
        case (ctx, Terminated(ref)) ⇒
          Actor.stopped
      }

      //same
      // [ERROR] [11/29/2017 00:30:27.418] [AkkaTypedExamples2-akka.actor.default-dispatcher-3] [akka://AkkaTypedExamples2/user] cannot use Same as initial behavior
      //  java.lang.IllegalArgumentException: cannot use Same as initial behavior
      //  at akka.typed.Behavior$.validateAsInitial(Behavior.scala:239)
      //  at akka.typed.internal.SupervisionMechanics.create(SupervisionMechanics.scala:73)
      //  at akka.typed.internal.SupervisionMechanics.processSignal(SupervisionMechanics.scala:54)
      //  at akka.typed.internal.SupervisionMechanics.processSignal$(SupervisionMechanics.scala:48)
      //  at akka.typed.internal.ActorCell.processSignal(ActorCell.scala:71)
      //  at akka.typed.internal.ActorCell.liftedTree1$1(ActorCell.scala:432)
      //  at akka.typed.internal.ActorCell.processAllSystemMessages(ActorCell.scala:432)
      //  at akka.typed.internal.ActorCell.process$1(ActorCell.scala:285)
      //  at akka.typed.internal.ActorCell.run(ActorCell.scala:298)
      //  at akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinTask.exec(ForkJoinExecutorConfigurator.scala:43)
      //  at akka.dispatch.forkjoin.ForkJoinTask.doExec(ForkJoinTask.java:260)
      //  at akka.dispatch.forkjoin.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1339)
      //  at akka.dispatch.forkjoin.ForkJoinPool.runWorker(ForkJoinPool.java:1979)
      //  at akka.dispatch.forkjoin.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:107)
    }

    val system = ActorSystem[akka.NotUsed](main, "AkkaTypedExamples2")
    try {
      // Exit the system after ENTER is pressed
      StdIn.readLine()
    } finally {
      system.terminate()
    }
  }

  def runChildAppendingStringNotWorking(): Unit = {
    val main: Behavior[akka.NotUsed] = Actor.immutable[NotUsed] { (ctx, msg) =>
      /***
       * Actor.immutable does not execute the code block in that situation,
       * since the code block passed to Actor.immutable is just an onMessage callback.
       *
       *   def immutable[T](***onMessage***: (ActorContext[T], T) ⇒ Behavior[T]): Immutable[T] =
       *     new Immutable(***onMessage***)
       *
       * So this entire runChildAppendingStringNotWorking() just does NOTHING.
       */
      val child = ctx.spawn(ChildAppendingString.behavior(""), "child")
      child ! "hey 1"
      child ! "hey 2"
      child ! "hey 3"
      child ! "hey 4"
      child ! "hey 5"
      child ! "hey 6"
      child ! "hey 7"
      same
    }

    val system = ActorSystem[akka.NotUsed](main, "AkkaTypedExamples2B")
    try {
      // Exit the system after ENTER is pressed
      StdIn.readLine()
    } finally {
      system.terminate()
    }
  }

  def runChildAppendingString(): Unit = {
    val main: Behavior[akka.NotUsed] = Actor.deferred[NotUsed] { ctx =>
      val child = ctx.spawn(ChildAppendingString.behavior(""), "child")
      child ! "hey 1"
      child ! "hey 2"
      child ! "hey 3"
      child ! "hey 4"
      child ! "hey 5"
      child ! "hey 6"
      child ! "hey 7"

      Actor.immutable[akka.NotUsed] {
        (_, _) ⇒ Actor.unhandled
      } onSignal {
        case (ctx, Terminated(ref)) ⇒
          Actor.stopped
      }
    }

    val system = ActorSystem[akka.NotUsed](main, "AkkaTypedExamples2C")
    try {
      // Exit the system after ENTER is pressed
      StdIn.readLine()
    } finally {
      system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    Wrap("runChild2")(runChild2)
    Wrap("runChildAppendingStringNotWorking")(runChildAppendingStringNotWorking)
    Wrap("runChildAppendingStringNotWorking")(runChildAppendingString)
  }
}
