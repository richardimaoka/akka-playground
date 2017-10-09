package my.akka.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorMaterializer, Materializer, SubstreamCancelStrategy}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class SplitWhenSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit var system: ActorSystem = ActorSystem("SplitWhenSpec")
  implicit var materializer: Materializer = ActorMaterializer()
  implicit def dispatcher = system.dispatcher

  def logit(value: => Any): Unit = info(value.toString)

  "splitWhen" should {
    "work" in {
      Source(1 to 10)
        .splitWhen(SubstreamCancelStrategy.drain)(_ == 3)
        .map(x => {logit(s"${x}"); x})
        .concatSubstreams
        .runWith(Sink.ignore)
    }

    "splits a subflow when the predicate returns true" in {
      val sinkPublisher = Source(1 to 10)
        .splitWhen(SubstreamCancelStrategy.drain)(_ == 5)
        .prefixAndTail(1)
        .concatSubstreams
        .runWith(Sink.asPublisher(false))

      val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
      sinkPublisher.subscribe(subscriber)
      val subscription = subscriber.expectSubscription()

      subscription.request(1)
      val (_, tailFlow1) = subscriber.expectNext()
      val tailSink1 = tailFlow1.runWith(TestSink.probe[Int])

      tailSink1.requestNext(2)
      tailSink1.requestNext(3)
      tailSink1.requestNext(4)
      //This request(1) is important - if you omit this, you will not get tailFlow2, nor the element 6.
      //Also, this request(1) is required so that tailSink1.expectComplete() passes, as in FlowSplitWhenSpec in akka repo
      tailSink1.request(1)
      tailSink1.expectComplete()

      subscription.request(1)
      val (_, tailFlow2) = subscriber.expectNext()
      val tailSink2 = tailFlow2.runWith(TestSink.probe[Int])
      tailSink2.requestNext(6)
      tailSink2.requestNext(7)
      tailSink2.requestNext(8)
      tailSink2.requestNext(9)
      tailSink2.requestNext(10)
    }

    "does not split until the flow if the predicate is not met" in {
      val sinkPublisher = Source(1 to 10)
        .splitWhen(SubstreamCancelStrategy.drain)(_ == 5)
        .prefixAndTail(1)
        .concatSubstreams
        .runWith(Sink.asPublisher(false))

      val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
      sinkPublisher.subscribe(subscriber)
      val subscription = subscriber.expectSubscription()

      subscription.request(1)
      val (_, tailFlow1) = subscriber.expectNext()
      //tailFlow2 cannot be obtained since the element == 5 did not ariive from the upstream
      subscriber.expectNoMsg(100.milliseconds)
    }

    "does not split until the flow if the predicate is not met 2" in {
      val sinkPublisher = Source(1 to 10)
        .splitWhen(SubstreamCancelStrategy.drain)(_ == 5)
        .prefixAndTail(1)
        .concatSubstreams
        .runWith(Sink.asPublisher(false))

      val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
      sinkPublisher.subscribe(subscriber)
      val subscription = subscriber.expectSubscription()

      subscription.request(1)
      val (_, tailFlow1) = subscriber.expectNext()
      val tailSink1 = tailFlow1.runWith(TestSink.probe[Int])

      tailSink1.requestNext(2)
      tailSink1.requestNext(3)
      //tailFlow2 cannot be obtained since the element == 5 did not ariive from the upstream
      subscriber.expectNoMsg(100.milliseconds)
    }

    "splits a subflow when first element is split-by" in {
      val sinkPublisher = Source(1 to 5)
        .splitWhen(SubstreamCancelStrategy.drain)(_ == 1)
        .prefixAndTail(1)
        .concatSubstreams
        .runWith(Sink.asPublisher(false))

      val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
      sinkPublisher.subscribe(subscriber)
      val subscription = subscriber.expectSubscription()

      subscription.request(1)
      val (_, tailFlow1) = subscriber.expectNext()
      val tailSink1 = tailFlow1.runWith(TestSink.probe[Int])

      tailSink1.requestNext(2)
      tailSink1.requestNext(3)
      tailSink1.requestNext(4)
      tailSink1.requestNext(5)
      tailSink1.request(1)
      tailSink1.expectComplete()

      subscriber.expectComplete()
    }

    "support cancelling a substream" in {
      val sinkPublisher = Source(1 to 10)
        .splitWhen(SubstreamCancelStrategy.drain)(_ == 5)
        .prefixAndTail(1)
        .concatSubstreams
        .runWith(Sink.asPublisher(false))

      val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
      sinkPublisher.subscribe(subscriber)
      val subscription = subscriber.expectSubscription()

      subscription.request(1)
      val (_, tailFlow1) = subscriber.expectNext()
      val tailSink1 = tailFlow1.runWith(TestSink.probe[Int])
      tailSink1.cancel()

      subscription.request(1)
      val (_, tailFlow2) = subscriber.expectNext()
      val tailSink2 = tailFlow2.runWith(TestSink.probe[Int])

      // Somehow if you replace `request(), then expectNext` with requestNext,
      // it doesn't work because OnSubScribe and/or OnComplete messages arrive.
      // Even more weird things are:
      //   * when doing expectComplete(), onSubscribe arrives
      //   * when doing expectSubscriptin, onComplete arrives .... ┐(´-｀)┌
      tailSink2.request(5)
      tailSink2.expectNext(6)
      tailSink2.expectNext(7)
      tailSink2.expectNext(8)
      tailSink2.expectNext(9)
      tailSink2.expectNext(10)
      //Also, this request(1) is required so that tailSink2.expectComplete() passes, as in FlowSplitWhenSpec in akka repo
      tailSink2.request(1)
      tailSink2.expectComplete()
    }
  }

  "support cancelling the master stream and active substreams from prefixAndTail work after cancellation of master" in {
    val sinkPublisher = Source(1 to 10)
      .splitWhen(SubstreamCancelStrategy.drain)(_ == 5)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))

    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
    sinkPublisher.subscribe(subscriber)
    val subscription = subscriber.expectSubscription()

    subscription.request(1)
    val (_, tailFlow1) = subscriber.expectNext()
    val tailSink1 = tailFlow1.runWith(TestSink.probe[Int])
    tailSink1.requestNext(2)

    //cancelling master subscription
    subscription.cancel()

    //Still the subflow works as normal
    tailSink1.requestNext(3)
    tailSink1.requestNext(4)
    tailSink1.request(1)
    tailSink1.expectComplete()

    //No completion message for the master stream
    subscription.request(1)
    subscriber.expectNoMsg(100.milliseconds)
  }

  "fail if substream materializes twice" in {
    val sinkPublisher = Source(1 to 10)
      .splitWhen(SubstreamCancelStrategy.drain)(_ == 5)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))

    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
    sinkPublisher.subscribe(subscriber)
    val subscription = subscriber.expectSubscription()

    subscription.request(1)
    val (_, tailFlow1) = subscriber.expectNext()
    val tailSink1a = tailFlow1.runWith(TestSink.probe[Int])
    val tailSink1b = tailFlow1.runWith(TestSink.probe[Int])

    tailSink1a.requestNext(2)

    tailSink1b.request(1)
    val error = tailSink1b.expectError()
    error.getClass should be (classOf[IllegalStateException])
    error.getMessage should be ("Substream Source cannot be materialized more than once")

    tailSink1a.requestNext(3)
    tailSink1a.requestNext(4)

  }
}
