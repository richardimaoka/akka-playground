package my.akka.stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorMaterializer, Materializer}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class FlowFlattenMergeSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit var system: ActorSystem = ActorSystem("FlowFlattenMergeSpec")
  implicit var materializer: Materializer = ActorMaterializer()
  implicit def dispatcher = system.dispatcher

  def logit(value: => Any): Unit = info(value.toString)

  val toSeq = Flow[Int].grouped(1000).toMat(Sink.head)(Keep.right)
  val toSet = toSeq.mapMaterializedValue(_.map(_.toSet))

  "FlowFlattenMergeSpec" should {
    "work in a happy case" in {
      val set = Source(List(Source(0 to 9), Source(10 to 19), Source(20 to 29), Source(30 to 40)))
        .flatMapMerge(4, identity)
        .runWith(toSet)

      logit(Await.result(set, 1.seconds))
    }

    "emit elements in order" in {
      val sink = Source(1 to 2)
        .flatMapMerge(2, i => Source(List.fill(10)(i)))
        .runWith(TestSink.probe[Int](system))

      //2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, ...
      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())

      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())

      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())

      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())
      logit(sink.requestNext())
    }

    "emit elements in order 2" in {
      val sink = Source(List(Source(0 to 9), Source(10 to 19), Source(20 to 29), Source(30 to 40)))
        .flatMapMerge(4, identity)
        .runWith(TestSink.probe[Int](system))

      logit(sink.requestNext()) //30
      logit(sink.requestNext()) //20
      logit(sink.requestNext()) //10
      logit(sink.requestNext()) //0
      logit(sink.requestNext()) //31
      logit(sink.requestNext()) //21
      logit(sink.requestNext()) //11
      logit(sink.requestNext()) //1
      logit(sink.requestNext()) //32
      logit(sink.requestNext()) //22
      logit(sink.requestNext()) //12
      logit(sink.requestNext()) //2
      logit(sink.requestNext()) //33
      logit(sink.requestNext()) //23
      logit(sink.requestNext()) //13
      logit(sink.requestNext()) //3
      //...
    }
  }

}
