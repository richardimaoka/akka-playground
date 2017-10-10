package my.akka.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ConstantFun
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.util.control.NoStackTrace

class FlowConcatSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit var system: ActorSystem = ActorSystem("FlowConcatSpec")
  implicit var materializer: Materializer = ActorMaterializer()
  implicit def dispatcher = system.dispatcher

  def logit(value: => Any): Unit = info(value.toString)

  "FlowConcatSpec" should {
    "work in a happy case" in {
      // This publisher sends Source[Int, NotUsed]
      val publisher = TestPublisher.manualProbe[Source[Int, NotUsed]]()
      val subscriber = TestSubscriber.manualProbe[Int]()
      Source.fromPublisher(publisher)
        // As the Source sends Source[Int, NotUsed],
        // identity func will emit Source[Int, NotUsed]
        .flatMapConcat(x => x)
        .to(Sink.fromSubscriber(subscriber)).run()

      val upstream = publisher.expectSubscription()
      val downstream = subscriber.expectSubscription()
      downstream.request(1000)

      val substreamPublisher = TestPublisher.manualProbe[Int]()
      val substreamFlow = Source.fromPublisher(substreamPublisher)
      upstream.expectRequest()
      upstream.sendNext(substreamFlow)
      val subUpstream = substreamPublisher.expectSubscription()

      val testException = new Exception("test") with NoStackTrace
      upstream.sendError(testException)
      subscriber.expectError(testException)
      subUpstream.expectCancellation()
    }

    "emit elements in order" in {
      val subscriber = TestSubscriber.manualProbe[Int]()
      val sink = Source(1 to 2)
        .flatMapConcat(i => Source(List.fill(3)(i)))
        .runWith(TestSink.probe[Int](system))

      sink.requestNext(1)
      sink.requestNext(1)
      sink.requestNext(1)
      sink.requestNext(2)
      sink.requestNext(2)
      sink.requestNext(2)
    }

  }

}
