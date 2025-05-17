package com.example.websockets

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.{IO, IOApp}
import org.http4s.websocket.WebSocketFrame
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import fs2.Stream
import fs2.concurrent.Topic
import scala.concurrent.duration.*


object Program extends IOApp.Simple {

  given logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  def program: IO[Unit] = {
    for {
      queue <- Queue.unbounded[IO, WebSocketFrame]
      topic <- Topic[IO, WebSocketFrame]
      s <- Stream(
        Stream.fromQueueUnterminated(queue).through(topic.publish),
        Stream.awakeEvery[IO](30.seconds)
          .map(_ => WebSocketFrame.Ping())
          .through(topic.publish),
        Stream.eval(Server[IO](queue, topic))
      ).parJoinUnbounded.compile.drain
    } yield s
  }

  override def run: IO[Unit] = program
}
