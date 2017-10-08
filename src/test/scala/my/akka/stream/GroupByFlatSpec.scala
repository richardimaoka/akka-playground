package my.akka.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorMaterializer, Materializer}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers, WordSpec}

import scala.concurrent.duration._

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

}
