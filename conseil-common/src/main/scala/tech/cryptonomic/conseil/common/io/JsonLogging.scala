package tech.cryptonomic.conseil.common.io

import io.circe.Json
import io.youi.client.HttpClient
import io.youi.http.content.Content
import io.youi.net._
import perfolation._
import profig.JsonUtil
import scribe.logstash.LogstashRecord
import scribe.Execution.global
import scribe.output.format.OutputFormat
import scribe.output.{EmptyOutput, LogOutput, TextOutput}
import scribe.writer.Writer
import scribe.{LogRecord, MDC}
import java.net.{URL => NetUrl}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/** Configurations to format log entries as json.
  * It's based on the logstash schema, which we assume is in turn based on the
  * the generic Elastic Common Schema: https://www.elastic.co/guide/en/ecs-logging/java/current/intro.html
  */
object JsonLogging {
  /* The JsonWriter is a minor variation of the existing scribe `LogstashWriter`
   * You can find the references here: https://github.com/outr/scribe/wiki/logstash
   *
   * We modified the writer to make the logstash endpoint optional, falling back to the console
   * writer.
   */

  /** A scribe writer that creates json log entries and either append them to the console
    * or sends them to a specific logstash entry-point
    */
  case class JsonWriter(
      service: String,
      additionalFields: Map[String, String] = Map.empty,
      asynchronous: Boolean = true,
      logstash: Option[NetUrl] = None
  ) extends Writer {
    private lazy val client = logstash.map { url =>
      HttpClient
        .url(
          URL(url.toExternalForm())
        )
        .post
    }

    override def write[M](record: LogRecord[M], output: LogOutput, outputFormat: OutputFormat): Unit = {
      val future = log(record)
      if (!asynchronous) {
        Await.result(future, 10.seconds)
        ()
      }
    }

    def log[M](record: LogRecord[M]): Future[Any] = {
      val l = record.timeStamp
      val timestamp = s"${l.t.F}T${l.t.T}.${l.t.L}${l.t.z}"
      val r = LogstashRecord(
        message = record.logOutput.plainText,
        service = service,
        level = record.level.name,
        value = record.levelValue,
        throwable = record.throwable.map(LogRecord.throwable2LogOutput(EmptyOutput, _).plainText),
        fileName = record.fileName,
        className = record.className,
        methodName = record.methodName,
        line = record.line,
        thread = record.thread.getName,
        `@timestamp` = timestamp,
        mdc = MDC.map.map { case (key, function) =>
          key -> function().toString
        },
        data = record.data.map { case (key, function) =>
          key -> function().toString
        }
      )

      val jsonObj = JsonUtil.toJson(r).asObject.get
      val jsonWithFields = additionalFields.foldLeft(jsonObj) { (obj, field) =>
        obj.add(field._1, Json.fromString(field._2))
      }
      val json = Json.fromJsonObject(jsonWithFields).noSpaces

      /* choose where to send the output */
      client match {
        case None =>
          Future.successful(
            scribe.Platform.consoleWriter.write(record, new TextOutput(json), OutputFormat.default)
          )
        case Some(cl) =>
          val content = Content.string(json, ContentType.`application/json`)
          cl.content(content).send()
      }

    }
  }
}
