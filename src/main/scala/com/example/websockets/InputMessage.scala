package com.example.websockets

import cats.data.Validated
import cats.effect.Ref

trait InputMessage[F[_]] {
  def defaultRoom: Validated[String, Room]
  def parse(
           userRef: Ref[F, Option[User]],
           text: String
           ): F[List[OutputMessage]]
}

case class TextCommand(left: String, right: Option[String])
