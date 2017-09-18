package my.akka.stream

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.stream.scaladsl.{BroadcastHub, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class AdhocSourceSpec extends TestKit(ActorSystem("AdhocSourceSpec")) with WordSpecLike with Matchers with BeforeAndAfterAll {
  def logit(value: => Any): Unit = info(value.toString)

  implicit val materializer = ActorMaterializer()

  "AdhocSource" must {
    "a" in {
      val source = Source.repeat(5)
      val timeout = 1000.milliseconds
    }

    "time out after the preset timeout value has passed" in {
      val source = Source.repeat(5)
      val timeout = 200.milliseconds

      val timeoutSource = AdhocSource.timeoutSource(timeout, source)
      val mat = timeoutSource.runWith(TestSink.probe[Int])
      mat.request(1).expectNext(5)
      mat.request(1).expectNext(5)
      mat.request(1).expectNext(5)
      Thread.sleep(500)
      mat.request(1).expectError().getClass should be(classOf[TimeoutException])
    }

    "wait in lazy source after timeout" in {
      val timeout = 200.milliseconds
      val materialized = new AtomicInteger(0)
      val source = Source
        .empty.mapMaterializedValue(_ => materialized.incrementAndGet())
        .concat(Source.repeat(5))

      val (watchTerm, sink) = AdhocSource.adhocSource(timeout, 5, source)
        .watchTermination()(Keep.right)
        .toMat(TestSink.probe[Int])(Keep.both)
        .run()

      Thread.sleep(500)

      // Source not materialized yet
      materialized.get() should be(0)

      sink.request(1).expectNext(5)
      // Source materialized after the first demand
      materialized.get() should be(1)
      sink.request(1).expectNext(5)
      sink.request(1).expectNext(5)
      materialized.get() should be(1)

      Thread.sleep(500)

      //After timeout has passed, there must be TimeoutException
      import scala.concurrent.ExecutionContext.Implicits.global
      watchTerm onComplete{
        case Success(a) => fail("This future should not succeed!")
        case Failure(a) => succeed
      }

      materialized.get() should be(1)

      //on the next demand, the source should be materialized again
      sink.request(1).expectNext(5)
      materialized.get() should be(2)

      Thread.sleep(500)

      //if no new demand, the source is still lazy i.e. not materialized
      materialized.get() should be(2)

    }

  }

  "lazily" should {
    "not materialize even after run, when there is no demand" in {
      val materialized = new AtomicBoolean()
      Source
        .lazily(() => Source.empty.mapMaterializedValue(_ => materialized.set(true)))
        .runWith(TestSink.probe[Int])
      Thread.sleep(500)
      materialized.get() should be(false)
    }

    "materialize after run AND when there is a demand" in {
      val materialized = new AtomicBoolean()
      val mat = Source
        .lazily(() => Source.empty.mapMaterializedValue(_ => materialized.set(true)))
        .runWith(TestSink.probe[Int])

      mat.request(1)
      Thread.sleep(500)
      materialized.get() should be(true)
    }
  }

  "timeoutSource" should {
    "not start the source even after run, when there is no demand" in {
      val materialized = new AtomicBoolean()
      val timeout = 200.milliseconds
      val mat = AdhocSource.timeoutSource(timeout, Source.empty.mapMaterializedValue(_ => materialized.set(true)))
        .runWith(TestSink.probe[Int])
      Thread.sleep(500)
      materialized.get() should be(false)
    }

    "materialize after run AND when there is a demand" in {
      val materialized = new AtomicBoolean()
      val timeout = 200.milliseconds
      val mat = AdhocSource.timeoutSource(timeout, Source.empty.mapMaterializedValue(_ => materialized.set(true)))
        .runWith(TestSink.probe[Int])
      mat.request(1)
      Thread.sleep(500)
      materialized.get() should be(true)
    }

    "shut down the source when a single consumer disconnects" in {
      val shutdown = Promise[Done]()
      val timeout = 200.milliseconds
      AdhocSource.timeoutSource(
        timeout,
        Source.repeat("a").watchTermination() { (_, term) =>
          shutdown.completeWith(term)
        }).runWith(Sink.head)
      Await.ready(shutdown.future, 10.seconds)
      /**
       * Sink.head terminates when receiving the first element.
       * If you use Sink.ignore, then the future times out:
       *   java.util.concurrent.TimeoutException: Futures timed out after [10 seconds
       */
    }

    "shut down the source when there is no demand until timeout" in {
      val timeout = 1000.milliseconds
      val materialized = new AtomicBoolean()

      val source1 = Source.empty.mapMaterializedValue(_ => materialized.set(true))
      val source2 = Source.repeat(5)

      val (watchTerm, sink) = AdhocSource.timeoutSource(
        timeout,
        source1.concat(source2)
      ).watchTermination()(Keep.right)
        .toMat(TestSink.probe[Int])(Keep.both)
        .run()

      Thread.sleep(300)
      materialized.get() should be(false)

      sink.requestNext(5)
      sink.requestNext(5)
      sink.requestNext(5)
      Thread.sleep(3000)

      sink.expectError().getClass should be(classOf[TimeoutException])
      import scala.concurrent.ExecutionContext.Implicits.global
      watchTerm onComplete{
        case Success(a) => fail("This future should not succeed!")
        case Failure(a) => succeed
      }
    }

  }
}
