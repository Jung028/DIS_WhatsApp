package com.hep88

import akka.actor.typed.{ActorRef, PostStop, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist,ServiceKey}
import akka.cluster.typed._
import akka.{ actor => classic }
import akka.actor.typed.scaladsl.adapter._
import scalafx.collections.ObservableHashSet
import scalafx.application.Platform
import akka.cluster.ClusterEvent.ReachabilityEvent
import akka.cluster.ClusterEvent.ReachableMember
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.ClusterEvent.MemberEvent
import akka.actor.Address
import com.hep88.Upnp._


object ChatClient {
  //protocol chat client server
  sealed trait Command
  case class Joined (list: Iterable[User]) extends Command
  case class MemberList (list: Iterable[User]) extends Command
  case class Message(msg: String, from: ActorRef[ChatClient.Command]) extends Command

  //protocol chat client Main App
  case class StartJoin (name: String) extends Command
  case class SendMessageL(target: ActorRef[ChatClient.Command], content: String) extends Command

  //start signal for client actor
  case object start extends Command
  //server discovery protocol
  final case object FindTheServer extends Command
  private case class ListingResponse(listing: Receptionist.Listing) extends Command

  //data property for Actor client
  val members = new ObservableHashSet[User]()
  val unreachables = new ObservableHashSet[Address]()
  var remoteOpt:Option[ActorRef[ChatServer.Command]] = None
  var defaultBehavior: Option[Behavior[ChatClient.Command]] = None
  var nameOpt: Option[String] = None



  unreachables.onChange { (ns, _) =>
    Platform.runLater {
      Client.control.updateList(members.toList.filter(y => !unreachables.exists(x => x == y.ref.path.address)))
    }
  }

  members.onChange { (ns, _) =>
    Platform.runLater {
      Client.control.updateList(ns.toList.filter(y => !unreachables.exists(x => x == y.ref.path.address)))
    }
  }

  def messageStarted(): Behavior[ChatClient.Command] = Behaviors.receive[ChatClient.Command] { (context, message) =>
    message match {
      case SendMessageL(target, content) =>
        target ! Message(content, context.self)
        Behaviors.same
      case Message(msg, from) =>
        Platform.runLater {
          Client.control.addText(msg)
        }
        Behaviors.same
      case MemberList(list: Iterable[User]) =>
        members.clear()
        members ++= list
        Behaviors.same
      case StartJoin(name) =>  // Handle StartJoin here
        nameOpt = Option(name)
        import com.hep88.ChatServer._
        for (remote <- remoteOpt) {
          remote ! JoinChat(name, context.self)
        }
        Behaviors.same
    }
  }.receiveSignal {
    case (context, PostStop) =>
      for (name <- nameOpt;
           remote <- remoteOpt) {
        remote ! ChatServer.Leave(name, context.self)
      }
      defaultBehavior.getOrElse(Behaviors.same)
  }


  def apply(): Behavior[ChatClient.Command] =
    Behaviors.setup { context =>
      val upnpRef = context.spawn(Upnp(), Upnp.name)
      upnpRef ! AddPortMapping(20000)

      // (1) a ServiceKey is a unique identifier for this actor

      // (2) create an ActorRef that can be thought of as a Receptionist
      // Listing “adapter.” this will be used in the next line of code.
      // the ChatClient.ListingResponse(listing) part of the code tells the
      // Receptionist how to get back in touch with us after we contact
      // it in Step 4 below.
      // also, this line of code is long, so i wrapped it onto two lines
      val listingAdapter: ActorRef[Receptionist.Listing] =
      context.messageAdapter { listing =>
        println(s"listingAdapter:listing: ${listing.toString}")
        ChatClient.ListingResponse(listing)
      }
      //(3) send a message to the Receptionist saying that we want
      // to subscribe to events related to ChatServer.ServerKey, which
      // represents the ChatClient actor.
      context.system.receptionist ! Receptionist.Subscribe(ChatServer.ServerKey, listingAdapter)

      defaultBehavior = Option( Behaviors.receiveMessage { message =>
        message match {
          case ChatClient.start =>
            context.self ! FindTheServer

            Behaviors.same
          // (4) send a Find message to the Receptionist, saying
          // that we want to find any/all listings related to
          // Mouth.MouthKey, i.e., the Mouth actor.
          case FindTheServer =>
            println(s"Clinet Hello: got a FindTheServer message")
            context.system.receptionist !
              Receptionist.Find(ChatServer.ServerKey, listingAdapter)

            Behaviors.same
          // (5) after Step 4, the Receptionist sends us this
          // ListingResponse message. the `listings` variable is
          // a Set of ActorRef of type ChatServer.Command, which
          // you can interpret as “a set of ChatServer ActorRefs.” for
          // this example i know that there will be at most one
          // ChatServer actor, but in other cases there may be more
          // than one actor in this set.
          case ListingResponse(ChatServer.ServerKey.Listing(listings)) =>
            val xs: Set[ActorRef[ChatServer.Command]] = listings
            for (x <- xs) {
              remoteOpt = Some(x)
            }
            Behaviors.same

          case Joined(list) =>
            Platform.runLater {
              Client.control.displayStatus("joined")
            }
            members.clear()
            members ++= list
            messageStarted()

          case MemberList(list) =>
            members.clear()
            members ++= list
            Behaviors.same


          case StartJoin(name) =>
            nameOpt = Option(name)
            import com.hep88.ChatServer._
            for (remote <- remoteOpt){
              remote ! JoinChat(name, context.self)
            }
            Behaviors.same

        }
      })
      defaultBehavior.get
    }
}

