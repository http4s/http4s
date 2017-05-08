import fs2._
import fs2.util.syntax._
import org.http4s.multipart.MultipartParser
import scodec.bits.ByteVector

def ruinDelims(str: String) = augmentString(str) flatMap {
  case '\n' => "\r\n"
  case c => c.toString
}

def unspool(str: String, limit: Int = Int.MaxValue): Stream[Task, Byte] = {
  if (str.isEmpty) {
    Stream.empty
  } else if (str.length <= limit) {
    Stream.emits(ByteVector.view(str getBytes "ASCII").toSeq)
  } else {
    val (front, back) = str.splitAt(limit)
    Stream.emits(ByteVector.view(front getBytes "ASCII").toSeq) ++ unspool(back, limit)
  }
}

val myText =
  """
    |I have a string
    |It is is cool
    |
    |Don't like it cool.
    |Lets eat like a fool.
  """.stripMargin



Stream.emit(myText)
  .covary[Task]
  .through(text.utf8Encode)
  .map(ByteVector.fromByte)
  .open.flatMap(MultipartParser.receiveLine(None))
.close
.runLog
.unsafeRun()
//  .pull(MultipartParser.receiveLine(None))
//  .runLog
//  .unsafeRun()