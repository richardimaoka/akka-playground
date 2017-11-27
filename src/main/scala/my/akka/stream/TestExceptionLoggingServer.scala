package my.akka.stream

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.util.ByteString

import scala.concurrent.duration.Duration
//https://github.com/akka/akka/issues/23501
object TestExceptionLoggingServer {
  def ktosoExample(): Unit ={
    implicit val system = ActorSystem ()
    implicit val mat = ActorMaterializer ()

    try {
      /**
        * The following code given by ktoso no longer fails as https://github.com/akka/akka/pull/21801
        * changed FileSource so that it fails in prestart if there is a typo in the file name.
        */
      FileIO.fromPath(new File("TYPO HERE").toPath) //(new File("C:\\akka\\akka-2.3.7\\README").toPath)//
        //.log("hey1")
        .map(x => {
        println(x); x
      })
        .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1000))
        .map(_.utf8String)
        .map {
          line ⇒
            val entry = line.split(" ")
            val n = entry.head
            val time = entry.tail.head

            println(s"${
              n
            } ${
              Duration(time).toMicros
            }")
        }
        //.log("hey2")
        .runWith(Sink.ignore)

      //Source(List(1,0)).map(10/_).log("progress").runWith(Sink.ignore)
      Thread.sleep(1000)
    }

    finally {
      mat.shutdown()
      system.terminate()
    }
  }

  def anotherExample(): Unit ={
    implicit val system = ActorSystem ()
    implicit val mat = ActorMaterializer ()

    try {
      Source(-5 to 5)
        .map(1 / _)
        .runWith(Sink.ignore)
      Thread.sleep(1000)
    }

    finally {
      mat.shutdown()
      system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    //ktosoExample()
    anotherExample()
  }
}
