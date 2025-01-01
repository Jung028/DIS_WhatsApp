package com.hep88

import akka.cluster.typed._
import akka.{ actor => classic }
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.adapter._
import com.typesafe.config.ConfigFactory
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafxml.core.{FXMLLoader, NoDependencyResolver}
import scalafx.Includes._
import scala.concurrent.Future
import scala.concurrent.duration._

object Client extends JFXApp {
  val greeterMain: ActorSystem[ChatClient.Command] = ActorSystem(ChatClient(), "HelloSystem")
  //starting the finding of chat server actor
  greeterMain ! ChatClient.start

  val loader = new FXMLLoader(null, NoDependencyResolver)
  loader.load(getClass.getResourceAsStream("view/test.fxml"))
  val border: scalafx.scene.layout.BorderPane = loader.getRoot[javafx.scene.layout.BorderPane]()
  val control = loader.getController[com.hep88.view.MainWindowController#Controller]()
  control.chatClientRef = Option(greeterMain)
  val cssResource = getClass.getResource("view/DarkTheme.css")
  stage = new PrimaryStage() {
    scene = new Scene(){
      root = border
      stylesheets = List(cssResource.toExternalForm)
    }
  }

  stage.onCloseRequest = handle( {
    greeterMain.terminate
  })
}