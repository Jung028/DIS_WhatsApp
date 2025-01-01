package com.hep88

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.hep88.Upnp.AddPortMapping
import scalafx.collections.ObservableHashSet

case class User (name:String, ref: ActorRef[ChatClient.Command])

object ChatServer {
  //protocol
  sealed trait Command
  case class JoinChat(name: String, ref: ActorRef[ChatClient.Command]) extends Command
  case class Leave(name: String, from: ActorRef[ChatClient.Command]) extends Command


  //ServiceKey
  val ServerKey: ServiceKey[ChatServer.Command] = ServiceKey("Server")

  //Server State
  val members = new ObservableHashSet[User]()

  //update everyone in the chat about the new masterlist
  members.onChange((newSet, c) => {
    import com.hep88.ChatClient._
    val newList = MemberList(newSet.toList)
    for (user <- newSet){
      user.ref ! newList
    }
  })

  def apply(): Behavior[ChatServer.Command] =
    Behaviors.setup { context =>
      val upnpRef = context.spawn(Upnp(), Upnp.name)
      upnpRef ! AddPortMapping(20000)

      context.system.receptionist ! Receptionist.Register(ServerKey, context.self)

      Behaviors.receiveMessage { message =>
        message match {
          case JoinChat(name, ref) =>
            import com.hep88.ChatClient._
            ChatServer.members += User (name, ref)
            ref ! Joined(ChatServer.members.toList)
            Behaviors.same
          case Leave(name, from) =>
            members -= User(name, from)
            Behaviors.same


        }
      }
    }
}

object ServerApp extends App {
  val greeterMain: ActorSystem[ChatServer.Command] = ActorSystem(ChatServer(), "HelloSystem")

}