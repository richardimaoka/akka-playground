package my.akka.stream

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.stream.scaladsl.{BroadcastHub, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class AdhocSourceSpec extends TestKit(ActorSystem("AdhocSourceSpec")) with WordSpecLike with Matchers with BeforeAndAfterAll {
  def logit(value: => Any): Unit = info(value.toString)

  implicit val materializer = ActorMaterializer()

  "AdhocSource" must {
    "a" in {
      val source = Source.repeat(5)
      val timeout = 1000.milliseconds

      //val adhocSource = AdhocSource.adhocSource(timeout, 5, source)
      //val mat = adhocSource.runWith(TestSink.probe[Int])
      //      mat.request(1).expectNext(5)
      //      mat.request(1).expectNext(5)
      //      mat.request(1).expectNext(5)
      //      //Thread.sleep(300)
      //      mat.request(1).expectNext(5)
      //      mat.request(1).expectNext(5)
      //      mat.request(1).expectNext(5)
    }

    "B" in {
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

  }
}
