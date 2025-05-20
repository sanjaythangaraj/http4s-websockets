package com.example.websockets

import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpApp, HttpRoutes, StaticFile}
import cats.*
import cats.implicits.*
import cats.effect.*
import cats.effect.std.Queue
import fs2.*
import fs2.concurrent.Topic
import fs2.io.file.Files
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.typelevel.log4cats.LoggerFactory
import scala.concurrent.duration.*
import org.http4s.MediaType
import org.http4s.headers.`Content-Type`

class Routes[F[_]: Files: Temporal: LoggerFactory] extends Http4sDsl[F] {
  def service(
      wsb: WebSocketBuilder[F],
      queue: Queue[F, OutputMessage],
      topic: Topic[F, OutputMessage],
      inputMessage: InputMessage[F],
      protocol: Protocol[F],
      chatState: Ref[F, ChatState]
  ): HttpApp[F] = {
    HttpRoutes
      .of[F] {
        case request @ GET -> Root / "chat.html" =>
          StaticFile
            .fromPath(
              fs2.io.file.Path("src/main/resources/chat.html"),
              Some(request)
            )
            .getOrElseF(NotFound())

        case GET -> Root / "metrics" =>
          def currentState: F[String] = {
            chatState.get.map { cs =>
              s"""
                 |<!Doctype html>
                 |<title>Chat Server State</title>
                 |<body>
                 |<pre>Users: ${cs.userRooms.keys.size}</pre>
                 |<pre>Rooms: ${cs.roomMembers.keys.size}</pre>
                 |<pre>Overview:
                 |${cs.roomMembers.keys.toList
                  .map { room =>
                    cs.roomMembers
                      .getOrElse(room, Set.empty[User])
                      .map(_.name)
                      .toList
                      .sorted
                      .mkString(s"${room.room} Room Members:\n\t", "\n\t", "")
                  }
                  .mkString("Rooms:\n\t", "\n\t", "")}
              |</pre>
              |</body>
              |</html>""".stripMargin
            }
          }

          currentState.flatMap { currState =>
            Ok(currState, `Content-Type`(MediaType.text.html))
          }

        case GET -> Root / "ws" =>
          for {
            uRef   <- Ref.of[F, Option[User]](None)
            uQueue <- Queue.unbounded[F, OutputMessage]
            ws <- wsb.build(
              send(topic, uQueue, uRef),
              receive(protocol, inputMessage, uRef, queue, uQueue)
            )
          } yield ws

      }
      .orNotFound
  }

  private def handleWebSocketStream(
      wsfStream: Stream[F, WebSocketFrame],
      inputMessage: InputMessage[F],
      protocol: Protocol[F],
      uRef: Ref[F, Option[User]]
  ): Stream[F, OutputMessage] = {
    for {
      wsf <- wsfStream
      om <- Stream.evalSeq(
        wsf match {
          case WebSocketFrame.Text(text, _) =>
            inputMessage.parse(uRef, text)
          case WebSocketFrame.Close(_) =>
            protocol.disconnect(uRef)
        }
      )
    } yield om
  }

  private def filterMsg(
      msg: OutputMessage,
      userRef: Ref[F, Option[User]]
  ): F[Boolean] = {
    msg match {
      case DiscardMessage => false.pure[F]
      case sendToUser @ SendToUser(_, _) =>
        userRef.get.map { _.fold(false)(u => sendToUser.forUser(u)) }
      case chatMsg @ ChatMsg(_, _, _) =>
        userRef.get.map { _.fold(false)(u => chatMsg.forUser(u)) }
      case _ => true.pure[F]
    }
  }

  private def processMsg(msg: OutputMessage): WebSocketFrame = {
    msg match {
      case KeepAlive => WebSocketFrame.Ping()
      case msg @ _   => WebSocketFrame.Text(msg.asJson.noSpaces)
    }
  }

  private def receive(
      protocol: Protocol[F],
      inputMessage: InputMessage[F],
      uRef: Ref[F, Option[User]],
      queue: Queue[F, OutputMessage],
      uQueue: Queue[F, OutputMessage]
  ): Pipe[F, WebSocketFrame, Unit] = { wsfStream =>
    handleWebSocketStream(wsfStream, inputMessage, protocol, uRef)
      .evalMap { om =>
        uRef.get.flatMap {
          case Some(_) =>
            queue.offer(om)
          case None =>
            uQueue.offer(om)
        }
      }
      .concurrently {
        Stream
          .awakeEvery(30.seconds)
          .map(_ => KeepAlive)
          .foreach(uQueue.offer)
      }
  }

  private def send(
      topic: Topic[F, OutputMessage],
      uQueue: Queue[F, OutputMessage],
      uRef: Ref[F, Option[User]]
  ): Stream[F, WebSocketFrame] = {
    def uStream =
      Stream
        .fromQueueUnterminated(uQueue)
        .filter {
          case DiscardMessage => false
          case _              => true
        }
        .map(processMsg)

    def mainStream =
      topic
        .subscribe(maxQueued = 1000)
        .evalFilter(filterMsg(_, uRef))
        .map(processMsg)

    Stream(uStream, mainStream).parJoinUnbounded
  }
}
