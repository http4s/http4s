/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.parse.{Parser => P}
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Renderable
import org.http4s.util.Writer

sealed trait ProductIdOrComment extends Renderable
object ProductIdOrComment {
  @deprecated("Use serverAgentParser(Int) instead", "0.22.15")
  private[http4s] val serverAgentParser: P[(ProductId, List[ProductIdOrComment])] =
    serverAgentParser(Rfc7230.CommentDefaultMaxDepth)

  private[http4s] def serverAgentParser(maxDepth: Int): P[(ProductId, List[ProductIdOrComment])] = {
    val rws = P.charIn(' ', '	').rep.void
    ProductId.parser ~ (rws *> (ProductId.parser.orElse(ProductComment.parser(maxDepth)))).rep0
  }

}

final case class ProductId(value: String, version: Option[String] = None)
    extends ProductIdOrComment {

  override def render(writer: Writer): writer.type = {
    writer << value
    version.foreach { v =>
      writer << '/' << v
    }
    writer
  }
}

object ProductId {
  private[http4s] val parser = (Rfc7230.token ~ (P.string("/") *> Rfc7230.token).?).map {
    case (value: String, version: Option[String]) => ProductId(value, version)
  }
}

final case class ProductComment(value: String) extends ProductIdOrComment {
  override def render(writer: Writer): writer.type = {
    writer << '(' << value << ')'
    writer
  }
}

object ProductComment {
  private[http4s] def parser(maxDepth: Int): P[ProductComment] =
    Rfc7230.comment(maxDepth).map(ProductComment.apply)

  @deprecated("Use parser(Int) instead", "0.22.15")
  private[http4s] val parser: P[ProductComment] =
    parser(Rfc7230.CommentDefaultMaxDepth)
}
