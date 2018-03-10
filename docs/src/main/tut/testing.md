---
menu: main
weight: 350
title: Testing
---

## Introduction

Testing http4s [[org.http4s.HttpService]]'s is straightforward given that it's:

```scala
    type HttpService[F[_]] = Kleisli[OptionT[F, ?], Request[F], Response[F]]
```

The documentation explains:

```
  /**
    * A [[Kleisli]] that produces an effect to compute an [[OptionT[F]]]] from a
    * [[Request[F]]]. In case an [[OptionT.none]] is computed the server backend
    * should respond with a 404.
    * An HttpService can be run on any supported http4s
    * server backend, such as Blaze, Jetty, or Tomcat.
    */
```

So, since the above is simply a function, we can test it just like any other function.

## Example

Given the following service, which we can define through the following [Swagger]() doc:

```yaml
swagger: "2.0"
info:
  description: "Example Swagger Doc"
  version: "1.0.0"
  title: "Http4s Demo"
  license:
host: ""
basePath: "/api"
schemes:
- "http"
paths:
  /user/{id}:
    post:
      summary: "Get a user by id."
      produces:
      - "application/json"
      parameters:
      - in: "path"
        name: "id"
        description: "ID of User"
        format: "[0-9]{4}"
        required: true
        schema:
          type: integer
      responses:
        200:
          description: "Got representation of User."
          schema:
            $ref: "#/definitions/User"
        400:
          description: "Invalid User ID, i.e. not exactly 4 digits"
        404: 
          description: "User does not exist."
        500:
          description: "Server-side error."  
definitions:
  User:
    type: "object"
    required:
    - "name"
    - "hobbies"
    properties:
      name:
        type: "string"
      hobbies:
        type: "array"
        description: "String's."
``` 

```tut:book

## References

* [Kleisli](https://typelevel.org/cats/datatypes/kleisli.html)
* [OptionT](https://typelevel.org/cats/datatypes/optiont.html)

