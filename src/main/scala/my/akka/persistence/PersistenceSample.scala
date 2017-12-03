package my.akka.persistence

import akka.actor.{ActorSystem, ExtendedActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory

case class Cmd(data: String)
case class Evt(data: String)

class MyEventAdapter(system: ExtendedActorSystem) extends EventAdapter {
  override def manifest(event: Any): String =
    "" // when no manifest needed, return ""

  override def toJournal(event: Any): Any =
    event // identity

  override def fromJournal(event: Any, manifest: String): EventSeq =
    EventSeq.single(event) // identity
}

class MyPersistentActor extends PersistentActor {
  var stateString: String = ""

  override def persistenceId: String = "json-actor"
  override def journalPluginId: String = "akka.persistence.journal.auto-json-store"

  override def receiveRecover: Receive = {
    case RecoveryCompleted ⇒
      println("Recovery Completed")
    case Evt(s)                ⇒
      println(s"Recovering an event = Evt(${s})")
      stateString = ", " + s
  }

  override def receiveCommand: Receive = {
    case Cmd(s) ⇒
      println(s"Received Command Cmd(${s}")
      persist(Evt(s)) { e ⇒
        println(s"Event = Evt(${s}) persisted")
      }
  }
}

object PersistenceSample {

  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.parseString("""
      |akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      |akka.persistence.journal {
      |  auto-json-store {
      |    class = "akka.persistence.journal.inmem.InmemJournal" # reuse inmem, as an example
      |  }
      |}
    """.stripMargin
    )

    val system = ActorSystem("aaaaa", config)
//    println("akka.persistence.snapshot-store.local = " +
//      system.settings.config.getConfig("akka.persistence.snapshot-store.local").root().render())

    try {
      val props = Props(new MyPersistentActor)

      val p1 = system.actorOf(props)

      p1 ! Cmd("abc")
      p1 ! Cmd("def")

      Thread.sleep(1000)

    } finally {
      system.terminate()
    }

  }
}
