package com.bowlingx

import java.io.{ByteArrayInputStream, InputStreamReader, SequenceInputStream}
import java.nio.file.{FileSystems, Path, Paths, StandardWatchEventKinds}
import java.util
import javax.script.{Compilable, CompiledScript, ScriptEngine}

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.routing.{Broadcast, RoundRobinPool}
import com.bowlingx.actors.{RenderActor, UpdatedScript}
import com.bowlingx.providers.ScriptResources
import jdk.nashorn.api.scripting.NashornScriptEngineFactory
import play.api.Logger
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._
import scala.concurrent.duration._

trait EngineWatcher {

  protected val engine: ScriptEngine = new NashornScriptEngineFactory().getScriptEngine
  protected val vendorFiles: ScriptResources
  val actorSystem: ActorSystem
  val lifecycle: ApplicationLifecycle
  val renderInstances:Int
  val renderTimeout:FiniteDuration

  protected def bootstrap: ByteArrayInputStream

  val logger = Logger(this.getClass)

  protected var renderer: ActorRef = createRenderer(createCompiledScripts())

  def createRenderer(compiledScript:Try[CompiledScript]) : ActorRef = {
    actorSystem.actorOf(Props(
      new RenderActor(compiledScript, renderTimeout)).withRouter(RoundRobinPool(renderInstances)))
  }

  protected def createCompiledScripts(): Try[CompiledScript] = {
    Try {
      engine.asInstanceOf[Compilable].compile(new InputStreamReader(new SequenceInputStream(
        util.Collections.enumeration((bootstrap +: vendorFiles.resources.map(_.openStream())).asJava)
      )))
    }
  }

  protected def initScheduling()(implicit context: ExecutionContext): Unit = {
    val watch = FileSystems.getDefault.newWatchService()

    val uniqueFolders = vendorFiles.resources.map(r => Paths.get(r.toURI).getParent).distinct
    val fileNamesAllowedToTriggerChange = vendorFiles.resources.map(r => Paths.get(r.toURI).getFileName.toString)

    val keys = uniqueFolders.map(
      r => r.register(watch,
        StandardWatchEventKinds.ENTRY_MODIFY
      )
    )
    val scheduler = actorSystem.scheduler.schedule(0.second, 0.second) {
      // it's possible that the watcher has been closed already, so we ignore any exceptions
      val thisKeyOption = Try(Some(watch.poll())).toOption.flatten
      thisKeyOption.foreach(thisKey => {
        keys.find(_ == thisKey).foreach { k =>
          val events = k.pollEvents().asScala
          events.foreach(event => {
            if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
              val file = event.context().asInstanceOf[Path].toString
              if (fileNamesAllowedToTriggerChange.contains(file)) {
                logger.info(s"Bundle source file `$file` changed, recompiling...")
                createCompiledScripts() match {
                  case Success(script) =>
                    renderer ! Broadcast(UpdatedScript(script))
                  case Failure(ex) =>
                    logger.error("Fatal: Got Exception during script compile", ex)
                }
              }
            }
          })
          k.reset()
        }
      })
    }

    lifecycle.addStopHook(() => Future {
      watch.close()
      scheduler.cancel()
      renderer ! Broadcast(PoisonPill)
    })
  }

}
