package my.akka.typed

import akka.typed._
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.Actor.same

import scala.io.StdIn


object Child2 {
  val behavior: Behavior[String] = Actor.immutable[String] { (ctx, msg) =>
    println(s"received msg = ${msg}")
    same
  }
}

object AkkaTypedExamples2 {
  def main(args: Array[String]): Unit = {
    val main: Behavior[akka.NotUsed] = Actor.deferred { ctx =>
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
}