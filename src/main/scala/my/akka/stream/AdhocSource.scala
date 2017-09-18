package my.akka.stream

import akka.NotUsed
import akka.stream.{Graph, SourceShape}
import akka.stream.scaladsl.Source

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

object AdhocSource {

  def adhocSource[T](timeout: FiniteDuration, maxRetries: Int, source: Source[T, _]): Source[T, _] =
    Source
      .lazily(() => source)
      .backpressureTimeout(timeout).recoverWithRetries(maxRetries, {
        case t: TimeoutException =>
          println(s"recovered!! ${maxRetries}")
          adhocSource(timeout, maxRetries - 1, source).asInstanceOf[Graph[SourceShape[T], NotUsed]]
      })

  def timeoutSource[T](timeout: FiniteDuration, source: Source[T, _]): Source[T, _] =
    Source
      .lazily(() => source)
      .backpressureTimeout(timeout)

}
