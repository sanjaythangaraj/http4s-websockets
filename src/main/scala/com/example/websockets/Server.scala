package com.example.websockets

import cats.syntax.all.*
import cats.effect.*
import cats.effect.std.Queue
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.*
import fs2.concurrent.Topic
import fs2.io.file.Files
import fs2.io.net.Network
import org.http4s.websocket.WebSocketFrame
import org.typelevel.log4cats.LoggerFactory

object Server {
  private def server[F[_]: Async: Files: Network: LoggerFactory](
      queue: Queue[F, OutputMessage],
      topic: Topic[F, OutputMessage],
      inputMessage: InputMessage[F],
      protocol: Protocol[F],
      cs: Ref[F, ChatState]
  ): F[Unit] = {
    val host = host"0.0.0.0"
    val port = port"8080"
    EmberServerBuilder
      .default[F]
      .withHost(host)
      .withPort(port)
      .withHttpWebSocketApp(wsb =>
        new Routes().service(wsb, queue, topic, inputMessage, protocol, cs)
      )
      .build
      .useForever
      .void
  }

  def apply[F[_]: Async: Files: Network: LoggerFactory](
      queue: Queue[F, OutputMessage],
      topic: Topic[F, OutputMessage],
      inputMessage: InputMessage[F],
      protocol: Protocol[F],
      cs: Ref[F, ChatState]
  ): F[Unit] = server(queue, topic, inputMessage, protocol, cs)
}
