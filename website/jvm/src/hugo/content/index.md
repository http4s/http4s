---
title: http4s
---

<div class="jumbotron mt-3">
  <div class="container">
    <p class="display-4">
      Typeful, functional, streaming HTTP for Scala.
    </p>
    <button class="btn btn-primary btn-lg dropdown-toggle" type="button" id="doc-menu-item" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
      Get started with http4s
    </button>
    {{% nav-docs %}}
  </div>
</div>

<div class="container">
  <div class="card-deck">
    <div class="card p-3">
      <h3 class="card-title">Typeful</h3>

      <p class="card-text">
        http4s servers and clients share an immutable model of
        requests and responses. Standard headers are modeled as
        semantic types, and entity codecs are done by typeclass.
      </p>
    </div>
    <div class="card p-3">
      <h3 class="card-title">Functional</h3>

      <p>
        The pure functional side of Scala is favored to promote
        composability and easy reasoning about your code.  I/O is
        managed through <a href="https://github.com/typelevel/cats-effect">cats-effect</a>.
      </p>
    </div>
    <div class="card p-3">
      <h3 class="card-title">Streaming</h3>

      <p class="card-text">
        http4s is built on <a href="https://github.com/functional-streams-for-scala/fs2">FS2</a>, 
        a streaming library that provides for processing and emitting
        large payloads in constant space and implementing websockets.
      </p>
    </div>
  </div>
</div>
