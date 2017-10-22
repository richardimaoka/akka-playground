package akka.stream

import akka.actor.ActorSystem
import akka.stream.impl.fusing.GraphStages
import akka.stream.scaladsl.{Balance, Broadcast, Merge, Zip}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class GraphInterpreterSpec
  extends TestKit(ActorSystem("GraphInterpreterSpec"))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {
  import GraphStages._

  // Reusable components
  val identity = GraphStages.identity[Int]
  val detach = detacher[Int]
  val zip = Zip[Int, String]
  val bcast = Broadcast[Int](2)
  val merge = Merge[Int](2)
  val balance = Balance[Int](2)

}
