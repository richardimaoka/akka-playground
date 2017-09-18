package my.akka.stream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{BroadcastHub, Keep, RunnableGraph, Source}
import akka.stream.stage._

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 * akka/akka#23375
 * https://github.com/akka/akka/issues/23375
 * https://github.com/akka/akka-stream-contrib/issues/110
 * https://gist.github.com/jroper/4f8108f8fa00a7251919e08d4cf9eb71
 */
object LazyBroadcastHub {
  /**
   * Create a broadcast hub for the given source.
   *
   * The hub will only run the source when there are consumers attached to the hub. When all consumers disconnect,
   * after the given idle timeout, if no more consumers connect it will shut the source down.
   *
   * The source will be rematerialized whenever it's not running but a new consumer attaches to the hub.
   *
   * The materialization value is a tuple of a source as produced by BroadcastHub, and a KillSwitch to kill the hub.
   *
   * @param source The source to broadcast.
   * @param idleTimeout The time to wait when there are no consumers before shutting the source down.
   * @param bufferSize The buffer size to buffer messages to producers.
   */
  def forSource[T](source: Source[T, _], idleTimeout: FiniteDuration, bufferSize: Int): RunnableGraph[(Source[T, NotUsed], KillSwitch)] = {
    Source.fromGraph(new LazySourceStage[T](source, idleTimeout))
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(BroadcastHub.sink[T](bufferSize)) {
        case ((callbacks, killSwitch), broadcastSource) =>
          val source = broadcastSource.via(new RecordingStage[T](callbacks))
          (source, killSwitch)
      }
  }

  def forSource[T](source: Source[T, _], idleTimeout: FiniteDuration): RunnableGraph[(Source[T, NotUsed], KillSwitch)] =
    forSource(source, idleTimeout, bufferSize = 256)

  def forSource[T](source: Source[T, _], bufferSize: Int): RunnableGraph[(Source[T, NotUsed], KillSwitch)] =
    forSource(source, Duration.Zero, bufferSize)

  def forSource[T](source: Source[T, _]): RunnableGraph[(Source[T, NotUsed], KillSwitch)] =
    forSource(source, Duration.Zero)

  private trait MaterializationCallbacks {
    def materialized(): Unit
    def completed(): Unit
  }

  private class RecordingStage[T](callbacks: MaterializationCallbacks) extends GraphStage[FlowShape[T, T]] {
    private val in = Inlet[T]("RecordingStage.in")
    private val out = Outlet[T]("RecordingStage.out")
    override def shape = FlowShape(in, out)
    override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush() = push(out, grab(in))
      })
      setHandler(out, new OutHandler {
        override def onPull() = pull(in)
        override def onDownstreamFinish() = callbacks.completed()
      })
      override def preStart() = {
        // This must be done in preStart, if done during materialization then there's a race for the LazySourceStage
        // to finish materializing before this gets invoked.
        callbacks.materialized()
      }
    }
  }

  private class LazySourceStage[T](source: Source[T, _], idleTimeout: FiniteDuration) extends GraphStageWithMaterializedValue[SourceShape[T], MaterializationCallbacks] {
    private val out = Outlet[T]("LazySourceStage.out")
    override def shape = SourceShape(out)
    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {
      val logic = new TimerGraphStageLogic(shape) with MaterializationCallbacks {

        var materializedSources = 0
        var activeIn: Option[SubSinkInlet[T]] = None
        var stopSourceRequest = 0

        val materializedCallback = createAsyncCallback[Unit] { _ =>
          materializedSources += 1
          if (activeIn.isEmpty) {
            startSource()
          }
        }

        val completedCallback = createAsyncCallback[Unit] { _ =>
          materializedSources -= 1
          if (materializedSources == 0) {
            if (idleTimeout == Duration.Zero) {
              stopSource()
            } else {
              stopSourceRequest += 1
              scheduleOnce(stopSourceRequest, idleTimeout)
            }
          }
        }

        def startSource() = {
          assert(activeIn.isEmpty)

          val in = new SubSinkInlet[T]("LazySourceStage.in")
          in.setHandler(new InHandler {
            override def onPush() = push(out, in.grab())
          })
          setHandler(out, new OutHandler {
            override def onPull() = in.pull()
          })

          source.runWith(in.sink)(subFusingMaterializer)

          if (isAvailable(out)) {
            in.pull()
          }
          activeIn = Some(in)
        }

        def stopSource() = {
          assert(activeIn.nonEmpty)

          activeIn.get.cancel()
          ignoreOut()
          activeIn = None
        }

        override protected def onTimer(timerKey: Any) = {
          if (stopSourceRequest == timerKey && materializedSources == 0) {
            stopSource()
          }
        }

        def ignoreOut() = {
          setHandler(out, new OutHandler {
            override def onPull() = ()
          })
        }

        override def materialized() = {
          materializedCallback.invoke(())
        }
        override def completed() = {
          completedCallback.invoke(())
        }

        ignoreOut()
      }

      (logic, logic)
    }
  }

  /**
   * My own test funcs
   */

  def sourceEmpty(): Unit ={
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    try {
      Source.empty.mapMaterializedValue(a => {println(a); a}).to(Sink.ignore)
      Thread.sleep(200)
    }
    finally {
      system.terminate()
    }
  }

  def promise(): Unit ={
    import scala.concurrent.ExecutionContext.Implicits.global

    val f = Future { 10 }
    val p = Promise[Int]()

    /**
     * completeWith "takes" the return value from Future
     * and copy it to the Promise's result
     */
    p completeWith f
    p.future onComplete  {
      case Success(x) => println("Success: " + x)
      case Failure(x) => println("Failure: " + x)
    }
  }

  def main(args: Array[String]): Unit ={
    println("reduceByKey ----------------")
    sourceEmpty()
    println("groupByHang ----------------")
    promise()
  }
}
