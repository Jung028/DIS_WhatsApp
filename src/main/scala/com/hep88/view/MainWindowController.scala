package com.hep88.view

import akka.actor.typed.ActorRef
import scalafxml.core.macros.sfxml
import scalafx.event.ActionEvent
import scalafx.scene.control.{Label, ListView, TextField}
import com.hep88.ChatClient
import com.hep88.User
import com.hep88.Client
import javafx.fxml.FXML
import scalafx.collections.ObservableBuffer
import scalafx.Includes._

@sfxml
class MainWindowController(@FXML private val txtName: TextField,
                           @FXML private val lblStatus: Label,
                           @FXML private val listUser: ListView[User],
                           @FXML private val listMessage: ListView[String],
                           @FXML private val txtMessage: TextField) {

    var chatClientRef: Option[ActorRef[ChatClient.Command]] = None

    val receivedText: ObservableBuffer[String] =  new ObservableBuffer[String]()

    listMessage.items = receivedText

  def handleJoin(action: ActionEvent): Unit = {
      if(txtName != null)
        chatClientRef map((x)=> x ! ChatClient.StartJoin(txtName.text.value))
  }

  def displayStatus(text: String): Unit = {
      lblStatus.text = text
  }
  def updateList(x: Iterable[User]): Unit ={
    listUser.items = new ObservableBuffer[User]() ++= x
  }
  def handleSend(actionEvent: ActionEvent): Unit ={

    if (listUser.selectionModel().selectedIndex.value >= 0){
      Client.greeterMain ! ChatClient.SendMessageL(listUser.selectionModel().selectedItem.value.ref,
        txtMessage.text())
    }
  }
  def addText(text: String): Unit = {
      receivedText += text
  }

}