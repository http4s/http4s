---
menu: methods
weight: 115
title: HTTP Methods
---

For a REST API, your service will want to support different verbs/methods.
Http4s has a list of all the [methods] you're familiar with, and a few more.

```tut:silent
import cats.effect._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._, org.http4s.dsl.io._
import org.http4s.circe._
```

```tut:book
case class TweetWithId(id: Int, message: String)
case class Tweet(message: String)

def getTweet(tweetId: Int): IO[Option[TweetWithId]] = ???
def addTweet(tweet: Tweet): IO[TweetWithId] = ???
def updateTweet(id: Int, tweet: Tweet): IO[Option[TweetWithId]] = ???
def deleteTweet(id: Int): IO[Unit] = ???

implicit val tweetWithIdEncoder = jsonEncoderOf[IO, TweetWithId]
implicit val tweetDecoder = jsonOf[IO, Tweet]

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

[methods]: ../api/org/http4s/Method$.html
[`DefaultHead`]: ../api/org/http4s/server/middleware/DefaultHead$.html
