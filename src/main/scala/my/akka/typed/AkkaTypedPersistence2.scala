package my.akka.typed

import akka.typed.persistence.scaladsl.PersistentActor
import akka.typed.{Behavior, Terminated}
import akka.actor.ActorSystem
import akka.typed.persistence.scaladsl.PersistentActor.{Actions, Persist}
import akka.typed.scaladsl.Actor
import com.typesafe.config.ConfigFactory
import my.akka.typed.MyPersistentActor2.Command

object MyPersistentActor2 {
  case class Command(str: String)
  case class Event(str: String)
  case class State(str: String)

  private val actions: Actions[Command, Event, State] =
    Actions{ (ctx, cmd, state) =>
      cmd match {
        case Command(str) =>
          val event = Event(str)
          Persist(event).andThen{ state =>
            println(s"persisted ${event}")
          }
      }
    }

  private def applyEvent(event: Event, state: State): State =
    event match {
      case Event(str) =>
        println(s"applying event ${event}")
        State(state.str + ", " + str)
    }

  def behavior: Behavior[Command] =
    PersistentActor.immutable[Command, Event, State](
      persistenceId = "pppid",
      initialState = State("initial state"),
      actions = actions,
      applyEvent = applyEvent
    )
}

object AkkaTypedPersistence2 {
  def mainBehavior: Behavior[akka.NotUsed] =
    Actor.deferred { ctx =>
      val persistActorRef = ctx.spawn(MyPersistentActor2.behavior, "persistttt")
      persistActorRef ! Command("abc")
      persistActorRef ! Command("def")
      persistActorRef ! Command("ghi")
      persistActorRef ! Command("jkl")
      persistActorRef ! Command("mno")

      Actor.immutable[akka.NotUsed] {
        (_, _) => Actor.unhandled
      } onSignal {
        case (ctx, Terminated(ref)) =>
          Actor.stopped
      }
    }

  def main(args: Array[String]): Unit ={
    val config = ConfigFactory.parseString("""
      |akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      |akka.persistence.journal.plugin = "akka.persistence.journal.inmem" # reuse inmem, as an example
      """.stripMargin
    )

    val system = ActorSystem("system", config)
    try {
      import akka.typed.scaladsl.adapter._
      system.spawn(mainBehavior, "guardian")
      Thread.sleep(1000)
    } finally {
      system.terminate()
    }
  }
}