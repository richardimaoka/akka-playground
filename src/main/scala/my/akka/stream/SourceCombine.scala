package my.akka.stream

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Keep, Merge, Sink, Source}

object SourceCombine {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  def main(args: Array[String]): Unit = {
    val source1 = Source.queue(2000, OverflowStrategy.backpressure)
    val source2 = Source.queue(2000, OverflowStrategy.backpressure)

    val source = Source.combine(source1, source2)(Merge(_)).toMat(Sink.ignore)(Keep.left).run()

    println(source)

  }
}
