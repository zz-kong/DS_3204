package com.hep88.view

import akka.actor.typed.ActorRef
import scalafxml.core.macros.sfxml
import scalafx.event.ActionEvent
import scalafx.scene.control.{Alert, Label, ListView, TableColumn, TableView, TextField}
import com.hep88.ChatClient
import com.hep88.User
import com.hep88.Client
import javafx.beans.property.SimpleStringProperty
import scalafx.collections.ObservableBuffer
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.StringProperty
import scalafx.scene.control.Alert.AlertType

case class AvailableTask(taskNo: String, taskName: String, progress: String, personName: String)

@sfxml
class MainWindowController(private val txtName: TextField,
                           private val lblStatus: Label,
                           private val listUser: ListView[User],
                           private val tableTasks: TableView[AvailableTask],
                           private val lblSelectedTask: Label) {

  val selectedTask: StringProperty = new SimpleStringProperty("")


  var chatClientRef: Option[ActorRef[ChatClient.Command]] = None

  val receivedText: ObservableBuffer[String] =  new ObservableBuffer[String]()

  val availableTasks: ObservableBuffer[AvailableTask] = new ObservableBuffer[AvailableTask]()

  tableTasks.items = availableTasks

  //create a table column for task no
  val taskNoColumn = new TableColumn[AvailableTask, String]("No")
  taskNoColumn.cellValueFactory = { features =>
    new StringProperty(this, "taskNo", features.value.taskNo)
  }

  taskNoColumn.minWidth = 85
  taskNoColumn.prefWidth = 85
  taskNoColumn.prefWidth =  85

  // Create a TableColumn for the task name
  val taskNameColumn = new TableColumn[AvailableTask, String]("Tasks")
  taskNameColumn.cellValueFactory = { features =>
    new StringProperty(this, "taskName", features.value.taskName)
  }

  taskNameColumn.minWidth = 336
  taskNameColumn.prefWidth = 336
  taskNameColumn.maxWidth = 336

  // Create a TableColumn for the progress
  val progressColumn = new TableColumn[AvailableTask, String]("Progress")
  progressColumn.cellValueFactory = { features =>
    new StringProperty(this, "progress", features.value.progress)
  }

  progressColumn.minWidth = 85
  progressColumn.prefWidth = 85
  progressColumn.prefWidth = 85


  // Create a TableColumn for the person name
  val personNameColumn = new TableColumn[AvailableTask, String]("By")
  personNameColumn.cellValueFactory = { features =>
    new StringProperty(this, "personName", features.value.personName)
  }

  personNameColumn.minWidth = 85
  personNameColumn.prefWidth = 85
  personNameColumn.prefWidth = 85

  // Add the columns to the TableView
  tableTasks.columns.addAll(taskNoColumn, taskNameColumn, progressColumn, personNameColumn)

  //listen to the change(selected item)
  tableTasks.selectionModel().selectedItem.onChange { (_, _, newValue) =>
    // Check if newValue is not null before accessing its properties
    if (newValue != null) {
      selectedTask.set(newValue.taskNo)
      lblSelectedTask.text = selectedTask.get()
    }
  }


  def initializeTasks(): Unit = {
    val tasks = List(
      AvailableTask("Task 1", "Planning", "Available", "-"),
      AvailableTask("Task 2", "Design", "Available", "-"),
      AvailableTask("Task 3", "Implementation", "Available", "-"),
      AvailableTask("Task 4", "Integration", "Available", "-"),
      AvailableTask("Task 5", "Testing", "Available", "-")
    )

    availableTasks ++= tasks
  }

  //when client join
  def handleJoin(action: ActionEvent): Unit = {
    if (txtName != null)
      chatClientRef map (_ ! ChatClient.StartJoin(txtName.text()))
  }

  //when client leave
  def handleLeave(action: ActionEvent): Unit = {
    if (txtName != null)
      chatClientRef.foreach(_ ! ChatClient.LeaveChat(txtName.text()))
  }

  def clearTasks(): Unit = {
    availableTasks.clear()
    txtName.clear()
    lblStatus.text = "-"
    lblSelectedTask.text = "-"
  }

  def displayStatus(text: String): Unit = {
    lblStatus.text = text
  }

  def updateList(x: Iterable[User]): Unit = {
    listUser.items = new ObservableBuffer[User]() ++= x
  }

  def handleSelection(actionEvent: ActionEvent): Unit = {
    val selectedItem = tableTasks.selectionModel().getSelectedItem
    if (selectedItem != null) {
      // Update the selected task with new values
      val updatedTask = AvailableTask(selectedItem.taskNo, selectedItem.taskName, "In Progress", txtName.text())
      val index = availableTasks.indexOf(selectedItem)
      availableTasks.update(index, updatedTask)
    }
  }

  def handleCompleted(actionEvent: ActionEvent): Unit = {
    val selectedItem = tableTasks.selectionModel().getSelectedItem
    if (selectedItem != null) {
      //if the task is in progress
      if (selectedItem.progress == "In Progress") {
        //only person who perform the task can complete the task
        if (selectedItem.personName == txtName.text()) {
          // Update the selected task with new values
          val updatedTask = AvailableTask(selectedItem.taskNo, selectedItem.taskName, "Completed", txtName.text())
          val index = availableTasks.indexOf(selectedItem)
          availableTasks.update(index, updatedTask)

        } else {
          val title = StringProperty("Authorization Error")
          val content = StringProperty("You are not authorized to complete this task.")
          showAlert(title, content)
          println("You are not authorized to complete this task.")
        }
      } else {
        val title = StringProperty("Authorization Error")
        val content = StringProperty("You cannot complete an available task..")
        showAlert(title, content)
        println("You are not able to complete an available task.")
      }
    }
  }

  def showAlert(title: StringProperty, content: StringProperty): Unit = {
    val alert = new Alert(AlertType.Warning) {
      initOwner(tableTasks.scene().getWindow)
      title = title.value
      contentText = content.value
    }
    alert.showAndWait()
  }

  def updateTask(task: AvailableTask): Unit = {
    Platform.runLater {
      tableTasks.items = new ObservableBuffer[AvailableTask]() += task
    }
  }





  def addText(text: String): Unit = {
    receivedText += text
  }
}
