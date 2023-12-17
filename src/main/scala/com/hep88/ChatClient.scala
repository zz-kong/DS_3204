package com.hep88
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import ChatClient.Command
import akka.actor.Address
import com.hep88.Upnp
import com.hep88.Upnp.AddPortMapping
import com.hep88.view.AvailableTask
import scalafx.application.Platform
import scalafx.collections.ObservableHashSet

//data structure
case class User(name: String, ref:ActorRef[ChatClient.Command])

object ChatClient {
  //chat client protocol
  sealed trait Command
  case object start extends Command


  //find the chat server
  final case object FindTheServer extends Command
  private case class ListingResponse(listing: Receptionist.Listing) extends Command

  //chat protocol
  case class Joined(lists: Iterable[User]) extends Command
  case class MemberList(lists: Iterable[User]) extends Command
  //The reason for two list is because, we will only send the joined message for 1 time
  //We have to keep the client updated about the new user joined

  case class LeaveChat(username: String) extends Command
  case class TaskUpdateToClient(task: AvailableTask) extends Command

  case class StartJoin(name: String) extends Command
  case class SendMessageL(target: ActorRef[ChatClient.Command], content: String) extends Command
  case class Message(msg: String, from: ActorRef[ChatClient.Command]) extends Command

  //chat client value
  var nameOpt: Option[String] = None

  //Chat client hash set
  val members = new ObservableHashSet[User]()
  val unreachables = new ObservableHashSet[Address]()
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

  var remoteOpt:Option[ActorRef[ChatServer.Command]] = None
  var defaultBehaviour: Option[Behavior[ChatClient.Command]] = None

  def messageStarted(): Behavior[ChatClient.Command] = Behaviors.receive[ChatClient.Command]{
    (context, message) =>
      message match {
        case SendMessageL(target, content) =>
          target ! Message(content, context.self)
          Behaviors.same
        case Message(msg, frm) =>
          Platform.runLater{
            Client.control.addText(msg)
          }
          Behaviors.same
        case MemberList(list: Iterable[User])=>
          members.clear()
          members ++= list
          Behaviors.same

        case LeaveChat(username) =>
          if (members.exists(_.name == username)) {
            members.remove(User(username, context.self))
            // send notification to server
            remoteOpt.foreach(_ ! ChatServer.Leave(username, context.self))

            // Update the UI to reflect the user leaving
            Platform.runLater {
              Client.control.updateList(members.toList)
              Client.control.clearTasks()

            }
          }
          Behaviors.same

        case TaskUpdateToClient(task) =>
          println(s"Received TaskUpdateToClient: $task")
          // Update the task list with the received task
          Platform.runLater {
            Client.control.updateTask(task)
          }
          Behaviors.same

      }
  }.receiveSignal{
    case (context, PostStop) =>
      for (name <- nameOpt;
           remote <- remoteOpt) {
        remote ! ChatServer.Leave(name, context.self)
      }
      defaultBehaviour.getOrElse(Behaviors.same)

  }


  def apply(): Behavior[ChatClient.Command] =
    Behaviors.setup { context =>
      //val UpnpRef = context.spawn(Upnp(), Upnp.name)
      //UpnpRef ! AddPortMapping(20000)

      // (1) a ServiceKey is a unique identifier for this actor


      // (2) create an ActorRef that can be thought of as a Receptionist
      // Listing “adapter.” this will be used in the next line of code.
      // the ClientHello.ListingResponse(listing) part of the code tells the
      // Receptionist how to get back in touch with us after we contact
      // it in Step 4 below.
      // also, this line of code is long, so i wrapped it onto two lines
      val listingAdapter: ActorRef[Receptionist.Listing] =
      context.messageAdapter { listing =>
        println(s"listingAdapter:listing: ${listing.toString}")
        ChatClient.ListingResponse(listing)
      }
      //(3) send a message to the Receptionist saying that we want
      // to subscribe to events related to ServerHello.ServerKey, which
      // represents the ClientHello actor.
      context.system.receptionist ! Receptionist.Subscribe(ChatServer.ServerKey, listingAdapter)

      defaultBehaviour = Option(Behaviors.receiveMessage { message =>
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
          // a Set of ActorRef of type ServerHello.Command, which
          // you can interpret as “a set of ServerHello ActorRefs.” for
          // this example i know that there will be at most one
          // ServerHello actor, but in other cases there may be more
          // than one actor in this set.
          case ListingResponse(ChatServer.ServerKey.Listing(listings)) =>
            val xs: Set[ActorRef[ChatServer.Command]] = listings
            for (x <- xs) {
              remoteOpt = Some(x)
            }
            Behaviors.same
          case Joined(list)=>
            Platform.runLater{
              Client.control.displayStatus("Joined")
              Client.control.initializeTasks()
            }
            members.clear()
            members ++= list
            messageStarted()

          case MemberList(list)=>
            println("User List")
            for(user <- list){
              println(user)
            }
            println("End of user list")
            Behaviors.same

          case StartJoin(name)=>
            nameOpt = Option(name)
            import com.hep88.ChatServer._
            for (remote <-remoteOpt) {
              remote ! JoinChat(nameOpt.get, context.self)
            }
            Behaviors.same


          case LeaveChat(username) =>
            if (members.exists(_.name == username)) {
              members.remove(User(username, context.self))
              // send notification to server
              remoteOpt.foreach(_ ! ChatServer.Leave(username, context.self))
              Platform.runLater {
                Client.control.displayStatus("Leave")
                Client.control.updateList(members.toList)
                Client.control.clearTasks()
              }
            }
            Behaviors.same

        }
      })
      defaultBehaviour.get
    }



}

