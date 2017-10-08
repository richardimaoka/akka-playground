package my.akka.stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

class PrefixAndTailSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit var system: ActorSystem = ActorSystem("PrefixAndTailSpec")
  implicit var materializer: Materializer = ActorMaterializer()

  implicit def dispatcher = system.dispatcher

  def logit(value: => Any): Unit = info(value.toString)

  "PrefixAndTail" must {
    "work on empty input" in {
      val futureSink = Sink.head[(immutable.Seq[Int], Source[Int, _])]
      val fut = Source.empty.prefixAndTail(10).runWith(futureSink)
      val (prefix, tailFlow) = Await.result(fut, 3.seconds)
      prefix should be(Nil)
      val tailSubscriber = TestSubscriber.manualProbe[Int]
      tailFlow.to(Sink.fromSubscriber(tailSubscriber)).run()
      tailSubscriber.expectSubscriptionAndComplete()
    }

    "work on short input" in  {
      val futureSink = Sink.head[(immutable.Seq[Int], Source[Int, _])]
      val fut = Source(List(1, 2, 3)).prefixAndTail(10).runWith(futureSink)

      val (prefix, tailFlow) = Await.result(fut, 3.seconds)
      prefix should be(List(1, 2, 3)) //actually prefix = Vector(1, 2, 3), but still this matcher passes
      logit(prefix)
      logit(List(1, 2, 3))

      val tailSubscriber = TestSubscriber.manualProbe[Int]
      tailFlow.to(Sink.fromSubscriber(tailSubscriber)).run()
      tailSubscriber.expectSubscriptionAndComplete()
    }

    "work on short input with sink publisher" in  {
      val sinkPublisher = Source(List(1, 2, 3)).prefixAndTail(10).runWith(Sink.asPublisher(false))

      val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
      sinkPublisher.subscribe(subscriber)
      val subscription = subscriber.expectSubscription()

      /**
       * See that ONLY ONE REQUEST request(1) from downstream
       * pulls the prefix vector Vector(1,2,3) with THREE elements
       */
      subscription.request(1)
      val (prefix, tailFlow) = subscriber.expectNext()
      logit(prefix)
      logit(tailFlow)
      // Vector(1, 2, 3)
      // Source(SourceShape(EmptySource.out(2115725597)))
      subscriber.expectComplete()

      val sink = tailFlow.runWith(TestSink.probe[Int])
      sink.expectSubscription()
      sink.expectComplete()
    }

    "work on long input with sink publisher" in  {
      val sinkPublisher = Source(List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)).prefixAndTail(3).runWith(Sink.asPublisher(false))

      val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
      sinkPublisher.subscribe(subscriber)
      val subscription = subscriber.expectSubscription()

      /**
       * See that ONLY ONE REQUEST request(1) from downstream
       * pulls the prefix vector Vector(1,2,3) with THREE elements
       */
      subscription.request(1)
      val (prefix, tailFlow) = subscriber.expectNext()
      logit(prefix)
      logit(tailFlow)
      // Vector(1, 2, 3)
      // Source(SourceShape(EmptySource.out(2115725597)))
      subscriber.expectComplete()

      val sink = tailFlow.runWith(TestSink.probe[Int])
      sink.requestNext(4)
      sink.requestNext(5)
      sink.requestNext(6)
      sink.requestNext(7)
      sink.requestNext(8)
      sink.requestNext(9)
      sink.requestNext(10)
      sink.expectComplete()
    }

    "work on long input with grouped" in  {
      val sinkPublisher = Source(List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)).prefixAndTail(3).runWith(Sink.asPublisher(false))

      val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
      sinkPublisher.subscribe(subscriber)
      val subscription = subscriber.expectSubscription()

      /**
       * See that ONLY ONE REQUEST request(1) from downstream
       * pulls the prefix vector Vector(1,2,3) with THREE elements
       */
      subscription.request(1)
      val (prefix, tailFlow) = subscriber.expectNext()
      logit(prefix)
      logit(tailFlow)
      // Vector(1, 2, 3)
      // Source(SourceShape(EmptySource.out(2115725597)))
      subscriber.expectComplete()

      /**
       * See one request for grouped() will emit a Seq[Int]
       */
      val sink = tailFlow.grouped(7).runWith(TestSink.probe[Seq[Int]])
      sink.request(1)
      logit(sink.expectNext())
      //Vector(4, 5, 6, 7, 8, 9, 10)
    }
  }

  "handle zero take count" in {
    val fut = Source(1 to 10).prefixAndTail(0).runWith(Sink.head[(immutable.Seq[Int], Source[Int, _])])
    val (takes, tail) = Await.result(fut, 3.seconds)
    takes should be(Nil)

    val sink = tail.grouped(10).runWith(TestSink.probe[Seq[Int]])
    sink.requestNext(Vector(1,2,3,4,5,6,7,8,9,10))
  }

  "handle negative take count" in {
    val futureSink = Sink.head[(immutable.Seq[Int], Source[Int, _])]
    val fut = Source(1 to 10).prefixAndTail(-1).runWith(futureSink)
    val (takes, tail) = Await.result(fut, 3.seconds)
    takes should be(Nil)

    val sink = tail.grouped(10).runWith(TestSink.probe[Seq[Int]])
    sink.requestNext(Vector(1,2,3,4,5,6,7,8,9,10))
  }

  "work if size of take is equal to stream size" in {
    val futureSink = Sink.head[(immutable.Seq[Int], Source[Int, _])]
    val fut = Source(1 to 10).prefixAndTail(10).runWith(futureSink)
    val (takes, tail) = Await.result(fut, 3.seconds)
    takes should be(1 to 10)

    val subscriber = TestSubscriber.manualProbe[Int]()
    tail.to(Sink.fromSubscriber(subscriber)).run()
    subscriber.expectSubscriptionAndComplete()
  }

  "throw if tail is attempted to be materialized twice" in {
    val futureSink = Sink.head[(immutable.Seq[Int], Source[Int, _])]
    val fut = Source(1 to 2).prefixAndTail(1).runWith(futureSink)
    val (takes, tail) = Await.result(fut, 3.seconds)
    takes should be(Seq(1))

    val subscriber1 = TestSubscriber.probe[Int]()
    tail.to(Sink.fromSubscriber(subscriber1)).run()

    val subscriber2 = TestSubscriber.probe[Int]()
    tail.to(Sink.fromSubscriber(subscriber2)).run()
    subscriber2.expectSubscriptionAndError().getMessage should ===("Substream Source cannot be materialized more than once")

    subscriber1.requestNext(2).expectComplete()
  }

  "signal error if substream has been not subscribed in time" in {
    val ms = 300

    val tightTimeoutMaterializer =
      ActorMaterializer(ActorMaterializerSettings(system)
        .withSubscriptionTimeoutSettings(
          StreamSubscriptionTimeoutSettings(StreamSubscriptionTimeoutTerminationMode.cancel, ms.millisecond)))

    val futureSink = Sink.head[(immutable.Seq[Int], Source[Int, _])]
    val fut = Source(1 to 2).prefixAndTail(1).runWith(futureSink)(tightTimeoutMaterializer)
    val (takes, tail) = Await.result(fut, 3.seconds)
    takes should be(Seq(1))

    val subscriber = TestSubscriber.probe[Int]()
    Thread.sleep(1000)

    tail.to(Sink.fromSubscriber(subscriber)).run()(tightTimeoutMaterializer)
    subscriber.expectSubscriptionAndError().getMessage should ===(s"Substream Source has not been materialized in ${ms} milliseconds")
  }

  "not fail the stream if substream has not been subscribed in time and configured subscription timeout is noop" in {
    val tightTimeoutMaterializer =
      ActorMaterializer(ActorMaterializerSettings(system)
        .withSubscriptionTimeoutSettings(
          StreamSubscriptionTimeoutSettings(StreamSubscriptionTimeoutTerminationMode.noop, 1.millisecond)))

    val futureSink = Sink.head[(immutable.Seq[Int], Source[Int, _])]
    val fut = Source(1 to 2).prefixAndTail(1).runWith(futureSink)(tightTimeoutMaterializer)
    val (takes, tail) = Await.result(fut, 3.seconds)
    takes should be(Seq(1))

    val subscriber = TestSubscriber.probe[Int]()
    Thread.sleep(200)

    tail.to(Sink.fromSubscriber(subscriber)).run()(tightTimeoutMaterializer)
    subscriber.expectSubscription().request(2)
    subscriber.expectNext(2).expectComplete()
  }

  "shut down main stage if substream is empty, even when not subscribed" in {
    val futureSink = Sink.head[(immutable.Seq[Int], Source[Int, _])]
    val (term, fut) = Source.single(1)
      .prefixAndTail(1)
      .watchTermination()(Keep.right)
      .toMat(futureSink)(Keep.both)
      .run()
    val (takes, tail) = Await.result(fut, 3.seconds)
    takes should be(Seq(1))
    Await.result(term, 3.seconds) should be (Done)
  }

  "DO NOT shut down main stage if substream is NOT empty, even when not subscribed" in {
    val futureSink = Sink.head[(immutable.Seq[Int], Source[Int, _])]
    val (term, fut) = Source(1 to 2)
      .prefixAndTail(1)
      .watchTermination()(Keep.right)
      .toMat(futureSink)(Keep.both)
      .run()
    val (takes, tail) = Await.result(fut, 3.seconds)
    takes should be(Seq(1))
    /**
     * ??????????????????????????????????????????????????
     */
    Await.result(term, 3.seconds) should be (Done)
  }

  "handle onError when no substream open" in {
    val publisher = TestPublisher.manualProbe[Int]()
    val subscriber = TestSubscriber.manualProbe[(immutable.Seq[Int], Source[Int, _])]()

    Source.fromPublisher(publisher).prefixAndTail(3).to(Sink.fromSubscriber(subscriber)).run()

    val upstream = publisher.expectSubscription()
    val downstream = subscriber.expectSubscription()

    downstream.request(1)

    upstream.expectRequest()
    upstream.sendNext(1)

    val testError = new Exception("test") with NoStackTrace
    upstream.sendError(testError)
    subscriber.expectError(testError)
  }

  "handle master stream cancellation" in {
    val publisher = TestPublisher.manualProbe[Int]()
    val subscriber = TestSubscriber.manualProbe[(immutable.Seq[Int], Source[Int, _])]()

    Source.fromPublisher(publisher).prefixAndTail(3).to(Sink.fromSubscriber(subscriber)).run()

    val upstream = publisher.expectSubscription()
    val downstream = subscriber.expectSubscription()

    downstream.request(1)

    upstream.expectRequest()
    upstream.sendNext(1)

    //Reactive Stream API's cancel
    downstream.cancel()
    upstream.expectCancellation()
  }

  "pass along early cancellation" in {
    val up = TestPublisher.manualProbe[Int]()
    val down = TestSubscriber.manualProbe[(immutable.Seq[Int], Source[Int, _])]()

    val flowSubscriber = Source.asSubscriber[Int].prefixAndTail(1).to(Sink.fromSubscriber(down)).run()

    val downstream = down.expectSubscription()
    downstream.cancel()
    up.subscribe(flowSubscriber)
    val upsub = up.expectSubscription()
    upsub.expectCancellation()
  }

  "work even if tail subscriber arrives after substream completion" in {
    val pub = TestPublisher.manualProbe[Int]()
    val sub = TestSubscriber.manualProbe[Int]()

    val f = Source.fromPublisher(pub).prefixAndTail(1).runWith(Sink.head)
    val s = pub.expectSubscription()
    s.sendNext(0)

    val (_, tail) = Await.result(f, 3.seconds)

    val tailPub = tail.runWith(Sink.asPublisher(false))
    s.sendComplete()

    tailPub.subscribe(sub)
    sub.expectSubscriptionAndComplete()
  }

}
