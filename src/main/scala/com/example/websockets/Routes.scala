package com.example.websockets

import org.http4s.dsl.Http4sDsl
import org.http4s.*
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

class Routes[F[_] : Files : Concurrent : LoggerFactory] extends Http4sDsl[F] {
  def service(
               wsb: WebSocketBuilder[F],
               queue: Queue[F, WebSocketFrame],
               topic: Topic[F, WebSocketFrame]
             ): HttpApp[F] = {
    HttpRoutes.of[F] {
      case request@GET -> Root / "chat.html" =>
        StaticFile
          .fromPath(
            fs2.io.file.Path("src/main/resources/chat.html"),
            Some(request)
          )
          .getOrElseF(NotFound())

      case GET -> Root / "ws" =>
        val send: Stream[F, WebSocketFrame] = topic.subscribe(maxQueued = 1000)
        val receive: Pipe[F, WebSocketFrame, Unit] = _.foreach(queue.offer)

        wsb.build(send, receive)

    }.orNotFound
  }
}
