package my.akka.stream

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Attributes, IOResult}
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.util.ByteString

import scala.concurrent.duration.Duration
import scala.util.Failure

object TestExceptionLoggingServer extends App {
  implicit val system = ActorSystem ()
  implicit val mat = ActorMaterializer ()

  try{
    FileIO.fromPath (new File("C:\\akka\\akka-2.3.7\\README").toPath)//(new File ("TYPO HERE").toPath) //
//      .map( x => {println(x); x})
//      .log ("progress1")
//      .via (Framing.delimiter (ByteString ("\n"), maximumFrameLength = 1000))
//      .log ("progress2")
//      .map (_.utf8String)
//      .log ("progress3")
//      .map {
//        line ⇒
//          val entry = line.split (" ")
//          val n = entry.head
//          val time = entry.tail.head
//
//          println (s"${
//            n
//          } ${
//            Duration (time).toMicros
//          }")
//      }
      .log ("progress4") runWith Sink.foreach(println)

    //Source(List(1,0)).map(10/_).log("progress").runWith(Sink.ignore)

  } finally {
    mat.shutdown()
    system.terminate()
  }

}
