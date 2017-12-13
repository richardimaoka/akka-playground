package my.akka.typed

import java.io.{PrintWriter, StringWriter}

import akka.serialization.SerializationExtension
import com.typesafe.config.ConfigFactory
import my.akka.wrapper.Wrap

class EmptyActor extends akka.actor.Actor {
  def receive = akka.actor.Actor.emptyBehavior
}

object MySerializer {
  def sampleFromAkkaDoc(): Unit = {
    import akka.actor._

    val config = ConfigFactory.parseString(
      """akka {
        |  actor {
        |    serialize-messages = on
        |  }
        |}
      """.stripMargin)

    val system = ActorSystem("example", config)
    try {
      // Get the Serialization Extension
      val serialization = SerializationExtension(system)
      // Have something to serialize
      val original = "woohoo"
      // Find the Serializer for it
      val serializer = serialization.findSerializerFor(original)
      println(s"Serializer for ${original.getClass} = " + serializer)
      // Turn it into bytes
      val bytes = serializer.toBinary(original)
      // Turn it back into an object
      val back = serializer.fromBinary(bytes, manifest = None)
      // Voilá!
      println(back, " ", back.getClass)
      println(original, " ", original.getClass)

    } finally {
      system.terminate()
    }
  }

  def serializerForUntypedActorRef(): Unit ={
    import akka.actor._

    val config = ConfigFactory.parseString(
      """akka {
        |  actor {
        |    serialize-messages = on
        |  }
        |}
      """.stripMargin)

    val system = ActorSystem("example", config)

    try {
      // Get the Serialization Extension
      val serialization = SerializationExtension(system)
      val original = system.actorOf(Props[EmptyActor])
      val serializer = serialization.findSerializerFor(original)
      println(s"Serializer for ${original.getClass} = " + serializer)
      // Turn it into bytes
      val bytes = serializer.toBinary(original)
      // Turn it back into an object
      val back = serializer.fromBinary(bytes, manifest = None)
      // Voilá!
      println(back, " ", back.getClass)
      println(original, " ", original.getClass)

    } catch {
      case t: Throwable => {
        val sw = new StringWriter
        t.printStackTrace(new PrintWriter(sw))
        println(t.getMessage)
        println(sw)
      }
    } finally {
      system.terminate()
    }
  }

  def serializerForTypedActorRef(): Unit ={
    import akka.typed.scaladsl._
    import akka.typed.scaladsl.adapter._

    val greeter = Actor.immutable[String] { (_, msg) ⇒
      println(s"typed actor received a message ${msg}!")
      akka.typed.scaladsl.Actor.same
    }

    val config = ConfigFactory.parseString(
      """akka {
        |  actor {
        |    serialization-bindings {
        |      "akka.typed.ActorRef" = java
        |      "akka.typed.internal.adapter.ActorRefAdapter" = java
        |    }
        |  }
        |}
      """.stripMargin)

    val system = akka.actor.ActorSystem("example", config)
    try {
      val original = system.spawn(greeter, "greeter")
      val serialization = SerializationExtension(system)
      val serializer = serialization.findSerializerFor(original)

      println(s"Serializer for ${original.getClass} = " + serializer)
      /**
        * As in https://github.com/akka/akka/pull/23696/files
        * Serializer for class akka.typed.internal.adapter.ActorRefAdapter = akka.typed.cluster.internal.MiscMessageSerializer@37f16b1b
        */

      // Turn it into bytes
      val bytes = serializer.toBinary(original)
      // Turn it back into an object
      val back = serializer.fromBinary(bytes, manifest = None)
      // Voilá!
      println(back, " ", back.getClass)
      println(original, " ", original.getClass)
    } finally {
      system.terminate()
    }
  }

  def displayConfig(): Unit ={
    import akka.actor._

    val config = ConfigFactory.parseString(
      """akka {
        |  actor {
        |    serialize-messages = on
        |  }
        |}
      """.stripMargin)
    val system = ActorSystem("example", config)
    try {
      val path = "akka.actor.serializers"
      println(path)
      println(system.settings.config.getConfig(path).root.render)

      val bindingPath = "akka.actor.serialization-bindings"
      println(bindingPath)
      println(system.settings.config.getConfig(bindingPath).root.render)
    } finally {
      system.terminate()
    }
  }


  def main(args: Array[String]): Unit = {
    Wrap("displayConfig")(displayConfig)
    Wrap("sampleFromAkkaDoc")(sampleFromAkkaDoc)
    Wrap("serializerForUntypedActorRef")(serializerForUntypedActorRef)
    Wrap("serializerForTypedActorRef")(serializerForTypedActorRef)
  }
}
