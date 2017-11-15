package my.akka.typed

import akka.Done
import akka.typed.persistence.scaladsl.PersistentActor
import akka.typed.persistence.scaladsl.PersistentActor.{Actions, Persist, PersistNothing, Stop}
import akka.typed.{ActorRef, Behavior}

sealed trait BlogCommand extends Serializable

final case class AddPost(content: PostContent, replyTo: ActorRef[AddPostDone]) extends BlogCommand
final case class GetPost(replyTo: ActorRef[PostContent]) extends BlogCommand
final case class ChangeBody(newBody: String, replyTo: ActorRef[Done]) extends BlogCommand
final case class Publish(replyTo: ActorRef[Done]) extends BlogCommand
final case object PassivatePost extends BlogCommand

final case class AddPostDone(postId: String)

sealed trait BlogEvent extends Serializable

final case class PostAdded(postId: String, content: PostContent) extends BlogEvent
final case class BodyChanged(postId: String, newBody: String) extends BlogEvent
final case class Published(postId: String) extends BlogEvent

object BlogState {
  val empty = BlogState(None, published = false)
}

final case class BlogState(
  content: Option[PostContent],
  published: Boolean) {

  def withContent(newContent: PostContent): BlogState =
    copy(content = Some(newContent))

  def isEmpty: Boolean = content.isEmpty

  def postId: String = content match {
    case Some(c) => c.postId
    case None    => throw new IllegalStateException("postId unknown before post is created")
  }
}

final case class PostContent(postId: String, title: String, body: String)

object BlogPost1 {

  private val actions: Actions[BlogCommand, BlogEvent, BlogState] =
    Actions { (ctx, cmd, state) ⇒
      cmd match {
        case AddPost(content, replyTo) ⇒
          val evt = PostAdded(content.postId, content)
          Persist(evt).andThen { state2 ⇒
            // After persist is done additional side effects can be performed
            replyTo ! AddPostDone(content.postId)
          }
        case ChangeBody(newBody, replyTo) ⇒
          val evt = BodyChanged(state.postId, newBody)
          Persist(evt).andThen { _ ⇒
            replyTo ! Done
          }
        case Publish(replyTo) ⇒
          Persist(Published(state.postId)).andThen { _ ⇒
            replyTo ! Done
          }
        case GetPost(replyTo) ⇒
          replyTo ! state.content.get
          PersistNothing()
        case PassivatePost =>
          Stop()
      }
    }

  private def applyEvent(event: BlogEvent, state: BlogState): BlogState =
    event match {
      case PostAdded(postId, content) ⇒
        state.withContent(content)

      case BodyChanged(_, newBody) ⇒
        state.content match {
          case Some(c) ⇒ state.copy(content = Some(c.copy(body = newBody)))
          case None ⇒ state
        }

      case Published(_) ⇒
        state.copy(published = true)
    }

  def behavior: Behavior[BlogCommand] =
    PersistentActor.immutable[BlogCommand, BlogEvent, BlogState](
      persistenceId = "abc",
      initialState = BlogState.empty,
      actions = actions,
      applyEvent = applyEvent
    )
}
