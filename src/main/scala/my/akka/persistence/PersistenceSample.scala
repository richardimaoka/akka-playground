package my.akka.persistence

import akka.actor.{ActorSystem, ExtendedActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.persistence.journal.{EventAdapter, EventSeq, Tagged}
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.Set

case class Cmd(data: String)
case class CmdToTag(data: String)

case class Evt(data: String)
case class EvtToTag(data: String)

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

  override def toJournal(event: Any): Any = event match {
    case EvtToTag(data) =>
      println(s"TagEventAdapter: toJournal called for ${event}")
      Tagged(event, Set("aaa", "bbbb")) // identity
  }

  override def fromJournal(event: Any, manifest: String): EventSeq = {
    println(s"TagEventAdapter: fromJournal called for ${event} and manifest = ${manifest}")
    EventSeq.single(event) // identity
  }
}

class MyPersistentActor extends PersistentActor {
  var stateString: String = "initial state"

  override def persistenceId: String = "json-actor"
  //override def journalPluginId: String = "akka.persistence.journal.auto-json-store"

  override def receiveRecover: Receive = {
    case RecoveryCompleted ⇒
      println("receiveRecover: Recovery Completed")
    case Evt(s) ⇒
      println(s"receiveRecover: Recovering an event = Evt(${s})")
      stateString += ", " + s
      println(s"receiveRecover: current state = ${stateString}")
    case Tagged(EvtToTag(s), tags) ⇒
      println(s"receiveRecover: Recovering an event = EvtToTag(${s}), tags = ${tags}")
      stateString += ", " + s
      println(s"receiveRecover: current state = ${stateString}")
    case a: Any =>
      println(s"receiveRecover: received ${a}")
  }

  override def receiveCommand: Receive = {
    case Cmd(s) ⇒
      println(s"receiveCommand: Received Command Cmd(${s})")
      persist(Evt(s)) { e ⇒
        println(s"receiveCommand: Event = Evt(${e.data}) persisted")
        stateString += ", " + e.data
        println(s"receiveCommand: current state = ${stateString}")
      }
    case CmdToTag(s) ⇒
      println(s"receiveCommand: Received Command CmdToTag(${s})")
      persist(EvtToTag(s)) { e ⇒
        println(s"receiveCommand: Event = EvtToTag(${e.data}) persisted")
        stateString += ", " + e.data
        println(s"receiveCommand: current state = ${stateString}")
      }
    case _ =>
      throw new Exception("unexpected command received")
  }
}

object PersistenceSample {

  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.parseString("""
      |akka {
      |  actor.warn-about-java-serializer-usage = off
      |  persistence {
      |    journal {
      |      leveldb {
      |        native = off
      |        event-adapters {
      |          identical-adapter = "my.akka.persistence.MyIdenticalEventAdapter"
      |          tagging-adapter = "my.akka.persistence.MyTaggingEventAdapter"
      |        }
      |        event-adapter-bindings {
      |           "my.akka.persistence.Evt" = identical-adapter
      |           "my.akka.persistence.EvtToTag" = tagging-adapter
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

    val system = ActorSystem("aaaaa", config)
//    println("akka.persistence.snapshot-store.local = " +
//      system.settings.config.getConfig("akka.persistence.snapshot-store.local").root().render())

    try {
      val props = Props(new MyPersistentActor)

      val p1 = system.actorOf(props, "p1")

//      p1 ! Cmd("abc")
//      p1 ! Cmd("def")
      p1 ! CmdToTag("123456789")
      p1 ! CmdToTag("098765432")
      p1 ! "kaboom"

      Thread.sleep(1000)

    } finally {
      system.terminate()
    }

  }
}
