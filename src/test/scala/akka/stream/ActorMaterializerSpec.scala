package akka.stream

import akka.Done
import akka.actor.{ActorSystem, Props}
import akka.stream.impl.{PhasedFusingActorMaterializer, StreamSupervisor}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.testkit.TestPublisher
import akka.testkit.{ImplicitSender, TestActor, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class ActorMaterializerSpec
  extends TestKit(ActorSystem("ActorMaterializerSpec"))
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {

  def logit(value: => Any): Unit = info(value.toString)

  "ActorMaterializer" must {

    "report shutdown status properly" in {
      val m = ActorMaterializer.create(system)

      m.isShutdown should ===(false)
      /**
        * This calls PhasedFusingActorMaterializer ->
        *   override def shutdown(): Unit =
        *     if (haveShutDown.compareAndSet(false, true)) supervisor ! PoisonPill
        *
        * Thus, haveShutDown (which will be used in def isShutDown()) is immediately set
        * and the following m.isShutdown returns true
        */
      m.shutdown()
      m.isShutdown should ===(true)
    }

    "properly shut down actors associated with it" in {
      val m = ActorMaterializer.create(system)

      val f = Source.fromPublisher(TestPublisher.probe[Int]()(system)).runFold(0)(_ + _)(m)

      m.shutdown()

      /**
        * AbruptTerminationException thrown by ActorGraphInterpreter's postStop() method.
        * StreamSupervisor of the materializer receives Terminate message.
        *
        * f: Future[Int] which results in Failure(AbruptTerminationException)
        */
      an[AbruptTerminationException] should be thrownBy Await.result(f, 3.seconds)
    }

    "refuse materialization after shutdown" in {
      val m = ActorMaterializer.create(system)
      m.shutdown()
      an[IllegalStateException] should be thrownBy
        Source(1 to 5).runForeach(println)(m)

      try{
        Source(1 to 5).runForeach(println)(m)
      } catch {
        case e: IllegalStateException =>
          /**
            * thrown by akka.actor.dungeon.Children (i.e.) not a class in akka.stream!
            */
          logit(e)
        // java.lang.IllegalStateException: cannot create children while terminating or terminated
      }
    }

    "shut down the supervisor actor it encapsulates" in {
      val m = ActorMaterializer.create(system).asInstanceOf[PhasedFusingActorMaterializer]

      /**
        * What will be the children when you have only one stream?
        */
      val mat = Source.maybe[Any].to(Sink.ignore).run()(m)
      m.supervisor ! StreamSupervisor.GetChildren

      logit("-----------------------------------------")
      //enabled with ImplicitSender
      expectMsgType[StreamSupervisor.Children].children.foreach(x => logit(x))
      // Actor[akka://ActorMaterializerSpec/user/StreamSupervisor-3/flow-3-0-ignoreSink#-149531784]

      /**
        * What will be the children when you have multiple stream?
        */
      Source.maybe[Any].to(Sink.ignore).run()(m)
      m.supervisor ! StreamSupervisor.GetChildren

      logit("-----------------------------------------")
      //enabled with ImplicitSender
      expectMsgType[StreamSupervisor.Children].children.foreach(x => logit(x))
      // Actor[akka://ActorMaterializerSpec/user/StreamSupervisor-3/flow-3-0-ignoreSink#-149531784]
      // Actor[akka://ActorMaterializerSpec/user/StreamSupervisor-3/flow-4-0-ignoreSink#-744930492]

      Source.maybe[Any].to(Sink.ignore).run()(m)
      m.supervisor ! StreamSupervisor.GetChildren

      logit("-----------------------------------------")
      //enabled with ImplicitSender
      expectMsgType[StreamSupervisor.Children].children.foreach(x => logit(x))
      // Actor[akka://ActorMaterializerSpec/user/StreamSupervisor-3/flow-3-0-ignoreSink#-149531784]
      // Actor[akka://ActorMaterializerSpec/user/StreamSupervisor-3/flow-4-0-ignoreSink#-744930492]
      // Actor[akka://ActorMaterializerSpec/user/StreamSupervisor-3/flow-5-0-ignoreSink#-1173554598]

      m.shutdown()

      m.supervisor ! StreamSupervisor.GetChildren
      expectNoMsg(1.second)
    }

    "returns correct children by GetChildren" in {
      val m = ActorMaterializer.create(system).asInstanceOf[PhasedFusingActorMaterializer]

      /**
        * What happens when there is one stream
        */
      val mat1 = Source.single(1).toMat(Sink.ignore)(Keep.right).run()(m)
      m.supervisor ! StreamSupervisor.GetChildren

      //Stream is already completed
      logit("-----------------------------------------")
      Await.result(mat1, 1.second) should be (Done)

      //enabled with ImplicitSender
      val expectedMsg1 = expectMsgType[StreamSupervisor.Children]
      expectedMsg1.children.foreach(x => logit(x))
      // see that it flow-**6**-0-
      // Actor[akka://ActorMaterializerSpec/user/StreamSupervisor-3/flow-6-0-ignoreSink#-899343765]
      expectedMsg1.children.size should be (1)

      /**
        * What happens when there is another stream
        */
      val mat2 = Source.single(1).toMat(Sink.ignore)(Keep.right).run()(m)
      m.supervisor ! StreamSupervisor.GetChildren

      //Stream is already completed
      logit("-----------------------------------------")
      Await.result(mat2, 1.second) should be (Done)

      //enabled with ImplicitSender
      val expectedMsg2 = expectMsgType[StreamSupervisor.Children]
      expectedMsg2.children.foreach(x => logit(x))
      // see that it flow-**7**-0-
      // Actor[akka://ActorMaterializerSpec/user/StreamSupervisor-3/flow-7-0-ignoreSink#-78039495]
      expectedMsg2.children.size should be (1)

      /**
        * Yet another stream
        */
      val mat3 = Source.single(1).toMat(Sink.ignore)(Keep.right).run()(m)
      m.supervisor ! StreamSupervisor.GetChildren

      //Stream is already completed
      logit("-----------------------------------------")
      Await.result(mat3, 1.second) should be (Done)

      //enabled with ImplicitSender
      val expectedMsg3 = expectMsgType[StreamSupervisor.Children]
      expectedMsg3.children.foreach(x => logit(x))
      // see that it flow-**8**-0-
      // Actor[akka://ActorMaterializerSpec/user/StreamSupervisor-3/flow-8-0-ignoreSink#-160574226]
      expectedMsg3.children.size should be (1)

      m.shutdown()

      m.supervisor ! StreamSupervisor.GetChildren
      expectNoMsg(1.second)
    }

    "handle properly broken Props" in {
      val m = ActorMaterializer.create(system)
      an[IllegalArgumentException] should be thrownBy
        Await.result(
          Source.actorPublisher(Props(classOf[TestActor], "wrong", "arguments")).runWith(Sink.head)(m),
          3.seconds)
    }

    "report correctly if it has been shut down from the side" in {
      val sys = ActorSystem()
      val m = ActorMaterializer.create(sys)
      Await.result(sys.terminate(), Duration.Inf)
      m.isShutdown should ===(true)
    }
  }
}


