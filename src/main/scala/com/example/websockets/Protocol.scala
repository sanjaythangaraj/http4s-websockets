package com.example.websockets

import cats.{Applicative, Monad}
import cats.syntax.all.*
import cats.data.Validated
import cats.data.Validated.Valid
import cats.data.Validated.Invalid
import cats.effect.Ref

trait Protocol[F[_]] {
  def register(name: String): F[OutputMessage]

  def isUsernameInUse(name: String): F[Boolean]

  def enterRoom(user: User, room: Room): F[List[OutputMessage]]

  def chat(user: User, text: String): F[List[OutputMessage]]

  def help(user: User): F[OutputMessage]

  def listRooms(user: User): F[List[OutputMessage]]

  def listMembers(user: User): F[List[OutputMessage]]

  def disconnect(userRef: Ref[F, Option[User]]): F[List[OutputMessage]]
}


object Protocol {
  def make[F[_] : Monad](chatState: Ref[F, ChatState]): Protocol[F] = new Protocol[F] {

    override def register(name: String): F[OutputMessage] = {
      User(name) match
        case Valid(user) => SuccessfulRegistration(user).pure[F]
        case Invalid(error) => ParsingError(None, error).pure[F]
    }

    override def isUsernameInUse(name: String): F[Boolean] = {
      chatState.get.map { cs =>
        cs.userRooms.keySet.exists(_.name == name)
      }
    }

    override def enterRoom(user: User, room: Room): F[List[OutputMessage]] = {
      chatState.get.flatMap { cs =>
        cs.userRooms.get(user) match {
          case Some(`room`) => List(
            SendToUser(user, s"You are already in the ${room.room} room")
          ).pure[F]
          case Some(_) =>
            val wLeaveMsgs = removeFromCurrentRoom(chatState, user)
            val wEnterMsgs = addToRoom(chatState, user, room)
            for {
              leaveMsgs <- wLeaveMsgs
              enterMsgs <- wEnterMsgs
            } yield leaveMsgs ++ enterMsgs
          case None => addToRoom(chatState, user, room)
        }
      }
    }

    override def chat(user: User, text: String): F[List[OutputMessage]] =
      for {
        cs <- chatState.get
        output <- cs.userRooms.get(user) match {
          case Some(room) => broadcastMessage(cs, room, ChatMsg(user, user, text))
          case None => List(SendToUser(user, "You are not  in a room")).pure[F]
        }
      } yield output

    override def help(user: User): F[OutputMessage] = {
      val text =
        """Commands:
          | /help             - Show this text
          | /room             - Change to default/entry room
          | /room <room name> - Change to the specified room
          | /rooms            - List all rooms
          | /members          - List members in current room
          |""".stripMargin
      SendToUser(user, text).pure[F]
    }

    override def listRooms(user: User): F[List[OutputMessage]] =
      chatState.get.map { cs =>
        val roomList =
          cs.roomMembers.keys
            .map(_.room)
            .toList
            .sorted
            .mkString("Rooms:\n\t", "\n\t", "")
        List(SendToUser(user, roomList))
      }

    override def listMembers(user: User): F[List[OutputMessage]] =
      chatState.get.map { cs =>
        val membersList =
          cs.userRooms.get(user) match
            case Some(room) =>
              cs.roomMembers
                .getOrElse(room, Set.empty[User])
                .map(_.name)
                .toList
                .sorted
                .mkString("Room Members:\n\t", "\n\t", "")
            case None => "You are not currently in a room"
        List(SendToUser(user, membersList))
      }

    override def disconnect(userRef: Ref[F, Option[User]]): F[List[OutputMessage]] =
      userRef.get.flatMap {
        case Some(user) => removeFromCurrentRoom(chatState, user)
        case None => List.empty[OutputMessage].pure[F]
      }
  }

  private def removeFromCurrentRoom[F[_] : Monad](
                                                   chatState: Ref[F, ChatState],
                                                   user: User
                                                 ): F[List[OutputMessage]] =
    chatState.get.flatMap { cs =>
      cs.userRooms.get(user) match {
        case Some(room) =>
          val updatedMembersSet = cs.roomMembers.getOrElse(room, Set.empty[User]) - user
          chatState.update { ccs =>
            ChatState(
              ccs.userRooms - user,
              if (updatedMembersSet.isEmpty) ccs.roomMembers - room
              else ccs.roomMembers + (room -> updatedMembersSet)
            )
          }.flatMap { _ =>
            broadcastMessage(cs, room, SendToUser(user, s"${user.name} has left the ${room.room} room"))
          }
        case None => List.empty[OutputMessage].pure[F]
      }

    }

  private def addToRoom[F[_] : Monad](
                                       chatState: Ref[F, ChatState],
                                       user: User,
                                       room: Room
                                     ): F[List[OutputMessage]] =
    chatState.updateAndGet(cs =>
      val updatedMembersSet = cs.roomMembers.getOrElse(room, Set.empty[User]) + user
      ChatState(
        cs.userRooms + (user -> room),
        cs.roomMembers + (room -> updatedMembersSet)
      )
    ).flatMap {
      broadcastMessage(_, room, SendToUser(user, s"${user.name} has joined the ${room.room} room"))
    }

  private def broadcastMessage[F[_] : Applicative](
                                                    cs: ChatState,
                                                    room: Room,
                                                    outputMessage: OutputMessage
                                                  ): F[List[OutputMessage]] =
    cs.roomMembers
      .getOrElse(room, Set.empty[User])
      .map { user =>
        outputMessage match {
          case SendToUser(_, msg) => SendToUser(user, msg)
          case ChatMsg(from, _, msg) => ChatMsg(from, user, msg)
          case _ => DiscardMessage
        }
      }
      .toList
      .pure[F]
}