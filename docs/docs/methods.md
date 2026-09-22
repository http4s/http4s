# HTTP Methods

For a REST API, your service will want to support different verbs/methods.
Http4s has a list of all the [methods] you're familiar with, and a few more.

```scala mdoc:silent
import cats.effect._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._, org.http4s.dsl.io._
import org.http4s.circe._
```

```scala mdoc:silent
case class TweetWithId(id: Int, message: String)
case class Tweet(message: String)

def getTweet(tweetId: Int): IO[Option[TweetWithId]] = ???
def addTweet(tweet: Tweet): IO[TweetWithId] = ???
def updateTweet(id: Int, tweet: Tweet): IO[Option[TweetWithId]] = ???
def deleteTweet(id: Int): IO[Unit] = ???

implicit val tweetWithIdEncoder: EntityEncoder[IO, TweetWithId] = jsonEncoderOf[IO, TweetWithId]
implicit val tweetDecoder: EntityDecoder[IO, Tweet] = jsonOf[IO, Tweet]

val tweetService = HttpRoutes.of[IO] {
  case GET -> Root / "tweets" / IntVar(tweetId) =>
    getTweet(tweetId)
      .flatMap(_.fold(NotFound())(Ok(_)))
  case req @ POST -> Root / "tweets" =>
    req.as[Tweet].flatMap(addTweet).flatMap(Ok(_))
  case req @ PUT -> Root / "tweets" / IntVar(tweetId) =>
    req.as[Tweet]
      .flatMap(updateTweet(tweetId, _))
      .flatMap(_.fold(NotFound())(Ok(_)))
  case HEAD -> Root / "tweets" / IntVar(tweetId) =>
    getTweet(tweetId)
      .flatMap(_.fold(NotFound())(_ => Ok()))
  case DELETE -> Root / "tweets" / IntVar(tweetId) =>
    deleteTweet(tweetId)
      .flatMap(_ => Ok())
}
```

There's also [`DefaultHead`] which replicates the functionality of the native
implementation of the `HEAD` route.

## Safe and Idempotent Requests with a Body: QUERY

Http4s supports the `QUERY` method defined in RFC 10008. The `QUERY` method is designed for safe and idempotent requests that need to carry a payload (request body), bridging the gap between `GET` (which does not have defined semantics for request bodies) and `POST` (which is neither safe nor idempotent).

You can match on `QUERY` requests in your routing DSL:

```scala
val searchService = HttpRoutes.of[IO] {
  case req @ QUERY -> Root / "search" =>
    req.as[SearchQuery].flatMap(performSearch).flatMap(Ok(_))
}
```

To support content negotiation for query formats, http4s also provides the `Accept-Query` header (defined in RFC 10008 Section 3).

[methods]: @API_URL@/org/http4s/Method$.html
[`DefaultHead`]: @API_URL@/org/http4s/server/middleware/DefaultHead$.html
