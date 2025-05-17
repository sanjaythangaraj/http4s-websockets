package com.example.websockets

import cats.data.Validated
import cats.syntax.all.*

def validateItem[F](
                     value: String,
                     userOrRoom: F,
                     name: String
                   ): Validated[String, F] =
  Validated.cond(
    value.length >= 2 && value.length <= 10,
    userOrRoom,
    s"$name must be between 2 and 10 characters"
  )

case class User(name: String)

object User {
  def apply(name: String): Validated[String, User] =
    validateItem(name, new User(name), "user name")
}

case class Room(room: String)

object Room {
  def apply(room: String): Validated[String, Room] =
    validateItem(room, new Room(room), "room")
}

case class ChatState(
                      userRooms: Map[User, Room],
                      roomMembers: Map[Room, Set[User]]
                    )

object ChatState {
  def apply(userRooms: Map[User, Room], roomMembers: Map[Room, Set[User]]) = {
    new ChatState(userRooms, roomMembers)
  }
}
