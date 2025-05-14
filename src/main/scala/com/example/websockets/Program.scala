package com.example.websockets

import cats.effect.{IO, IOApp}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory


object Program extends IOApp.Simple {

  given logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  override def run: IO[Unit] = Server[IO]
}
