package my.akka.typed

import akka.typed._
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.AskPattern._
import my.akka.wrapper.Wrap

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.Await

object HelloWorld {
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String)

  val greeter = Actor.immutable[Greet] { (_, msg) ⇒
    println(s"Hello ${msg.whom}!")
    msg.replyTo ! Greeted(msg.whom)
    Actor.same
  }
}


object AkkaTypedExamples {
  def helloWorld(): Unit ={
    import HelloWorld._
    // using global pool since we want to run tasks after system.terminate
    import scala.concurrent.ExecutionContext.Implicits.global

    val system: akka.typed.ActorSystem[Greet] = ActorSystem(greeter, "hello")

    implicit val timeout: akka.util.Timeout = 2.seconds
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    val future: Future[Greeted] = system ? (Greet("world", _))

    for {
      greeting ← future.recover { case ex ⇒ ex.getMessage }
      done ← { println(s"result: $greeting"); system.terminate() }
    } println("system terminated")
  }

  def moreComplicated(): Unit ={
    object ChatRoom {
      sealed trait Command
      final case class GetSession(screenName: String, replyTo: ActorRef[SessionEvent]) extends Command
      private final case class PostSessionMessage(screenName: String, message: String) extends Command

      sealed trait SessionEvent
      final case class SessionGranted(handle: ActorRef[PostMessage]) extends SessionEvent
      final case class SessionDenied(reason: String) extends SessionEvent
      final case class MessagePosted(screenName: String, message: String) extends SessionEvent

      final case class PostMessage(message: String)

      val behavior: Behavior[Command] = chatRoom(List.empty)

      private def chatRoom(sessions: List[ActorRef[SessionEvent]]): Behavior[Command] =
        Actor.immutable[Command] { (ctx, msg) ⇒
          msg match {
            case GetSession(screenName, client) ⇒
              val wrapper = ctx.spawnAdapter {
                p: PostMessage ⇒ PostSessionMessage(screenName, p.message)
              }
              client ! SessionGranted(wrapper)
              chatRoom(client :: sessions)
            case PostSessionMessage(screenName, message) ⇒
              val mp = MessagePosted(screenName, message)
              sessions foreach (_ ! mp)
              Actor.same
          }
        }
    }

    import ChatRoom._
    val gabbler =
      Actor.immutable[SessionEvent] { (_, msg) ⇒
        msg match {
          case SessionGranted(handle) ⇒
            handle ! PostMessage("Hello World!")
            Actor.same
          case MessagePosted(screenName, message) ⇒
            println(s"message has been posted by '$screenName': $message")
            Actor.stopped
        }
      }

    val main: Behavior[akka.NotUsed] =
      Actor.deferred { ctx ⇒
        val chatRoom = ctx.spawn(ChatRoom.behavior, "chatroom")
        println(s"chatRoom path in main = ${chatRoom.path}")

        val gabblerRef = ctx.spawn(gabbler, "gabbler")
        println(s"gabblerRef path in main = ${gabblerRef.path}")

        ctx.watch(gabblerRef)
        chatRoom ! GetSession("ol’ Gabbler", gabblerRef)

        Actor.immutable[akka.NotUsed] {
          (_, _) ⇒ Actor.unhandled
        } onSignal {
          case (ctx, Terminated(ref)) ⇒
            Actor.stopped
        }
      }

    val system = ActorSystem(main, "ChatRoomDemo")
    Await.result(system.whenTerminated, 3.seconds)
  }

  def main(args: Array[String]): Unit ={
    Wrap("helloWorld")(helloWorld)
    Wrap("moreComplicated")(moreComplicated)
  }
}
