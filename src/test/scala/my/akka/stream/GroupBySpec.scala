package my.akka.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestProbe
import org.scalatest.words.ResultOfKeyWordApplication
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
      masterSubscriber.expectNext() should be(1)

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
      masterSubscription.request(5)
      logit(masterSubscriber.expectNext())
      logit(masterSubscriber.expectNext())
      logit(masterSubscriber.expectNext())
      logit(masterSubscriber.expectNext())
      logit(masterSubscriber.expectNext())

      masterSubscription.request(1)
      masterSubscriber.expectComplete()
    }
  }

  "merge or concat substreams" should {
    "do" in {
      println("mergeSubstreams")
      Source(1 to 10).groupBy(3, _ % 3).mergeSubstreams.runForeach(x => print(x + ", "))
      //3, 2, 1, 4, 5, 6, 7, 8, 9, 10
      Thread.sleep(50)
      println()

      println("mergeSubstreamsWithParallelism 1 - only one subflow executed:")
      Source(1 to 10).groupBy(3, _ % 3).mergeSubstreamsWithParallelism(1).runForeach(x => print(x + ", "))
      //1
      Thread.sleep(50)
      println()

      println("mergeSubstreamsWithParallelism 2 - 2 of 3 subflows executed:")
      Source(1 to 20).groupBy(3, _ % 3).mergeSubstreamsWithParallelism(2).runForeach(x => print(x + ", "))
      //2, 1, 4, 5
      Thread.sleep(50)
      println()

      println("mergeSubstreamsWithParallelism 3 - all 3 subflows executed:")
      Source(1 to 10).groupBy(3, _ % 3).mergeSubstreamsWithParallelism(3).runForeach(x => print(x + ", "))
      //3, 2, 1, 4, 5, 6, 7, 8, 9, 10
      Thread.sleep(50)
      println()

      println("concatSubstreams - only one subflow executed")
      Source(1 to 10).groupBy(3, _ % 3).concatSubstreams.runForeach(x => print(x + ", "))
      //1, and it gets stuck
      Thread.sleep(50)
      println()
    }
  }

  "lift method in akka.stream.scaladsl.FlowGroupBySpec" must {
    "prefixAndTail" in {
      Source(1 to 10)
        .prefixAndTail(4)
        .runForeach(pair => {
          val takenElementVector: Seq[Int] = pair._1
          val sourceOfRemainingElements: Source[Int, NotUsed] = pair._2
          println("inner: takeElementVector = " + takenElementVector)
          // Vector(1, 2, 3, 4)
          sourceOfRemainingElements.runForeach(x => println("inner: " + x))
          //inner: 5
          //inner: 6
          //inner: 7
          //inner: 8
          //inner: 9
          //inner: 10
        })
      Thread.sleep(50)
    }

    "prefixAndTail then map" in {
      val key: Int => Int = _ % 3
      Source(1 to 10)
        .prefixAndTail(1)
        .map(p ⇒
           //→ method from predef returns Tuple2
          key(p._1.head) → (Source.single(p._1.head) ++ p._2)
          //due to p._1.head, all the elements in the original Source are included
        )
        .runForeach(pair => {
          val first: Int = pair._1
          val sourceOfAllElements: Source[Int, NotUsed] = pair._2
          println(first)
          // Only the very first element of the original Source
          // 1
          sourceOfAllElements.runForeach(x => println("inner in prefixAndTail: " + x))
          // due to p._1.head, all the elements in the original Source are included
          // inner: 1
          // inner: 2
          // inner: 3
          // inner: 4
          // inner: 5
          // inner: 6
          // inner: 7
          // inner: 8
          // inner: 9
          // inner: 10
        })
      Thread.sleep(50)
    }

    "groupBy, prefixAndTail then map" in {
      val key: Int => Int = _ % 5
      val probe = TestSink.probe[(Seq[Int], Source[Int, NotUsed])](system)
      val sink = Source(1 to 25)
        .groupBy(5, _ % 5)
        .prefixAndTail(3)
        .mergeSubstreams
        .runWith(probe)

      for(i <- 1 to 5) {
        sink.request(1)
        val pair = sink.expectNext()
        logit("pair = " + pair)
        //[info]   + pair = (Vector(1, 6, 11),Source(SourceShape(SubSource.out(1921634129))))
        //[info]   + pair = (Vector(2, 7, 12),Source(SourceShape(SubSource.out(875720113))))
        //[info]   + pair = (Vector(3, 8, 13),Source(SourceShape(SubSource.out(1273348023))))
        //[info]   + pair = (Vector(4, 9, 14),Source(SourceShape(SubSource.out(1631867481))))
        //[info]   + pair = (Vector(5, 10, 15),Source(SourceShape(SubSource.out(1569450599))))
        val subSink = pair._2.runWith(TestSink.probe[Int])
        subSink.request(50)
        //logit("subSink.expectNext() = " + subSink.expectNext())
      }
    }

  }
}
