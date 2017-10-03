package my.akka.stream

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.TestSubscriber
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import scala.concurrent.duration._

class GroupBySpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit var system: ActorSystem = ActorSystem("GroupBySpec")
  implicit var materializer: Materializer = ActorMaterializer()
  implicit def dispatcher = system.dispatcher

  def logit(value: => Any): Unit = info(value.toString)

  "GroupBySpec" should {
    "concatSubstreams" in {
      val elementCount = 5
      val groupCount = 2
      val maxSubstreams = 2
      val max = if (maxSubstreams > 0) maxSubstreams else groupCount

      logit(s"max = ${maxSubstreams}")

      val source = Source(1 to elementCount).runWith(Sink.asPublisher(false))
      val groupStreamPublisher = Source.fromPublisher(source).groupBy(max, _ % groupCount).concatSubstreams.runWith(Sink.asPublisher(false))
      val masterSubscriber = TestSubscriber.manualProbe[Int]()

      groupStreamPublisher.subscribe(masterSubscriber)
      val masterSubscription = masterSubscriber.expectSubscription()

      masterSubscription.request(1)
      masterSubscriber.expectNext() should be (1)

      /**
       * The stream is choked up
       */
      masterSubscription.request(1)
      masterSubscriber.expectNoMsg(200.milliseconds)
    }

    "mergeSubstreams" in {
      val elementCount = 5
      val groupCount = 2
      val maxSubstreams = 2
      val max = if (maxSubstreams > 0) maxSubstreams else groupCount

      logit(s"max = ${maxSubstreams}")

      val source = Source(1 to elementCount).runWith(Sink.asPublisher(false))
      val groupStreamPublisher = Source.fromPublisher(source).groupBy(max, _ % groupCount).mergeSubstreams.runWith(Sink.asPublisher(false))
      val masterSubscriber = TestSubscriber.manualProbe[Int]()

      groupStreamPublisher.subscribe(masterSubscriber)
      val masterSubscription = masterSubscriber.expectSubscription()

      /**
       * Non-deterministic order
       * it could be like 2,1,3,4,5 or could be 1,2,3,4,5
       */
      masterSubscription.request(1)
      val a = masterSubscriber.expectNext()
      logit(a)

      masterSubscription.request(1)
      val b = masterSubscriber.expectNext()
      logit(b)

      masterSubscription.request(1)
      val c = masterSubscriber.expectNext()
      logit(c)

      masterSubscription.request(1)
      val d = masterSubscriber.expectNext()
      logit(d)

      masterSubscription.request(1)
      val e = masterSubscriber.expectNext()
      logit(e)

      masterSubscription.request(1)
      masterSubscriber.expectComplete()
    }
  }
}
