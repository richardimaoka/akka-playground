package my.akka.typed

import scala.io.StdIn

import akka.typed.ActorRef
import akka.typed.ActorSystem
import akka.typed.Behavior
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.ActorContext


object Worker2 {
  def workerBehavior: Behavior[MutableRoundRobin.Job] = Actor.empty
}

object MutableRoundRobin {
  sealed trait Command
  final case class Job(payload: String) extends Command

  def roundRobinBehavior[T](numberOfWorkers: Int, worker: Behavior[T]): Behavior[T] =
    Actor.mutable[T](ctx => new MutableRoundRobin(ctx, numberOfWorkers, worker))
}

class MutableRoundRobin[T](ctx: ActorContext[T], numberOfWorkers: Int, worker: Behavior[T]) extends Actor.MutableBehavior[T] {
  private var index = 0L
  private val workers = (1 to numberOfWorkers).map { n =>
    ctx.spawn(worker, s"worker-$n")
  }

  override def onMessage(msg: T): Behavior[T] = {
    workers((index % workers.size).toInt) ! msg
    index += 1
    this
  }
}

object MutableRoundRobinApp {

  def main(args: Array[String]): Unit = {
    val root = Actor.deferred[Nothing] { ctx =>
      import MutableRoundRobin._
      import Worker2._
      val workerPool = ctx.spawn(roundRobinBehavior(numberOfWorkers = 3, workerBehavior), "workerPool")
      (1 to 20).foreach { n =>
        workerPool ! Job(n.toString)
      }

      Actor.empty
    }
    val system = ActorSystem[Nothing](root, "RoundRobin")
    try {
      // Exit the system after ENTER is pressed
      StdIn.readLine()
    } finally {
      system.terminate()
    }
  }
}