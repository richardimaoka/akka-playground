package my.akka.typed

import java.io.{PrintWriter, StringWriter}

import akka.serialization.{SerializationExtension, SerializerWithStringManifest}
import com.typesafe.config.{Config, ConfigFactory}
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

  def serializeTypedActorRef(config: Config): Unit = {
    import akka.typed.scaladsl._
    import akka.typed.scaladsl.adapter._

    val empty = Actor.immutable[String] { (_, msg) ⇒
      akka.typed.scaladsl.Actor.same
    }

    val system = akka.actor.ActorSystem("example", config)
    try {
      val original = system.spawn(empty, "empty")
      val serialization = SerializationExtension(system)

      val serializer = serialization.findSerializerFor(original)

      //Serializer for class akka.typed.internal.adapter.ActorRefAdapter = akka.serialization.JavaSerializer@7389af41
      println(s"Serializer for ${original.getClass} = " + serializer)

      // Turn it into bytes
      val bytes = serializer.toBinary(original)

      // Turn it back into an object
      val back = serialization.findSerializerFor(original) match {
        case s: SerializerWithStringManifest ⇒ s.fromBinary(bytes, s.manifest(original))
        case _ => serializer.fromBinary(bytes, manifest = None)
      }

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


  def main(args: Array[String]): Unit = {
    Wrap("displayConfig")(displayConfig)
    Wrap("sampleFromAkkaDoc")(sampleFromAkkaDoc)
    Wrap("serializerForUntypedActorRef")(serializerForUntypedActorRef)

    Wrap("typedWithJavaSerializer")(serializeTypedActorRef(
      ConfigFactory.parseString(
        """akka {
          |  actor {
          |    serialization-bindings {
          |      "akka.typed.ActorRef" = java
          |      "akka.typed.internal.adapter.ActorRefAdapter" = java
          |    }
          |  }
          |}
        """.stripMargin)
    ))
    /**
      *  This uses a config to override the serializers for typed ActorRef and ActorRefAdapter
      *  to use the Java Serializer ...
      *
      *  ... and gives the following exception since akka.typed.internal.adapter.ActorRefAdapter
      *  doesn't extend Java's Serializable interface
      *
      *  java.io.NotSerializableException: akka.typed.internal.adapter.ActorRefAdapter
      *        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1184)
      *        at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:348)
      *        at akka.serialization.JavaSerializer.$anonfun$toBinary$1(Serializer.scala:319)
      *        at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.java:12)
      *        at scala.util.DynamicVariable.withValue(DynamicVariable.scala:58)
      *        at akka.serialization.JavaSerializer.toBinary(Serializer.scala:319)
      *        at my.akka.typed.MySerializer$.main(MySerializer.scala:36)
      *        at my.akka.typed.MySerializer.main(MySerializer.scala)
      *        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
      *        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
      *        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
      */

    Wrap("typedWithMiscSerializer")(serializeTypedActorRef(ConfigFactory.parseString("")))
    /**
      *  This will use the default config as follows:
      *    # https://github.com/akka/akka/pull/23696/files
      *    # akka-typed/src/main/resources/reference.conf
      *    akka.actor {
      *      serializers {
      *        typed-misc = "akka.typed.cluster.internal.MiscMessageSerializer"
      *      }
      *      serialization-identifiers {
      *        "akka.typed.cluster.internal.MiscMessageSerializer" = 24
      *      }
      *      serialization-bindings {
      *        "akka.typed.ActorRef" = typed-misc
      *        "akka.typed.internal.adapter.ActorRefAdapter" = typed-misc
      *      }
      *    }
      *
      *  and produces the following output (i.e. successful serialization/deserialization) :
      *
      *    Serializer for class akka.typed.internal.adapter.ActorRefAdapter = akka.typed.cluster.internal.MiscMessageSerializer@387cd6cd
      *    (Actor[akka://example/user/empty#-205098054], ,class akka.typed.internal.adapter.ActorRefAdapter)
      *    (Actor[akka://example/user/empty#-205098054], ,class akka.typed.internal.adapter.ActorRefAdapter)
      */
  }
}
