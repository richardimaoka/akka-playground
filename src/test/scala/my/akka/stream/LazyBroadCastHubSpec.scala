package my.akka.stream

import java.util.concurrent.atomic.AtomicBoolean

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.javadsl.TestSink
import akka.stream.{ActorMaterializer, Materializer}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

class LazyBroadCastHubSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit var system: ActorSystem = _
  implicit var materializer: Materializer = _
  implicit def dispatcher = system.dispatcher

  "LazyBroadcastHub" should {

    "not start the source if there are no consumers" in {
      val materialized = new AtomicBoolean()
      LazyBroadcastHub.forSource(Source.empty.mapMaterializedValue(_ => materialized.set(true))).run()
      Thread.sleep(200)
      materialized.get() should be (false)
    }

    "start the source when a consumer attaches" in {
      val (source, _) = LazyBroadcastHub.forSource(Source.repeat("a")).run()
      val sink = source.runWith(TestSink.probe(system))
      sink.requestNext("a")
    }

    "shut down the source when a single consumer disconnects" in {
      val shutdown = Promise[Done]()
      val (source, _) = LazyBroadcastHub.forSource(Source.repeat("a").watchTermination() { (_, term) =>
        shutdown.completeWith(term)
      }).run()
      source.runWith(Sink.head)
      Await.ready(shutdown.future, 10.seconds)
    }

    "not shutdown when there is still a consumer" in {
      val shutdown = Promise[Done]()
      val (source, _) = LazyBroadcastHub.forSource(Source.repeat("a").watchTermination() { (_, term) =>
        shutdown.completeWith(term)
      }).run()
      val sink1 = source.runWith(TestSink.probe(system))
      val sink2 = source.runWith(TestSink.probe(system))
      sink1.requestNext("a")
      sink2.requestNext("a")
      sink2.cancel()
      Thread.sleep(200)
      shutdown.isCompleted should be (false)
    }

    "shut down when multiple consumers disconnect" in {
      val shutdown = Promise[Done]()
      val (source, _) = LazyBroadcastHub.forSource(Source.repeat("a").watchTermination() { (_, term) =>
        shutdown.completeWith(term)
      }).run()
      val sink1 = source.runWith(TestSink.probe(system))
      val sink2 = source.runWith(TestSink.probe(system))

      sink1.requestNext("a")
      sink2.requestNext("a")

      sink1.cancel()
      sink2.cancel()

      Await.ready(shutdown.future, 10.seconds)
    }

    "wait until a timeout before disconnecting" in {
      val shutdown = Promise[Done]()
      val (source, _) = LazyBroadcastHub.forSource(Source.repeat("a").watchTermination() { (_, term) =>
        shutdown.completeWith(term)
      }, 300.millis).run()
      source.runWith(Sink.head)
      Thread.sleep(200)
      shutdown.isCompleted should be (false)
      Await.ready(shutdown.future, 10.seconds)
    }

    "not disconnect if a new sink connects within the timeout" in {
      val shutdown = Promise[Done]()
      val (source, _) = LazyBroadcastHub.forSource(Source.repeat("a").watchTermination() { (_, term) =>
        shutdown.completeWith(term)
      }, 300.millis).run()
      source.runWith(Sink.head)
      Thread.sleep(200)
      val sink = source.runWith(TestSink.probe(system))
      sink.requestNext("a")
      Thread.sleep(200)
      shutdown.isCompleted should be (false)
    }

  }

  override protected def beforeAll() = {
    system = ActorSystem("Test")
    materializer = ActorMaterializer()
  }

  override protected def afterAll() = {
    system.terminate()
  }

}
