package my.akka.stream

import java.util.concurrent.ThreadLocalRandom

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.util.ByteString
import org.reactivestreams.Publisher
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

class GroupByFlatSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit var system: ActorSystem = ActorSystem("GroupByFlatSpec")
  implicit var materializer: Materializer = ActorMaterializer()

  implicit def dispatcher = system.dispatcher

  def logit(value: => Any): Unit = info(value.toString)

  "mergeSubstreams" should "flow all elements" in {
    val sink = Source(1 to 10)
      .groupBy(3, _ % 3)
      .mergeSubstreams
      .runWith(TestSink.probe[Int])

    logit("in non-deterministic order")
    for(i <- 1 to 10)
      logit(sink.requestNext())
    // 3
    // 2
    // 1
    // 4
    // 5
    // 6
    // 7
    // 8
    // 9
    // 10
    sink.expectComplete()
  }

  "mergeSubstreamsWithParallelism with less-than-group-count value" should "get stuck" in {
    val sink = Source(1 to 10)
      .map(i => (i%5, i))
      .groupBy(5, _._1)
      .mergeSubstreamsWithParallelism(3)
      .runWith(TestSink.probe[(Int, Int)])

    for(i <- 1 to 6)
      logit(sink.requestNext())
    // (3,3)
    // (2,2)
    // (1,1)
    // (2,7)
    // (1,6)
    // (3,8)
    sink.request(1)
    sink.expectNoMsg(100.milliseconds)
  }

  "concatSubstream" should "get stuck" in {
    val sink = Source(1 to 10)
      .map(i => (i%5, i))
      .groupBy(5, _._1)
      .concatSubstreams
      .runWith(TestSink.probe[(Int, Int)])

    logit(sink.requestNext())
    // (1,1)
    sink.request(1)
    sink.expectNoMsg(100.milliseconds)
  }

  "concatSubstream" should "NOT get stuck if mapper always returns the same value" in {
    val sink = Source(1 to 10)
      .groupBy(5, _ => 1)
      .concatSubstreams
      .runWith(TestSink.probe[(Int)])

    for(i <- 1 to 10)
      logit(sink.requestNext())
    sink.expectComplete()
  }

  "concatSubstream" should "not get stuck with prefixAndTail" in {
    val sinkPublisher = Source(1 to 20)
      .groupBy(5, _ % 5)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))

    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
    sinkPublisher.subscribe(subscriber)
    val subscription = subscriber.expectSubscription()
    subscription.request(6)
    logit(subscriber.expectNext())
    logit(subscriber.expectNext())
    logit(subscriber.expectNext())
    logit(subscriber.expectNext())
    logit(subscriber.expectNext())
    // (Vector(1),Source(SourceShape(SubSource.out(919321894))))
    // (Vector(2),Source(SourceShape(SubSource.out(1117827565))))
    // (Vector(3),Source(SourceShape(SubSource.out(104170662))))
    // (Vector(4),Source(SourceShape(SubSource.out(1845406776))))
    // (Vector(5),Source(SourceShape(SubSource.out(1266771622))))
    subscriber.expectNoMsg(100.milliseconds)
  }

  "concatSubstream" should "get stuck with prefixAndTail(2)" in {
    /**
     * In PrefixAndTailSpec, there is a case (no groupBy before prefixAndTail) works as:
     *
     *   See that ONLY ONE REQUEST request(1) from downstream
     *   pulls the prefix vector Vector(1,2,3) with THREE elements
     *
     * So what is the difference between this and the PrefixAndTailSpec case?
     * (i.e.) why the case below does not pull 2 elements for prefix upon one request?
     *
     * Because, from upstream (Source(1 to 20)), the first request pulls 1, and the second request pulls 2,
     * which means the second request (from the upstream perspective) gets stuck as the sub stream for the second
     * element = 2 is not active, so there is no way the prefix = Vector(1,6) can be sent to to the first substream
     */
    val sinkPublisher = Source(1 to 20)
      .groupBy(5, _ % 5)
      .prefixAndTail(2)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))

    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
    sinkPublisher.subscribe(subscriber)
    val subscription = subscriber.expectSubscription()
    subscription.request(60)
    subscriber.expectNoMsg(100.milliseconds)
  }

  "concatSubstreams" should "let subSource with prefixAndTail(1) emit values in order" in {
    val sinkPublisher = Source(1 to 20)
      .groupBy(5, _ % 5)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))

    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
    sinkPublisher.subscribe(subscriber)
    val subscription = subscriber.expectSubscription()
    subscription.request(5)

    val (prefix1, tailFlow1) = subscriber.expectNext()
    val (prefix2, tailFlow2) = subscriber.expectNext()
    val (prefix3, tailFlow3) = subscriber.expectNext()
    val (prefix4, tailFlow4) = subscriber.expectNext()
    val (prefix5, tailFlow5) = subscriber.expectNext()

    logit(prefix1)
    logit(prefix2)
    logit(prefix3)
    logit(prefix4)
    logit(prefix5)

    val tailSink1 = tailFlow1.runWith(TestSink.probe[Int])
    tailSink1.requestNext(6)
    tailSink1.request(1)
    /**
     * the above request(1) cannot pull the next element = 11 until the
     * tailFlow2 ~ 4 pull their respective next elements = 7 ~ 10
     */
    tailSink1.expectNoMsg(100.milliseconds)

    val tailSink2 = tailFlow2.runWith(TestSink.probe[Int])
    tailSink2.requestNext(7)
    val tailSink3 = tailFlow3.runWith(TestSink.probe[Int])
    tailSink3.requestNext(8)
    val tailSink4 = tailFlow4.runWith(TestSink.probe[Int])
    tailSink4.requestNext(9)
    val tailSink5 = tailFlow5.runWith(TestSink.probe[Int])
    tailSink5.requestNext(10)

    tailSink1.expectNoMsg(100.milliseconds) //Hmm, still the previous tailSink1.request(1) does not cause the emit of elem = 11 ...

    //you need to request on all the other subSource's
    tailSink2.request(1)
    tailSink3.request(1)
    tailSink4.request(1)
    tailSink5.request(1)

    //Then elem = 11 will be emitted
    tailSink1.expectNext(11) //Now the previous tailSink1.request(1) causes the pull
  }

  "groupBy" should "let cancellation of substream" in {
    val sinkPublisher = Source(1 to 10)
      .groupBy(5, _ % 2)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))

    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
    sinkPublisher.subscribe(subscriber)
    val subscription = subscriber.expectSubscription()
    subscription.request(5)

    val (_, tailFlow1) = subscriber.expectNext()
    val (_, tailFlow2) = subscriber.expectNext()

    val tailSink1 = tailFlow1.runWith(TestSink.probe[Int])
    val tailSink2 = tailFlow2.runWith(TestSink.probe[Int])

    tailSink1.cancel()
    tailSink1.expectNoMsg(100.milliseconds)

    tailSink2.requestNext(4)
    tailSink2.requestNext(6)
    tailSink2.requestNext(8)
    tailSink2.requestNext(10)
  }

  "groupBy" should "let subsource get stuck if one of them backpressures" in {
    val sinkPublisher = Source(1 to 10)
      .groupBy(5, _ % 2)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))

    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, NotUsed])]()
    sinkPublisher.subscribe(subscriber)
    val subscription = subscriber.expectSubscription()
    subscription.request(5)

    val (_, tailFlow1) = subscriber.expectNext()
    val (_, tailFlow2) = subscriber.expectNext()

    val tailSink1 = tailFlow1.runWith(TestSink.probe[Int])
    val tailSink2 = tailFlow2.runWith(TestSink.probe[Int])

    tailSink2.request(5)
    //no message, since tailSink1 back pressures and holds up tailSink*2* as well
    tailSink2.expectNoMsg(100.milliseconds)

    //then request on the other flow
    tailSink1.request(5)
    //now both tailSinks flow
    tailSink2.requestNext(4)
    tailSink2.requestNext(6)
    tailSink2.requestNext(8)
    tailSink2.requestNext(10)

    tailSink1.requestNext(3)
    tailSink1.requestNext(5)
    tailSink1.requestNext(7)
    tailSink1.requestNext(9)
  }

  "groupBy" should "accept cancellation of master downstream when not consumed anything" in {
    val sourcePublisher = TestPublisher.manualProbe[Int]()
    val sinkPublisher = Source.fromPublisher(sourcePublisher)
      .groupBy(2, _ % 2)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))
    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, _])]()
    sinkPublisher.subscribe(subscriber)

    val upstreamSubscription = sourcePublisher.expectSubscription()
    val downstreamSubscription = subscriber.expectSubscription()
    downstreamSubscription.cancel()
    upstreamSubscription.expectCancellation()
  }

  "groupBy" should "accept cancellation of master upstream when not consumed anything" in {
    val sourcePublisher = TestPublisher.manualProbe[Int]()
    val sinkPublisher = Source.fromPublisher(sourcePublisher)
      .groupBy(2, _ % 2)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))
    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, _])]()
    sinkPublisher.subscribe(subscriber)
    subscriber.expectSubscription()

    val upstreamSubscription = sourcePublisher.expectSubscription()
    upstreamSubscription.cancel()
    subscriber.expectNoMsg()
  }

  "groupBy" should "work with empty input stream" in {
    val sinkPublisher = Source(List.empty[Int])
      .groupBy(2, _ % 2)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))

    val sinkSubscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, _])]()
    sinkPublisher.subscribe(sinkSubscriber)

    sinkSubscriber.expectSubscriptionAndComplete()
  }

  "groupBy" should "abort on onError from upstream" in {
    val sourcePublisher = TestPublisher.manualProbe[Int]()
    val sinkPublisher = Source.fromPublisher(sourcePublisher)
      .groupBy(2, _ % 2)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))
    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, _])]()
    sinkPublisher.subscribe(subscriber)

    val upstreamSubscription = sourcePublisher.expectSubscription()

    val downstreamSubscription = subscriber.expectSubscription()
    downstreamSubscription.request(100)

    val e = new RuntimeException("test") with NoStackTrace
    upstreamSubscription.sendError(e)

    subscriber.expectError(e)
  }

  "groupBy" should "abort on onError from upstream when substreams are running" in {
    val sourcePublisher = TestPublisher.manualProbe[Int]()
    val sinkPublisher = Source.fromPublisher(sourcePublisher)
      .groupBy(2, _ % 2)
      .prefixAndTail(1)
      .concatSubstreams
      .runWith(Sink.asPublisher(false))
    val subscriber = TestSubscriber.manualProbe[(Seq[Int], Source[Int, _])]()
    sinkPublisher.subscribe(subscriber)

    val upstreamSubscription = sourcePublisher.expectSubscription()

    val downstreamSubscription = subscriber.expectSubscription()
    downstreamSubscription.request(100)

    upstreamSubscription.sendNext(1)
    upstreamSubscription.sendNext(2)
    upstreamSubscription.sendNext(3)
    upstreamSubscription.sendNext(4)
    upstreamSubscription.sendNext(1)
    upstreamSubscription.sendNext(1)

    val (_, substream1) = subscriber.expectNext()
    val (_, substream2) = subscriber.expectNext()
    val subStreamSinkPublisher1 = substream1.runWith(Sink.asPublisher(false))
    val subStreamSinkPublisher2 = substream2.runWith(Sink.asPublisher(false))
    val subStreamSinkSubscriber1 = TestSubscriber.manualProbe[Int]()
    val subStreamSinkSubscriber2 = TestSubscriber.manualProbe[Int]()
    subStreamSinkPublisher1.subscribe(subStreamSinkSubscriber1)
    subStreamSinkPublisher2.subscribe(subStreamSinkSubscriber2)
    val subStream1Subscription = subStreamSinkSubscriber1.expectSubscription()
    val subStream2Subscription = subStreamSinkSubscriber2.expectSubscription()
    subStream1Subscription.request(10)
    subStream2Subscription.request(10)
    subStreamSinkSubscriber1.expectNext(3)
    subStreamSinkSubscriber2.expectNext(4)
    subStreamSinkSubscriber1.expectNext(1)
    subStreamSinkSubscriber1.expectNext(1)

    val e = new RuntimeException("test") with NoStackTrace
    upstreamSubscription.sendError(e)

    subStreamSinkSubscriber1.expectError(e)
    subStreamSinkSubscriber2.expectError(e)
    subscriber.expectError(e)
  }

  "groupBy" should "emit subscribe before completed" in {
    val futureGroupSource =
      Source.single(0)
        .groupBy(1, elem ⇒ "all")
        .prefixAndTail(0)
        .map(_._2)
        .concatSubstreams
        .runWith(Sink.head)
    val pub: Publisher[Int] = Await.result(futureGroupSource, 3.seconds).runWith(Sink.asPublisher(false))
    val probe = TestSubscriber.manualProbe[Int]()
    pub.subscribe(probe)
    val sub = probe.expectSubscription()
    sub.request(1)
    probe.expectNext(0)
    probe.expectComplete()
  }

  "groupBy" should "work under fuzzing stress test" in {
    val sourcePublisher = TestPublisher.manualProbe[ByteString]()
    val sinkSubscriber = TestSubscriber.manualProbe[ByteString]()

    val sinkPublisher = Source.fromPublisher[ByteString](sourcePublisher)
      .groupBy(256, elem ⇒ elem.head).map(_.reverse).mergeSubstreams
      .groupBy(256, elem ⇒ elem.head).map(_.reverse).mergeSubstreams
      .runWith(Sink.asPublisher(false))
    sinkPublisher.subscribe(sinkSubscriber)

    val upstreamSubscription = sourcePublisher.expectSubscription()
    val downstreamSubscription = sinkSubscriber.expectSubscription()

    downstreamSubscription.request(300)

    def randomByteString(size: Int): ByteString = {
      val a = new Array[Byte](size)
      ThreadLocalRandom.current().nextBytes(a)
      ByteString(a)
    }

    for (i ← 1 to 300) {
      val byteString = randomByteString(10)
      //upstreamSubscription.expectRequest() //┐('～` )┌
      upstreamSubscription.sendNext(byteString)
      sinkSubscriber.expectNext() should ===(byteString)
    }
    upstreamSubscription.sendComplete()
  }
}
