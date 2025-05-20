package com.example.websockets

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.{IO, IOApp}
import cats.effect.Ref
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
      queue <- Queue.unbounded[IO, OutputMessage]
      topic <- Topic[IO, OutputMessage]
      cs <- Ref.of[IO, ChatState](ChatState(Map.empty, Map.empty))
      protocol <- IO(Protocol.make[IO](cs))
      im <- IO(InputMessage.make[IO](protocol))
      s <- Stream(
        Stream.fromQueueUnterminated(queue).through(topic.publish),
        Stream.awakeEvery[IO](30.seconds)
          .map(_ => KeepAlive)
          .through(topic.publish),
        Stream.eval(Server[IO](queue, topic, im, protocol, cs))
      ).parJoinUnbounded.compile.drain
    } yield s
  }

  override def run: IO[Unit] = program
}
