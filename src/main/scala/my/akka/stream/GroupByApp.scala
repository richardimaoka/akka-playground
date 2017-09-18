package my.akka.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}

import scala.util.{Failure, Success}

object GroupByApp {
  def groupByHang(): Unit ={
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    try {
      val source = Source(List(
        1 -> "1a", 1 -> "1b", 1 -> "1c",
        2 -> "2a", 2 -> "2b",
        3 -> "3a", 3 -> "3b", 3 -> "3c",
        4 -> "4a",
        5 -> "5a", 5 -> "5b", 5 -> "5c",
        6 -> "6a", 6 -> "6b",
        7 -> "7a",
        8 -> "8a", 8 -> "8b",
        9 -> "9a", 9 -> "9b",
      ))

      val a = source
        .groupBy(3, _._1)
        .map {
          case (aid, raw) => {
            println(aid, raw)
            aid -> List(raw)
          }
        }
        .reduce[(Int, List[String])] {
        case (l: (Int, List[String]), r: (Int, List[String])) =>
          println(l, r)
          (l._1, l._2 ::: r._2)
      }
        .mergeSubstreams
        .watchTermination()(Keep.both)
        .runForeach { case (aid: Int, items: List[String]) =>
          println(s"$aid - ${items.length}")
        }

      import scala.concurrent.ExecutionContext.Implicits.global
      a.onComplete{
        case Success(a) => println(s"success a = ${a}")
        case Failure(a) => println(s"failure a = ${a}")
      }
      Thread.sleep(2000)
    }
    finally {
      system.terminate()
    }
  }

  def reduceByKey(): Unit ={
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    try {
      val words = Source(List("weird", "stupid", "weird", "weird", "weirD"))
      val MaximumDistinctWords = 10

      val counts: Source[(String, Int), NotUsed] = words
        // split the words into separate streams first
        .groupBy(MaximumDistinctWords, identity)
        //transform each element to pair with number of words in it
        .map(_ -> 1)
        // add counting logic to the streams
        .reduce((l, r) => (l._1, l._2 + r._2))
        // get a stream of word counts
        .mergeSubstreams

      counts.runForeach(println)
      Thread.sleep(1000)
    }
    finally {
      system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    println("reduceByKey ----------------")
    reduceByKey()
    println("groupByHang ----------------")
    groupByHang()
  }
}
