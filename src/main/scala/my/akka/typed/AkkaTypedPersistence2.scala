package my.akka.typed

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.persistence.journal.{EventAdapter, EventSeq, Tagged}
import akka.typed.persistence.scaladsl.PersistentActor
import akka.typed.persistence.scaladsl.PersistentActor.{Actions, Persist}
import akka.typed.scaladsl.Actor
import akka.typed.{Behavior, Terminated}
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.Set

class MyIdenticalEventAdapter(system: ExtendedActorSystem) extends EventAdapter {
  override def manifest(event: Any): String =
    "mani1" // when no manifest needed, return ""

  override def toJournal(event: Any): Any = {
    println(s"EventAdapter  : toJournal called for ${event}")
    event // identity
  }

  override def fromJournal(event: Any, manifest: String): EventSeq = {
    println(s"EventAdapter  : fromJournal called for ${event} and manifest = ${manifest}")
    EventSeq.single(event) // identity
  }
}

class MyTaggingEventAdapter(system: ExtendedActorSystem) extends EventAdapter {
  override def manifest(event: Any): String =
    "mani1" // when no manifest needed, return ""

  override def toJournal(event: Any): Any = {
    println(s"TagEventAdapter: toJournal called for ${event}")
    Tagged(event, Set("aaa", "bbbb")) // identity
  }

  override def fromJournal(event: Any, manifest: String): EventSeq = {
    println(s"TagEventAdapter: fromJournal called for ${event} and manifest = ${manifest}")
    EventSeq.single(event) // identity
  }
}

sealed trait Command
case class CommandToTag(str: String) extends Command
case class CommandNotToTag(str: String) extends Command

sealed trait Event
case class EventToTag(str: String) extends Event
case class EventNotToTag(str: String) extends Event

case class State(str: String)

object MyPersistentActor2 {


  private val actions: Actions[Command, Event, State] =
    Actions{ (ctx, cmd, state) =>
      cmd match {
        case CommandToTag(str) =>
          val event = EventToTag(str)
          Persist(event).andThen{ state =>
            println(s"persisted ${event}")
          }
        case CommandNotToTag(str) =>
          val event = EventNotToTag(str)
          Persist(event).andThen{ state =>
            println(s"persisted ${event}")
          }
      }
    }

  private def applyEvent(event: Event, state: State): State =
    event match {
      case EventToTag(str) if (str == "kaboom") =>
        throw new Exception("kaboom")
      case EventToTag(str) =>
        println(s"applying event ${event}")
        State(state.str + ", " + str)
      case EventNotToTag(str) =>
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

//      persistActorRef ! CommandNotToTag("abc")
//      persistActorRef ! CommandNotToTag("def")
//      persistActorRef ! CommandNotToTag("ghi")
      persistActorRef ! CommandToTag("jkl")
      persistActorRef ! CommandToTag("mno")

      Actor.immutable[akka.NotUsed] {
        (_, _) => Actor.unhandled
      } onSignal {
        case (ctx, Terminated(ref)) =>
          Actor.stopped
      }
    }

  def main(args: Array[String]): Unit ={
    val config = ConfigFactory.parseString("""
      |akka {
      |  actor.warn-about-java-serializer-usage = off
      |  persistence {
      |    journal {
      |      leveldb {
      |        native = off
      |        event-adapters {
      |          identical-adapter = "my.akka.typed.MyIdenticalEventAdapter"
      |          tagging-adapter   = "my.akka.typed.MyTaggingEventAdapter"
      |        }
      |
      |         event-adapter-bindings {
      |          "my.akka.typed.EventNotToTag" = identical-adapter
      |          "my.akka.typed.EventToTag"    = tagging-adapter
      |        }
      |      }
      |      plugin = "akka.persistence.journal.leveldb"
      |      auto-start-journals = ["akka.persistence.journal.leveldb"]
      |    }
      |    snapshot-store {
      |      plugin = "akka.persistence.snapshot-store.local"
      |      auto-start-snapshot-stores = ["akka.persistence.snapshot-store.local"]
      |    }
      |  }
      |}""".stripMargin
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