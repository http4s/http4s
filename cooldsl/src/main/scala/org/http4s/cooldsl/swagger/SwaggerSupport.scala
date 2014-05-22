package org.http4s.cooldsl
package swagger

import org.http4s.cooldsl.CoolService
import shapeless.HList
import org.http4s._

import org.json4s._
import org.json4s.jackson.JsonMethods._
import JsonDSL._
import org.http4s.Header.`Content-Type`
import scodec.bits.ByteVector
import org.http4s.cooldsl.CoolAction

/**
 * Created by Bryce Anderson on 5/9/14.
 */
trait SwaggerSupport extends CoolService {

  def apiVersion: String = "1.0"
  def apiInfo: ApiInfo = ApiInfo("None", "none", "none", "none", "none", "none")

  implicit protected def jsonFormats: Formats = SwaggerSerializers.defaultFormats

  private val swagger = new Swagger("1.2", apiVersion, apiInfo)

  Method.Get / "api-info" |>>> { () =>
    val json = renderIndex(swagger.docs.toSeq)

    Status.Ok(compact(render(json)))
          .withHeaders(Header.`Content-Type`(MediaType.`application/json`))
  }

  Method.Get / "api-info" / -* |>>> { params: Seq[String] =>
    val path = params.mkString("/", "/", "")
    swagger.doc(path) match {
      case Some(api) =>
        val json = renderDoc(api)

        Status.Ok(compact(render(json)))
          .withHeaders(Header.`Content-Type`(MediaType.`application/json`))

      case None => Status.NotFound("api-info" + path)
    }
  }

  override protected def append[T <: HList, F, O](action: CoolAction[T, F, O]): Unit = {
    super.append(action)
    swagger.register(action)
  }

  protected def renderDoc(doc: Api): JValue = {
    val json = docToJson(doc) merge
      ("basePath" -> "http://localhost:8080/http4s") ~
        ("swaggerVersion" -> swagger.swaggerVersion) ~
        ("apiVersion" -> swagger.apiVersion)
    val consumes = dontAddOnEmpty("consumes", doc.consumes)_
    val produces = dontAddOnEmpty("produces", doc.produces)_
    val protocols = dontAddOnEmpty("protocols", doc.protocols)_
    val authorizations = dontAddOnEmpty("authorizations", doc.authorizations)_
    val jsonDoc = (consumes andThen produces andThen protocols andThen authorizations)(json)
    //    println("The rendered json doc:\n" + jackson.prettyJson(jsonDoc))
    jsonDoc
  }

  protected def renderIndex(docs: Seq[Api]): JValue = {
    ("apiVersion" -> swagger.apiVersion) ~
      ("swaggerVersion" -> swagger.swaggerVersion) ~
      ("apis" ->
        (docs.filter(_.apis.nonEmpty).map { doc =>
          ("path" -> doc.resourcePath) ~ ("description" -> doc.description)
        })) ~
      ("info" -> Option(swagger.apiInfo).map(Extraction.decompose(_)))
  }

  private[this] def dontAddOnEmpty(key: String, value: List[String])(json: JValue) = {
    val v: JValue = if (value.nonEmpty) key -> value else JNothing
    json merge v
  }

  protected def docToJson(doc: Api): JValue = Extraction.decompose(doc)

  implicit def jsonWritable = new SimpleWritable[JValue] {
    override def contentType: `Content-Type` = `Content-Type`(MediaType.`application/json`)

    override def asChunk(data: _root_.org.json4s.JValue): ByteVector =
      ByteVector.view(compact(render(data)).getBytes(CharacterSet.`UTF-8`.charset))
  }
}
