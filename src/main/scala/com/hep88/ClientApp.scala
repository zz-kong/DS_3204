package com.hep88

import akka.actor.typed.ActorSystem
import com.hep88.view.AvailableTask
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafxml.core.{FXMLLoader, NoDependencyResolver}
import scalafx.Includes._


object Client extends JFXApp{
  val greeterMain: ActorSystem[ChatClient.Command] = ActorSystem(ChatClient(), "ChatSystem")
  greeterMain ! ChatClient.start
  val loader = new FXMLLoader(null, NoDependencyResolver)
  loader.load(getClass.getResourceAsStream("view/MainWindow.fxml"))
  val border: scalafx.scene.layout.BorderPane = loader.getRoot[javafx.scene.layout.BorderPane]()
  val control = loader.getController[com.hep88.view.MainWindowController#Controller]()
  control.chatClientRef = Option(greeterMain)
  //val cssResource = getClass.getResource("view/DarkTheme.css")
  stage = new PrimaryStage() {
    scene = new Scene() {
      root = border
      //stylesheets = List(cssResource.toExternalForm)
    }
  }

  def updateTask(task: AvailableTask): Unit = {
    println("Being triggered")
    // Find the task in the existing task list and update it
    val index = control.availableTasks.indexWhere(_.taskNo == task.taskNo)
    if (index != -1) {
      control.availableTasks.update(index, task)
    }
  }

  stage.onCloseRequest = handle({
    greeterMain.terminate
  })
}