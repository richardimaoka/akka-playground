package my.akka.stream

import akka.NotUsed
import akka.stream.{Graph, SourceShape}
import akka.stream.scaladsl.Source

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

object AdhocSource {

  def adhocSource[T](timeout: FiniteDuration, maxRetries: Int, source: Source[T, _], printCount: Boolean = false): Source[T, _] =
    Source
      .lazily(
        () => source
          .backpressureTimeout(timeout)
          .recoverWithRetries(maxRetries, {
            case t: TimeoutException =>
              if(printCount)
                println(s"creating lazy source with remaining count = ${maxRetries - 1}")
              adhocSource(timeout, maxRetries - 1, source).asInstanceOf[Graph[SourceShape[T], NotUsed]]
          })
      )

  /**
   * This is wrong!
   * Once the lazy source started, backpressureTimeout keeps throwing TimeoutException
   * and recoverWithRetries is kept being called
   */
  def adhocSource2[T](timeout: FiniteDuration, maxRetries: Int, source: Source[T, _], printCount: Boolean = false): Source[T, _] =
    Source
      .lazily(() => source)
      .backpressureTimeout(timeout).recoverWithRetries(maxRetries, {
        case t: TimeoutException =>
          println(s"creating lazy source with remaining count = ${maxRetries - 1}")
          adhocSource(timeout, maxRetries - 1, source).asInstanceOf[Graph[SourceShape[T], NotUsed]]
      })

  def timeoutSource[T](timeout: FiniteDuration, source: Source[T, _]): Source[T, _] =
    Source
      .lazily(() => source)
      .backpressureTimeout(timeout)

}
