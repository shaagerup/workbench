package com.lihaoyi.workbench
import scala.concurrent.ExecutionContext.Implicits.global
import sbt._
import sbt.Keys._
import autowire._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.core.tools.io._
import org.scalajs.sbtplugin.ScalaJSPluginInternal._
import org.scalajs.sbtplugin.Implicits._
//import scala.collection._

import org.apache.logging.log4j.message._

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.{ LogEvent => Log4JLogEvent, _ }
import org.apache.logging.log4j.core.Filter.Result
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout


object WorkbenchBasePlugin extends AutoPlugin {

  override def requires = ScalaJSPlugin

  object autoImport {
    val localUrl = settingKey[(String, Int)]("localUrl")
  }
  import autoImport._
  import ScalaJSPlugin.AutoImport._

  val server = settingKey[Server]("local websocket server")


  lazy val replHistory = collection.mutable.Buffer.empty[String]

  val workbenchSettings = Seq(
    localUrl := ("localhost", 12345),
    (extraLoggers in ThisBuild) := {
      val clientLogger = new AbstractAppender(
        "FakeAppender", 
        null,
        PatternLayout.createDefaultLayout()) 
      {
        override def append(event: Log4JLogEvent): Unit = {

          val level = sbt.internal.util.ConsoleAppender.toLevel(event.getLevel)
          val message = event.getMessage

          message match {
            case o: ObjectMessage        => {
              o.getParameter match {
                case e : sbt.internal.util.StringEvent => server.value.Wire[Api].print(level.toString, e.message).call()
                case e : sbt.internal.util.ObjectEvent[_] => server.value.Wire[Api].print(level.toString, e.message.toString).call()
                case _ => server.value.Wire[Api].print(level.toString, message.getFormattedMessage()).call()
              }
            }
            case _ => server.value.Wire[Api].print(level.toString, message.getFormattedMessage()).call()
          }
        }
      }
      clientLogger.start()
      val currentFunction = extraLoggers.value
      (key: ScopedKey[_]) => clientLogger +: currentFunction(key)
    },
    server := new Server(localUrl.value._1, localUrl.value._2),
    (onUnload in Global) := { (onUnload in Global).value.compose{ state =>
      server.value.kill()
      state
    }}
  )

  private def getScopeId(scope: ScopeAxis[sbt.Reference]):String = {
     "" + scope.hashCode()
  }
  override def projectSettings = workbenchSettings

}
