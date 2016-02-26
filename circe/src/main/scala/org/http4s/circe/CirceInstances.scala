/*
 * Portions of this extracted from Circe project.
 * https://raw.githubusercontent.com/travisbrown/circe/v0.3.0/core/shared/src/main/scala/io/circe/Printer.scala
 * License: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.http4s
package circe

import org.log4s.getLogger

import cats.free.Trampoline
import io.circe.Json._
import io.circe._
import io.circe.jawn.CirceSupportParser.facade
import org.http4s.EntityEncoder.Entity
import org.http4s.headers.`Content-Type`
import scodec.bits.ByteVector
import scala.annotation.{switch, tailrec}
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process._

// Originally based on ArgonautInstances
trait CirceInstances {
  private[this] val log = getLogger

  implicit val json: EntityDecoder[Json] = org.http4s.jawn.jawnDecoder(facade)

  def jsonOf[A](implicit decoder: Decoder[A]): EntityDecoder[A] =
    json.flatMapR { json =>
      decoder.decodeJson(json).fold(
        failure =>
          DecodeResult.failure(InvalidMessageBodyFailure(s"Could not decode JSON: $json", Some(failure))),
        DecodeResult.success(_)
      )
    }

  implicit val jsonEncoder: EntityEncoder[Json] = {
    val ChunkSize = 50000

    EntityEncoder.encodeBy(`Content-Type`(MediaType.`application/json`)) { json: Json =>
      def contentLength(json: Json) = {
        def go(json: Json): Int =
          json.fold(
            4,
            b => if (b) 4 else 5,
            n => n.toString.length,
            s => renderedLength(s),
            arr => if (arr.isEmpty) 2 else arr.foldLeft(1 + arr.size)(_ + go(_)),
            obj => if (obj.isEmpty) 2
            else obj.toList.foldLeft(1 + 2 * obj.size) { case (acc, (k, v)) =>
              acc + renderedLength(k) + go(v)
            }
          )
        go(json)
      }

      def body(json: Json): EntityBody = {
        val builder = new StringBuilder()

        def flush: EntityBody = {
          val chunk = emit(ByteVector.encodeUtf8(builder.toString).fold(throw _, identity))
          builder.clear()
          chunk
        }

        def write(s: String, p: => EntityBody) = {
          builder.append(s)
          if (builder.size > ChunkSize)
            flush ++ p
          else
            p
        }

        def writeArray(array: List[Json], p: => EntityBody): EntityBody = array match {
          case Nil =>
            write("[]", p)
          case arr =>
            write("[", {
              def loop(xs: List[Json]): EntityBody = {
                writeJson(xs.head, Process.suspend {
                  if (xs.tail.nonEmpty) {
                    builder.append(",")
                    loop(xs.tail)
                  }
                  else {
                    builder.append("]")
                    p
                  }
                })
              }
              loop(arr)
            })
        }

        def writeObject(fields: List[(String, Json)], p: => EntityBody): EntityBody = fields match {
          case Nil =>
            write("{}", p)
          case fields =>
            write(s"{", {
              def loop(xs: List[(String, Json)]): EntityBody = {
                write(s"${renderJsonString(xs.head._1)}:",
                  writeJson(xs.head._2, Process.suspend {
                    if (xs.tail.nonEmpty) {
                      builder.append(",")
                      loop(xs.tail)
                    }
                    else {
                      builder.append("}")
                      p
                    }
                  })
                )
              }
              loop(fields)
            })
        }

        def writeJson(json: Json, p: => EntityBody): EntityBody = {
          json.fold(
            write("null", p),
            b => write(if (b) "true" else "false", p),
            n => write(n.toString, p),
            s => write(renderJsonString(s), p),
            arr => writeArray(arr, p),
            obj => writeObject(obj.toList, p)
          )
        }
        writeJson(json, flush)
      }

      Task.delay {
        body(json).step match {
          case step@Step(head, cont) =>
            head match {
              case emit @ Emit(Seq(bv)) if bv.size < ChunkSize =>
                log.debug("Emitted less than a full chunk.  No need to calculate content-length.")
                Entity(emit, Some(bv.size))
              case _ =>
                log.debug("Multi-chunk JSON output.  Calculating Content-Length")
                Entity(step.toProcess, Some(contentLength(json)))
            }
          case halt: Halt =>
            log.debug("Empty body.  No need to calculate content-length")
            Entity(halt, Some(0))
        }
      }
    }
  }


  def renderJsonString(string: String): String = {
    val builder = new StringBuilder("\"")
    @tailrec
    def appendJsonString(jsonString: String, normalChars: Boolean = true): Unit = {
      if (normalChars) {
        jsonString.span(isNormalChar) match {
          case (prefix, suffix) =>
            builder.append(prefix)
            if (suffix.nonEmpty) appendJsonString(suffix, normalChars = false)
        }
      } else {
        jsonString.span(c => !isNormalChar(c)) match {
          case (prefix, suffix) => {
            prefix.foreach { c => builder.append(escape(c)) }
            if (suffix.nonEmpty) appendJsonString(suffix, normalChars = true)
          }
        }
      }
    }
    appendJsonString(string)
    builder.append("\"").toString
  }

  private final def renderedLength(s: String): Int = {
    var len = 2
    var i = 0
    while (i < s.length) {
      len += ((s.charAt(i): @switch) match {
        case '\\' => 2
        case '"' => 2
        case '\b' => 2
        case '\f' => 2
        case '\n' => 2
        case '\r' => 2
        case '\t' => 2
        case ch =>
          // Based loosely on http://stackoverflow.com/a/8512877
          if (Character.isISOControl(ch)) 6
          else if (ch < 0x80) 1
          else if (ch < 0x800) 2
          else if (Character.isHighSurrogate(ch)) { i += 1; 4 }
          else 3
      })
      i += 1
    }
    len
  }

  private final def escape(c: Char): String = (c: @switch) match {
    case '\\' => "\\\\"
    case '"' => "\\\""
    case '\b' => "\\b"
    case '\f' => "\\f"
    case '\n' => "\\n"
    case '\r' => "\\r"
    case '\t' => "\\t"
    case possibleUnicode => if (Character.isISOControl(possibleUnicode)) {
      "\\u%04x".format(possibleUnicode.toInt)
    } else possibleUnicode.toString
  }

  private def isNormalChar(c: Char): Boolean = (c: @switch) match {
    case '\\' => false
    case '"' => false
    case '\b' => false
    case '\f' => false
    case '\n' => false
    case '\r' => false
    case '\t' => false
    case possibleUnicode => !Character.isISOControl(possibleUnicode)
  }

  def jsonEncoderOf[A](implicit encoder: Encoder[A]): EntityEncoder[A] =
    jsonEncoder.contramap[A](encoder.apply)
}
