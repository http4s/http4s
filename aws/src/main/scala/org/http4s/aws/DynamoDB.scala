package org.http4s
package aws

import scalaz.concurrent._
import org.http4s.Http4s._

import org.http4s.jawn._
import org.json4s._
import org.http4s.json4s.native._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.http4s.client._

import org.http4s.util.CaseInsensitiveString

object DynamoDb {
  class ProvisionedThroughputExceededException extends Exception

  sealed trait AttributeType {
    def asString:String
  }

  object AttributeType {
    case object String extends AttributeType { def asString = "S" }
    case object Binary extends AttributeType { def asString = "B" }
    case object Number extends AttributeType { def asString = "N" }
  }

  sealed trait KeySchemaType { def asString: String }
  object KeySchemaType {
    case object Hash extends KeySchemaType { def asString = "HASH"}
    case object Range extends KeySchemaType { def asString = "RANGE"}
  }

  sealed trait Field
  object Field {
    case class FString(s: String) extends Field
    case class Number(l: Long) extends Field
  }


  case class PutItem(values: Map[String, Field])
}

import DynamoDb._

class DynamoDb(client: Client, signer: AwsSigner, region: String) {

  val server =  Uri(
    scheme = Some(CaseInsensitiveString("http")),
    authority = Some(Uri.Authority(
      host = Uri.RegName(CaseInsensitiveString(s"dynamodb.$region.amazonaws.com"))
    )),
    path = "/"
  )


  val THROUGHPUT_EXCEPTION = "com.amazonaws.dynamodb.v20120810#ProvisionedThroughputExceededException"

  def handleError(body: JValue) = (for {
    JObject(children) <- body
    JField("__type", JString(errorType)) <- children
  } yield errorType).headOption filter { _ ==  THROUGHPUT_EXCEPTION } map { _ =>
    Task.fail(new ProvisionedThroughputExceededException())
  } getOrElse { Task.fail(new Exception(s"Error: $body"))}

  private def request(action: String, body: JValue = JObject(Nil)):Task[JValue] = {
    import org.http4s.Status.ResponseClass._
    for {
      request <- Request(
        uri = uri("http://dynamodb.us-west-2.amazonaws.com/"),
        method = Method.POST,
        headers = Headers(
          Header("x-amz-target", s"DynamoDB_20120810.$action")
        )
      ).withBody(body)
      signed <- signer(request)
      parsed  <- client.fetch(signed) { 
        case Successful(resp) => resp.as[JValue]
        case resp => resp.as[JValue] flatMap handleError
      }
    } yield parsed
  }

  def listTables:Task[Seq[String]] = request("ListTables") map { json =>
    for {
      JObject(child) <- json
      JField("TableNames", tableNames) <- child
      JString(name) <- tableNames
    } yield name
  }

  def createTable(name: String, attributeDefinitions: Map[String, (AttributeType, KeySchemaType)], readCapacity: Int, writeCapacity: Int) = request("CreateTable",
    ("AttributeDefinitions" -> (attributeDefinitions map { case (name, (aDef, _)) =>
      ("AttributeName" -> name) ~ ("AttributeType" -> aDef.asString)
    })) ~
      ("KeySchema" -> (attributeDefinitions map { case (name, (_, tpe)) =>
        ("AttributeName" -> name) ~ ("KeyType" -> tpe.asString)
      })) ~
      ("ProvisionedThroughput" -> (
        ("ReadCapacityUnits" -> readCapacity) ~ ("WriteCapacityUnits" -> writeCapacity)
        )) ~
      ("TableName" -> name)
  )

  def deleteTable(name: String) = request("DeleteTable", ("TableName" -> name))

  def toField(f: Field):JObject = f match {
    case Field.FString(s) => ("S" -> s)
    case Field.Number(l) => ("N" -> l.toString)
  }

  def write(items: Map[String, Seq[PutItem]]) = request("BatchWriteItem",
    "RequestItems" -> (
      items map { case (tableName, puts) =>
        tableName -> (puts map { case PutItem(values) =>
          "PutRequest" -> ("Item" -> (values map { case (k,v)  =>
            (k -> toField(v))
          }))
        })
      }
      )
  )

  def get(tableName: String, key: Map[String, String]) = request("GetItem",
    ("TableName" -> tableName) ~
      ("Key" -> key.map({case (k,v) => k -> Map("S" -> v)}))
  )




}
