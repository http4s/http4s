# Changelog

Maintenance branches are merged before each new release. This change log is
ordered chronologically, so each release contains all changes described below it.

# v0.23.29 (2024-10-23)

This is a maintenance release to fix scalafix processing exceptions in http4s modules.

## What's Changed
### http4s-core
* Update scala3-library, ... to 3.3.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7531
* Update a bunch of upstream dependencies to align with Scala 2.12.20 and 2.13.15 by @http4s-steward in https://github.com/http4s/http4s/pull/7541
* Fix for Uri macro with `org` variable by @samspills in https://github.com/http4s/http4s/pull/7537
### Documentation
* notes on how to put together a v0.23.xx release by @samspills in https://github.com/http4s/http4s/pull/7519
### Behind the scenes

<details>

* Update http4s-circe, http4s-ember-client, ... to 0.23.28 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7521
* Update sbt to 1.10.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7524
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7522
* Update netty-buffer, netty-codec-http to 4.1.114.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7533
* Update sbt-http4s-org to 0.17.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7514
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7525
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7536
* Update case-insensitive, ... to 1.4.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7535
* Update jnr-unixsocket to 0.38.23 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7542
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7544
* Ignore http4s-scalafix-internal updates by @rossabaker in https://github.com/http4s/http4s/pull/7545
* Update sbt-scoverage to 2.2.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7546
* Update sbt to 1.10.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7548

</details>

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.28...v0.23.29

# v0.23.28 (2024-09-09)

## What's Changed
### http4s-core
* Made traits and objects public for Node.js files by @Chingles2404 in https://github.com/http4s/http4s/pull/7452
* Add CustomMetricsOps by @dj707chen in https://github.com/http4s/http4s/pull/7469
* Update to vault-3.6.0, cats-2.11.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7467
* Update ip4s-core, ip4s-test-kit to 3.6.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7455
* Update log4cats-core, log4cats-js-console, ... to 2.7.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7465
* Update keypool to 0.4.10 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7503
* Update fs2-core, fs2-io to 3.11.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7506
* Minor optimization in ServerResponse by @Chingles2404 in https://github.com/http4s/http4s/pull/7510
* Fix EmptyCustomLabels and SizedSeq0 singleton creation issue by @dj707chen in https://github.com/http4s/http4s/pull/7511
### http4s-client
* WebSocket client `Reconnect` middleware by @armanbilge in https://github.com/http4s/http4s/pull/7445
### http4s-ember-client
* Ember Client : Retry when connection reset on JDK 17+ by @Dichotomia in https://github.com/http4s/http4s/pull/7472
### http4s-laws
* Update munit to 1.0.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7462
* Update munit-cats-effect to 2.0.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7466
### http4s-circe
* Update circe-core, circe-generic, ... to 0.14.8 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7470
### Documentation
* Adjust the state of Scala 3 on the quick start page by @danicheg in https://github.com/http4s/http4s/pull/7487
### Behind the scenes

<details>
* Update munit to 1.0.0-RC1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7438
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7447
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7450
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7453
* Fix bitrotten Nix actions by @rossabaker in https://github.com/http4s/http4s/pull/7454
* Update netty-buffer, netty-codec-http to 4.1.111.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7456
* Update http4s-circe, http4s-ember-client, ... to 0.23.27 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7457
* Update scalafmt-core to 3.8.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7463
* Update sbt to 1.10.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7461
* Update sbt-scoverage to 2.0.12 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7464
* Update scala-library to 2.13.14, sbt-http4s-org to 0.17.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7459
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7471
* Update xsbt-web-plugin to 4.2.5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7478
* Update sbt-scoverage to 2.1.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7483
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7482
* Update sbt to 1.10.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7484
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7486
* Update Java-WebSocket to 1.5.7 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7485
* Update netty-buffer, netty-codec-http to 4.1.112.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7490
* Update sbt-http4s-org to 0.17.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7495
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7496
* Update sbt-native-packager to 1.10.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7497
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7498
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7500
* Update sbt-scoverage to 2.1.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7505
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7507
* Update sbt-scoverage to 2.2.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7516
* Update netty-buffer, netty-codec-http to 4.1.113.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7513
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7509
</details>

## New Contributors
* @Chingles2404 made their first contribution in https://github.com/http4s/http4s/pull/7452
* @Dichotomia made their first contribution in https://github.com/http4s/http4s/pull/7472
* @dj707chen made their first contribution in https://github.com/http4s/http4s/pull/7469

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.27...v0.23.28

# v0.23.27 (2024-05-03)

This release is binary compatible with the 0.23.x series.

## What's Changed
### http4s-core
* Move ember H2Keys Http2PriorKnowledge key to core by @hamnis in https://github.com/http4s/http4s/pull/7407
* Handle characters > 0xff in multipart filenames by @rossabaker in https://github.com/http4s/http4s/pull/7419
* feat: suppress stack trace on protocol exceptions by @mcenkar in https://github.com/http4s/http4s/pull/7428
* Replace "*Decoded" Part methods with "*Bytes" by @rossabaker in https://github.com/http4s/http4s/pull/7436
* RFC: Cookies with `Max-Age=0` should be permitted by @henricook in https://github.com/http4s/http4s/pull/7435
* Update sbt-http4s-org to 0.17.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7437
* Update cats-effect, cats-effect-std, ... to 3.5.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7405
* Update fs2-core, fs2-io to 3.10.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7418
* Update sbt-scalajs, scalajs-compiler, ... to 1.16.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7421

### http4s-client
* Small refactor to DefaultClient to remove unsafe calls and repeated code by @Adam-McDevitt in https://github.com/http4s/http4s/pull/7417
* #6521 History client middleware by @SallyPerez in https://github.com/http4s/http4s/pull/7372
### http4s-ember-core
* Ember client drop head body by @hamnis in https://github.com/http4s/http4s/pull/7369
### http4s-circe
* Update circe-core, circe-generic, ... to 0.14.7 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7441
### http4s-laws
* Update scalacheck to 1.17.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7430
* Update discipline-core to 1.6.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7432
### Documentation
* Removed reference to TSec in documentation by @KristianAN in https://github.com/http4s/http4s/pull/7401
* Some tweaks to docs by @danicheg in https://github.com/http4s/http4s/pull/7422
* Documentation form multipart and urlform by @fredshonorio in https://github.com/http4s/http4s/pull/7328

### Behind the scenes
* Update http4s-circe, http4s-ember-client, ... to 0.23.26 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7403
* Update sbt-buildinfo to 0.12.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7413
* Update scalafmt-core to 3.8.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7420
* Update netty-buffer, netty-codec-http to 4.1.108.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7414
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7409
* Update sbt-native-packager to 1.10.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7425
* Update munit-cats-effect to 2.0.0-M5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7426
* Update netty-buffer, netty-codec-http to 4.1.109.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7427
* Update sbt-scala-native-config-brew to 0.3.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7440

## New Contributors
* @KristianAN made their first contribution in https://github.com/http4s/http4s/pull/7401
* @Adam-McDevitt made their first contribution in https://github.com/http4s/http4s/pull/7417
* @mcenkar made their first contribution in https://github.com/http4s/http4s/pull/7428
* @henricook made their first contribution in https://github.com/http4s/http4s/pull/7435
* @SallyPerez made their first contribution in https://github.com/http4s/http4s/pull/7372

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.26...v0.23.27

# v0.23.26 (2024-03-04)

## What's Changed

### http4s-ember-server
* Log errors in `upgradeSocket` by @froth in https://github.com/http4s/http4s/pull/7363
* Ember: return HTTP 431 when maxHeaderSize is exceeded by @rossabaker in https://github.com/http4s/http4s/pull/7399

### Documentation
* Add OptionalMultiQueryParamDecoderMatcher Documentation by @kejifasuyi in https://github.com/http4s/http4s/pull/7357
* Fix the outdated code snippet in the client dsl scaladoc by @danicheg in https://github.com/http4s/http4s/pull/7381

### Upgrades
* Update keypool to 0.4.9 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7364
* Update fs2-core, fs2-io to 3.9.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7367
* Update ip4s-core, ip4s-test-kit to 3.5.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7376

### Behind the scenes

* Update http4s-circe, http4s-ember-client, ... to 0.23.25 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7353
* Fix labelling PRs for the `client-testkit` module by @danicheg in https://github.com/http4s/http4s/pull/7354
* Refactor `release.yml` by @danicheg in https://github.com/http4s/http4s/pull/7355
* Update sbt-scalajs, scalajs-compiler, ... to 1.15.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7358
* Update netty-buffer, netty-codec-http to 4.1.105.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7361
* Update cats-effect, cats-effect-std, ... to 3.5.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7360
* Update netty-buffer, netty-codec-http to 4.1.106.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7365
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7368
* Update nscplugin, sbt-scala-native, ... to 0.4.17 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7366
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7371
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7374
* Update Java-WebSocket to 1.5.6 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7375
* Update munit to 1.0.0-M11 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7373
* Update jnr-unixsocket to 0.38.22 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7380
* Update netty-buffer, netty-codec-http to 4.1.107.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7378
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7377
* Update sbt-scoverage to 2.0.10 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7379
* Update scalafmt-core to 3.8.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7383
* Update sbt to 1.9.9 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7384
* Update sbt-scoverage to 2.0.11 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7385
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7392
* Update sbt-http4s-org to 0.16.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7393
* Update scala3-library, ... to 3.3.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7394
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7402

## New Contributors
* @kejifasuyi made their first contribution in https://github.com/http4s/http4s/pull/7357

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.25...v0.23.26

# v0.23.25 (2024-01-03)

Primarily, this is a maintenance release, binary compatible with the 0.23.x series. Also, it brings an increase in the default value for `idleConnectionTime` in `ember-client` from `45s` to `60s`. See the [PR](https://github.com/http4s/http4s/pull/7329) and [related issue](https://github.com/http4s/http4s/issues/7327) for details.

## What's Changed

### http4s-ember-core
* Align the server and client idle timeouts in Ember by @rlavolee in https://github.com/http4s/http4s/pull/7329

### http4s-client-testkit
* Fix handling of connection closure in `WSTestClient` by @armanbilge in https://github.com/http4s/http4s/pull/7334

### Documentation
* Add note about cats-parse to 0.23.24 changelog by @armanbilge in https://github.com/http4s/http4s/pull/7322
* Ross is not a moderator by @rossabaker in https://github.com/http4s/http4s/pull/7346
* Push error handling docs through mdoc by @Quafadas in https://github.com/http4s/http4s/pull/7340

### Behind the scenes
* Update http4s-circe, http4s-ember-client, ... to 0.23.24 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7323
* Update scalafmt-core to 3.7.17 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7324
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7325
* Update logback-classic to 1.2.13 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7331
* Update sbt-http4s-org to 0.16.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7332
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7333
* Update sbt-jmh to 0.4.7 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7336
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7337
* Update netty-buffer, netty-codec-http to 4.1.103.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7339
* Update sbt to 1.9.8 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7341
* Update Java-WebSocket to 1.5.5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7344
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7345
* Update netty-buffer, netty-codec-http to 4.1.104.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7342
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7349

## New Contributors
* @rlavolee made their first contribution in https://github.com/http4s/http4s/pull/7329

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.24...v0.23.25

# v0.23.24 (2023-11-14)

This release upgrades to [cats-parse v1.0.0](https://github.com/typelevel/cats-parse/releases/tag/v1.0.0) which may trigger eviction errors in your build. The cats-parse 1.x series is 100% binary-compatible with the cats-parse 0.3.x series, so it is safe to ignore the eviction errors in this case.

## What's Changed
### http4s-core
* Avoid linking MimeDB in Scala Native by @lolgab in https://github.com/http4s/http4s/pull/7278
* Fixes #7283: make BasicCredentials constructor safe by @grouzen in https://github.com/http4s/http4s/pull/7284
* Model Content-Transfer-Encoding header by @froth in https://github.com/http4s/http4s/pull/7292
* Add asCurlWithBody by @morgen-peschke in https://github.com/http4s/http4s/pull/7238
* Update fs2-core, fs2-io to 3.9.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7311
* Update request cookie parser to handle zero or more spaces between semicolons by @mrdziuban in https://github.com/http4s/http4s/pull/7312
* Update cats-parse to 1.0.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7313
* More efficient working with headers by @plokhotnyuk in https://github.com/http4s/http4s/pull/7228
* Fix #7262: mulitpart decoder wrapping unwanted errors by @LaurenceWarne in https://github.com/http4s/http4s/pull/7265
### http4s-server
* Fix handling of streaming bodies in entity limiter by @armanbilge in https://github.com/http4s/http4s/pull/7264
* Deprecate Jsonp Middleware by @froth in https://github.com/http4s/http4s/pull/7285
### http4s-ember-core
* Ember-Core: Use optimised fs2 method in write Loop. by @diesalbla in https://github.com/http4s/http4s/pull/7230
### http4s-ember-client
* Add warning logs for misconfigured timeouts by @sgjbryan in https://github.com/http4s/http4s/pull/7234
### Documentation
* Add client middleware documentation by @fredshonorio in https://github.com/http4s/http4s/pull/7058
* Fixes highlighting in error-handling documentation by @Marcus-Rosti in https://github.com/http4s/http4s/pull/7316
### Behind the scenes
* Update http4s-circe, http4s-ember-client to 0.23.23 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7220
* Update netty-buffer, netty-codec-http to 4.1.95.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7221
* Update netty-buffer, netty-codec-http to 4.1.96.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7227
* Update sbt to 1.9.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7223
* Update sbt-scala-native-crossproject to 1.3.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7202
* Update scalafmt-core to 3.7.11 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7226
* Update Java-WebSocket to 1.5.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7222
* Update fs2-core, fs2-io to 3.8.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7229
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7233
* Update scalafmt-core to 3.7.12 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7235
* Update cats-core, cats-laws to 2.10.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7239
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7244
* Update netty-buffer, netty-codec-http to 4.1.97.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7247
* Update sbt to 1.9.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7248
* Update epollcat to 0.1.6 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7253
* Update scalafmt-core to 3.7.13 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7254
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7255
* Update fs2-core, fs2-io to 3.9.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7258
* Update fs2-core, fs2-io to 3.9.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7259
* Update circe-core, circe-generic, ... to 0.14.6 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7260
* Update scalafmt-core to 3.7.14 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7263
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7266
* Update nscplugin, sbt-scala-native, ... to 0.4.15 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7267
* Update sbt-jmh to 0.4.6 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7268
* Update scala3-library, ... to 3.3.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7269
* Update sbt-scoverage to 2.0.9 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7270
* Update fs2-core, fs2-io to 3.9.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7275
* Update jnr-unixsocket to 0.38.21 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7273
* Update munit to 1.0.0-M10 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7280
* Update sbt to 1.9.6 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7279
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7282
* Update netty-buffer, netty-codec-http to 4.1.98.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7287
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7288
* Update sbt-scalajs, scalajs-compiler, ... to 1.14.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7289
* Update cats-effect, cats-effect-std, ... to 3.5.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7291
* Update netty-buffer, netty-codec-http to 4.1.99.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7293
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7295
* Update to sbt-http4s-org 0.15.3 by @armanbilge in https://github.com/http4s/http4s/pull/7242
* Update scala-library, scala-reflect to 2.13.12 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7272
* Update sbt-http4s-org to 0.15.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7281
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7297
* Update netty-buffer, netty-codec-http to 4.1.100.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7298
* Update nscplugin, sbt-scala-native, ... to 0.4.16 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7299
* Update to sbt-typelevel `0.6.0` with Laika `1.0.0` by @jenshalm in https://github.com/http4s/http4s/pull/7290
* Update scodec-bits to 1.1.38 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7303
* Update sbt to 1.9.7 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7304
* Update scalafmt-core to 3.7.15 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7305
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7307
* Update ip4s-core, ip4s-test-kit to 3.4.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7309
* Update netty-buffer, netty-codec-http to 4.1.101.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7315
* Update munit-cats-effect to 2.0.0-M4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7318
* Update sbt-http4s-org to 0.16.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7319
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7320

## New Contributors
* @lolgab made their first contribution in https://github.com/http4s/http4s/pull/7278
* @grouzen made their first contribution in https://github.com/http4s/http4s/pull/7284
* @morgen-peschke made their first contribution in https://github.com/http4s/http4s/pull/7238
* @plokhotnyuk made their first contribution in https://github.com/http4s/http4s/pull/7228
* @Marcus-Rosti made their first contribution in https://github.com/http4s/http4s/pull/7316
* @LaurenceWarne made their first contribution in https://github.com/http4s/http4s/pull/7265
* @sgjbryan made their first contribution in https://github.com/http4s/http4s/pull/7234

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.23...v0.23.24

# v0.23.23 (2023-07-19)

This release includes assorted fixes and optimizations for Ember.

## What's Changed

### http4s-ember-core

* Some micro-optimisations in `ember-core` by @danicheg in https://github.com/http4s/http4s/pull/7154

### http4s-ember-server

* Disable Ember server TLS logging more aggressively by @armanbilge in https://github.com/http4s/http4s/pull/7204
* Immediately release invalid connections in `getValidManaged` by @armanbilge in https://github.com/http4s/http4s/pull/7218
* WebSocketHelpers.scala - Use Chunks by @diesalbla in https://github.com/http4s/http4s/pull/7214

### http4s-ember-client

* Improve error messages for MissingHost and MissingPort by @george-wilson-rea in https://github.com/http4s/http4s/pull/7207

### Behind the scenes

* Update http4s-circe, http4s-ember-client to 0.23.22 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7192
* Update scalafmt-core to 3.7.6 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7197
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7200
* Update scalafmt-core to 3.7.7 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7201
* Update sbt to 1.9.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7203
* Update scalafmt-core to 3.7.8 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7205
* Update scalafmt-core to 3.7.9 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7210
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7217
* Update scalafmt-core to 3.7.10 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7215

## New Contributors

* @george-wilson-rea made their first contribution in https://github.com/http4s/http4s/pull/7207

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.22...v0.23.23

# v0.23.22 (2023-06-28)

This release includes assorted fixes for Ember.

## What's Changed

### http4s-core

* Fix `QueryOps#setQueryParams` scaladoc by @danicheg in https://github.com/http4s/http4s/pull/7126

### http4s-ember-core

* Include query params in http2 :path pseudo header by @ybasket in https://github.com/http4s/http4s/pull/7180
* A bunch of tweaks to `ClientHelpers`' methods by @danicheg in https://github.com/http4s/http4s/pull/7173

### http4s-ember-server

* Properly handle `NoSuchElementException` in `tlsSocket.applicationProtocol` by @arturaz in https://github.com/http4s/http4s/pull/7092
* Allow providing custom error handler when connection establishing fails. by @arturaz in https://github.com/http4s/http4s/pull/7093

### Behind the scenes

* Update http4s-circe, http4s-ember-client to 0.23.21 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7169
* Disable artifact upload by @armanbilge in https://github.com/http4s/http4s/pull/7168
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7099
* Update netty-buffer, netty-codec-http to 4.1.94.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7174
* Update munit to 1.0.0-M8 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7145
* Update scalac-compat-annotation to 0.1.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7177
* Update sbt-scalajs, scalajs-compiler, ... to 1.13.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7181
* Update cats-effect, cats-effect-std, ... to 3.5.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7182
* Update cats-parse to 0.3.10 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7184
* Update sbt to 1.9.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7185
* Update jawn-parser to 1.5.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7186
* Update scalac-compat-annotation to 0.1.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7187
* Use `Assertions#assume` in tests by @danicheg in https://github.com/http4s/http4s/pull/7183
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7188

## New Contributors

* @arturaz made their first contribution in https://github.com/http4s/http4s/pull/7092

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.21...v0.23.22

# v0.23.21 (2023-06-16)

This release fixes another regression in Ember HTTP/2.

## What's Changed

### http4s-server

* Use `ClockOps` in `ResponseTiming` Middleware by @danicheg in https://github.com/http4s/http4s/pull/7163

### http4s-ember-core

* Close H2 `readBuffer` on `headers.endStream` by @armanbilge in https://github.com/http4s/http4s/pull/7156
* Use `ClockOps` in `Util#readWithTimeout` by @danicheg in https://github.com/http4s/http4s/pull/7162

### http4s-ember-server

* Mask errors in Ember server `runConnection` by @armanbilge in https://github.com/http4s/http4s/pull/7157

### Behind the scenes

* Update http4s-circe, http4s-ember-client, ... to 0.23.20 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7153
* Simplify type in `H2Server#h2cUpgradeHttpRoute` by @danicheg in https://github.com/http4s/http4s/pull/7159

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.20...v0.23.21

# v0.23.20 (2023-06-12)

This release fixes a critical regression in Ember HTTP/2. It also upgrades to Scala 3.3.0 LTS.

## What's Changed

### http4s-core

* Add missing constructors to `ContextRoutes` by @hamnis in https://github.com/http4s/http4s/pull/7123
* Add `Upgrade-Insecure-Requests` header model by @diogocanut in https://github.com/http4s/http4s/pull/7129
* Update scala3-library, ... to 3.3.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7131

### http4s-server

* Add handling for fragmented frames in WebSockets by @mox692 in https://github.com/http4s/http4s/pull/7091

### http4s-ember-core

* Close `H2Stream` `readBuffer` on `data.endStream` by @armanbilge in https://github.com/http4s/http4s/pull/7147

### Documentation

* fixes wrong link in "Server Middleware" page in the docs #7117 by @rsemlal in https://github.com/http4s/http4s/pull/7118
* Added more examples to the doc (shortcut to create Responses) by @walesho in https://github.com/http4s/http4s/pull/7120

### Behind the scenes

* Fix release date by @armanbilge in https://github.com/http4s/http4s/pull/7114
* Update http4s-circe, http4s-ember-client, ... to 0.23.19 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7116
* Update netty-buffer, netty-codec-http to 4.1.93.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7124
* Update scalafmt-core to 3.7.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7130
* Update jawn-parser to 1.5.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7132
* Update sbt-scoverage to 2.0.8 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7134
* Update sbt-jmh to 0.4.5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7136
* Update nscplugin, sbt-scala-native, ... to 0.4.14 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7139
* Update epollcat to 0.1.5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7140
* Update jnr-unixsocket to 0.38.20 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7141
* Update sbt to 1.9.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7133
* Update sbt-http4s-org to 0.14.13 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7137
* Update scala-library, scala-reflect to 2.12.18 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7143
* Update scala-library, scala-reflect to 2.13.11 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7144

## New Contributors

* @rsemlal made their first contribution in https://github.com/http4s/http4s/pull/7118
* @walesho made their first contribution in https://github.com/http4s/http4s/pull/7120
* @diogocanut made their first contribution in https://github.com/http4s/http4s/pull/7129
* @mox692 made their first contribution in https://github.com/http4s/http4s/pull/7091

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.19...v0.23.20

# v0.23.19 (2023-05-12)

This release updates to Cats Effect v3.5.0 which includes an important change to the cancelation semantics of the `Async#async` and `IO.async` methods. Please check the [Cats Effect release notes for v3.5.0](https://github.com/typelevel/cats-effect/releases/tag/v3.5.0) for more details. These changes do not affect Ember, but they do affect Blaze (see https://github.com/http4s/blaze/issues/772) and possibly other http4s backends.

## What's Changed

### http4s-core

* Add X-Content-Type-Options header by @sierikov in https://github.com/http4s/http4s/pull/6981
* Fix base64 parser for http headers Web-Socket-Key and Web-Socket-Accept by @danghieutrung in https://github.com/http4s/http4s/pull/7037
* Add application/graphql media type by @keirlawson in https://github.com/http4s/http4s/pull/7055
* Update log4cats-core, log4cats-noop, ... to 2.6.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7084
* Fix resolving of root / URIs by @armanbilge in https://github.com/http4s/http4s/pull/7064
* Update cats-effect, cats-effect-std, ... to 3.5.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7110
* Update fs2-core, fs2-io to 3.7.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7112

### http4s-laws

* Fix Keep Alive property flake test by @kovstas in https://github.com/http4s/http4s/pull/7019

### http4s-server

* Tweak the `Router#translate` by @danicheg in https://github.com/http4s/http4s/pull/7083

### http4s-client

* Fix `Client.fromHttpApp` does not drain bodies by @kovstas in https://github.com/http4s/http4s/pull/7020

### http4s-client-testkit

* add WSTestClient builder from HttpApp by @kovstas in https://github.com/http4s/http4s/pull/7053

### http4s-ember-core

* Support ember http/2 over unix sockets by @armanbilge in https://github.com/http4s/http4s/pull/7039
* `Hpack` optimizations by @armanbilge in https://github.com/http4s/http4s/pull/7086
* Use `Channel` for H2 `readBuffer` by @armanbilge in https://github.com/http4s/http4s/pull/7096
* Encode trailer headers in Ember by @armanbilge in https://github.com/http4s/http4s/pull/6756

### Documentation

* Server middleware documentation by @fredshonorio in https://github.com/http4s/http4s/pull/6992
* Fix typo on the quickstart page by @danicheg in https://github.com/http4s/http4s/pull/7026
* Fix a broken link to JDKHttpClient in the docs by @satorg in https://github.com/http4s/http4s/pull/7045
* Document ErrorAction middleware by @Quafadas in https://github.com/http4s/http4s/pull/6953

### Behind the scenes

* Don't upload test artifacts by @armanbilge in https://github.com/http4s/http4s/pull/7027
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7032
* Update ip4s-core, ip4s-test-kit to 3.3.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7033
* Update nscplugin, sbt-scala-native, ... to 0.4.12 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7034
* Update logback-classic to 1.2.12 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7035
* Update fs2-core, fs2-io to 3.7.0-RC4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7036
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7040
* Update scalafmt-core to 3.7.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7041
* Tweak `build.sbt` by @danicheg in https://github.com/http4s/http4s/pull/7042
* Update netty-buffer, netty-codec-http to 4.1.91.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7043
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7044
* Update sbt-scala-native-crossproject to 1.3.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7047
* Update sbt-revolver to 0.10.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7051
* Update sbt-scalajs, scalajs-compiler, ... to 1.13.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7056
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7057
* Adopt scala-native-config-brew by @armanbilge in https://github.com/http4s/http4s/pull/6724
* Update sbt-scala-native-crossproject to 1.3.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7071
* Update cats-effect, cats-effect-std, ... to 3.5.0-RC4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7074
* Ember-Core H2Connection: extract "WriteChunk" auxiliary function. by @diesalbla in https://github.com/http4s/http4s/pull/7069
* Update sbt-http4s-org to 0.14.12 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7076
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7089
* Update netty-buffer, netty-codec-http to 4.1.92.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7090
* Ember-Core H2Stream - Extract "cancelWith" method. by @diesalbla in https://github.com/http4s/http4s/pull/7075
* Update cats-effect, cats-effect-std, ... to 3.5.0-RC5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7094
* Ember-Core State - Extract factory method by @diesalbla in https://github.com/http4s/http4s/pull/7070
* Ember-Core / H2Stream / sendData: for-comp is not tail-recursive. by @diesalbla in https://github.com/http4s/http4s/pull/7079
* Update fs2-core, fs2-io to 3.7.0-RC5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7098
* Update case-insensitive, ... to 1.4.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7109
* Update sbt to 1.8.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7111

## New Contributors

* @sierikov made their first contribution in https://github.com/http4s/http4s/pull/6981
* @kovstas made their first contribution in https://github.com/http4s/http4s/pull/7019
* @danghieutrung made their first contribution in https://github.com/http4s/http4s/pull/7037
* @Quafadas made their first contribution in https://github.com/http4s/http4s/pull/6953

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.19-RC3...v0.23.19

# v0.23.19-RC3 (2023-03-15)

This release candidate ships several fixes to Ember client and server. It is built against Cats Effect v3.5.0-RC3 and FS2 v3.7.0-RC2.

## What's Changed

### http4s-core

* Add Deprecation and Sunset Headers by @zan-preston in https://github.com/http4s/http4s/pull/6991

### http4s-ember-core

* All received messages in h2 could have trailers by @ChristopherDavenport in https://github.com/http4s/http4s/pull/7021
* Redirect `H2Connection.writeLoop` errors to logger by @armanbilge in https://github.com/http4s/http4s/pull/7016

### http4s-ember-client

* Add ability to disable SNI in Ember by @joan38 in https://github.com/http4s/http4s/pull/6990
* Detect terminated `EmberConnection`s in ember client prior to attempting a request by @armanbilge in https://github.com/http4s/http4s/pull/6980

### http4s-ember-server

* Handle EndOfStream error in Ember WebSocket server by @zAPFy in https://github.com/http4s/http4s/pull/7008
* Special Case Error Handler For Unparsable Request Line by @isomarcte in https://github.com/http4s/http4s/pull/6934

### http4s-circe

* Update circe-core, circe-generic, ... to 0.14.5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7012

### Behind the scenes

* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7007
* Update nix and cachix actions by @armanbilge in https://github.com/http4s/http4s/pull/7011
* Update sbt-http4s-org to 0.14.11 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7013
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/7022
* Update netty-buffer, netty-codec-http to 4.1.90.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7023

## New Contributors

* @zan-preston made their first contribution in https://github.com/http4s/http4s/pull/6991
* @zAPFy made their first contribution in https://github.com/http4s/http4s/pull/7008

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.19-RC2...v0.23.19-RC3

# v0.23.19-RC2 (2023-02-28)

This release candidate updates to Cats Effect v3.5.0-RC3 and FS2 v3.7.0-RC2.

## What's Changed

### http4s-core

* Update cats-effect, cats-effect-std, ... to 3.5.0-RC3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7004
* Update fs2-core, fs2-io to 3.7.0-RC2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7005

### http4s-server

* Use `ByteVector#toHex` in `CSRF` Middleware by @danicheg in https://github.com/http4s/http4s/pull/6984
* Use `ByteVector#fromHex` in `CSRF` Middleware by @danicheg in https://github.com/http4s/http4s/pull/7002

### http4s-client

* `JavaNetClient` does not need `Async` by @armanbilge in https://github.com/http4s/http4s/pull/6996

### Documentation

* Clarify module choices in quickstart docs by @armanbilge in https://github.com/http4s/http4s/pull/6977

### Behind the scenes

* Update sbt-native-packager to 1.9.16 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6987
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6988
* Update scalafmt-core to 3.7.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6997
* Update scodec-bits to 1.1.36 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6998
* Update scodec-bits to 1.1.37 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/7003

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.19-RC1...v0.23.19-RC2

# v0.23.19-RC1 (2023-02-20)

This release candidate updates to Cats Effect v3.5.0-RC2, which brings [major changes](https://github.com/typelevel/cats-effect/releases/tag/v3.5.0-RC1), as well as FS2 v3.7.0-RC1. Otherwise there are no significant changes in http4s itself.

## What's Changed

### http4s-core

* Update nscplugin, sbt-scala-native, ... to 0.4.10 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6946
* Update sbt-scalajs, scalajs-compiler, ... to 1.13.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6962
* Update scodec-bits to 1.1.35 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6961
* Allow to customize flags for file decoders by @armanbilge in https://github.com/http4s/http4s/pull/6982
* Update to CE v3.5.0-RC2, FS2 v3.7.0-RC1 by @armanbilge in https://github.com/http4s/http4s/pull/6985

### http4s-ember-server

* Fix hanging when stream fails by @TimWSpence in https://github.com/http4s/http4s/pull/6930

### Documentation

* Phase out the client DSL in the docs by @gringrape in https://github.com/http4s/http4s/pull/6944
* Add section with sbt dependencies to quickstart by @valencik in https://github.com/http4s/http4s/pull/6963

### Behind the scenes

* Update sbt-native-packager to 1.9.13 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6928
* Update http4s-circe, http4s-ember-client, ... to 0.23.18 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6929
* Update scalafmt-core to 3.7.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6932
* Update scalafmt-core to 3.7.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6938
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6949
* Refactor `Outcome` usage in `BracketRequestResponseSuite` by @danicheg in https://github.com/http4s/http4s/pull/6948
* Update sbt-jmh to 0.4.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6951
* Update cats-effect, cats-effect-std, ... to 3.4.6 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6956
* Update scala-java-time to 2.5.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6833
* Update locales-minimal-en_us-db to 1.5.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6832
* Update scala3-library, ... to 3.2.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6947
* Update fs2-core, fs2-io to 3.6.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6964
* Update sbt-native-packager to 1.9.14 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6966
* Update fs2-core, fs2-io to 3.6.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6967
* Update sbt-scoverage to 2.0.7 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6969
* Update netty-buffer, netty-codec-http to 4.1.88.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6971
* Update netty-buffer, netty-codec-http to 4.1.89.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6972
* Update sbt-native-packager to 1.9.15 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6973
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6974
* Update cats-effect, cats-effect-std, ... to 3.4.7 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6975
* Tweak `MetricsOps#classifierFMethodWithOptionallyExcludedPath` by @danicheg in https://github.com/http4s/http4s/pull/6976
* Update epollcat to 0.1.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6983

## New Contributors

* @gringrape made their first contribution in https://github.com/http4s/http4s/pull/6944

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.18...v0.23.19-RC1

# v0.23.18 (2023-01-17)

This is a bugfix that addresses a fatal error that affects a small
number of people.  It also contains some optimizations and important
dependency upgrades, including a memory leak in Cats Effect.

## What's Changed
### http4s-core
* Use `Chunk` builder instead of `Buffer` in `ChunkWriter` by @danicheg in https://github.com/http4s/http4s/pull/6919
* Fix StackOverflowError initializing CommonRules by @rossabaker in https://github.com/http4s/http4s/pull/6920
* Update cats-effect, cats-effect-std, ... to 3.4.5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6924
* Update fs2-core, fs2-io to 3.5.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6925

### http4s-ember-core
* Fix Ember H2 end of stream handling by @valencik, @janilcgarcia in https://github.com/http4s/http4s/pull/6882
### http4s-circe
* Use `Chunk` builder in `CirceInstances#streamedJsonArray` by @danicheg in https://github.com/http4s/http4s/pull/6922
### Behind the scenes
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6898
* Try limiting Node.js heap size to 1GB by @armanbilge in https://github.com/http4s/http4s/pull/6894
* Update sbt to 1.8.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6900
* Update cats-parse to 0.3.9 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6902
* Post v0.21.34 cleanup by @rossabaker in https://github.com/http4s/http4s/pull/6904
* Update http4s-circe, http4s-ember-client, ... to 0.23.17 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6907
* Post-0.23.17 cleanup by @rossabaker in https://github.com/http4s/http4s/pull/6906
* Update sbt to 1.8.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6909
* Update sbt-http4s-org to 0.14.10 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6912
* Update netty-buffer, netty-codec-http to 4.1.87.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6918
* Low-hanging Ember optimizations by @armanbilge in https://github.com/http4s/http4s/pull/6753
* Even less memory for Node.js by @armanbilge in https://github.com/http4s/http4s/pull/6921
* Update sbt-native-packager to 1.9.12 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6923

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.17...v0.23.18

# v0.23.17 (2023-01-04)
## What's Changed
### http4s-core
* `Cross-Origin-Resource-Policy` header model by @samspills in https://github.com/http4s/http4s/pull/6709
* Add `Uri.Host.fromString` method by @CraigHammondDexcom in https://github.com/http4s/http4s/pull/6741
* Fix some scaladocs by @danicheg in https://github.com/http4s/http4s/pull/6586
* More efficient string building from `Headers` by @danicheg in https://github.com/http4s/http4s/pull/6776
* Use modeled headers in CORS middleware by @danicheg in https://github.com/http4s/http4s/pull/6790
* Update cats-core, cats-laws to 2.9.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6804
* Deprecate `Query#apply` by @danicheg in https://github.com/http4s/http4s/pull/6817
* Reduce JS size by @armanbilge in https://github.com/http4s/http4s/pull/6835
* Deprecate event stream entity decoder by @armanbilge in https://github.com/http4s/http4s/pull/6843
* Use `Temporal#realTime` in client retry middleware by @armanbilge in https://github.com/http4s/http4s/pull/6834
* Fix for 6851 : Uri.Path.Segment can throw from its equals method by @mattyjbrown in https://github.com/http4s/http4s/pull/6853
* Fixes [CVE-2023-22465](https://github.com/http4s/http4s/security/advisories/GHSA-54w6-vxfh-fw7f)
* Update sbt-scalajs, scalajs-compiler, ... to 1.11.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6815
* Update epollcat to 0.1.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6841
* Update cats-effect, cats-effect-std, ... to 3.4.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6884
* Update vault to 3.5.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6892
### http4s-server
* Reuse predefined `AuthScheme` values by @danicheg in https://github.com/http4s/http4s/pull/6778
* Update cats-effect, cats-effect-std, ... to 3.4.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6805
* Fixes resourceServiceBuilder to work on Windows by @mabasic in https://github.com/http4s/http4s/pull/6826
* Tweak body interrupting for better performance of `DefaultHead` Middleware by @danicheg in https://github.com/http4s/http4s/pull/6855
### http4s-client
* Relax constraints for client gzip middleware by @armanbilge in https://github.com/http4s/http4s/pull/6819
* Use Node.js 18 in devshell/CI by @armanbilge in https://github.com/http4s/http4s/pull/6828
### http4s-ember-core
* Ember-Core H2Stream readBody: extract "pullBuffer" function. by @diesalbla in https://github.com/http4s/http4s/pull/6627
* Special-case `EmptyBody` in request encoder by @armanbilge in https://github.com/http4s/http4s/pull/6752
* Ember-Core: simplify logic for push promises. by @diesalbla in https://github.com/http4s/http4s/pull/6772
* Use Vault#contains to check for PriorKnowledge by @valencik in https://github.com/http4s/http4s/pull/6893
* Update jnr-unixsocket to 0.38.19 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6810
### http4s-ember-server
* `Shutdown#trackConnection` should never be empty by @armanbilge in https://github.com/http4s/http4s/pull/6781
* Add withAdditionalSocketOptions to EmberServerBuilder by @TimWSpence in https://github.com/http4s/http4s/pull/6786
* Fix resource leaks by @TimWSpence in https://github.com/http4s/http4s/pull/6825
### http4s-jawn
* Update jawn-fs2 to 2.4.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6726
### http4s-circe
* Call Chunk.toByteBuffer directly by @CremboC in https://github.com/http4s/http4s/pull/6716
### Documentation
* Clean up client doc a bit by @bplommer in https://github.com/http4s/http4s/pull/6702
* apply syntax highlighting to code blocks in `contributing.md` by @yoshinorin in https://github.com/http4s/http4s/pull/6708
* Added scala k8s to libraries in the adopters page by @hnaderi in https://github.com/http4s/http4s/pull/6714
* Using `mdoc:silent` more liberally throughout docs by @valencik in https://github.com/http4s/http4s/pull/6705
* docs: remove `Future[_]` from presupplied encoders by @yoshinorin in https://github.com/http4s/http4s/pull/6749
* Revamp the integrations page by @armanbilge in https://github.com/http4s/http4s/pull/6801
* Partially Rewrite Client Docs by @valencik in https://github.com/http4s/http4s/pull/6725
* Add http4s-netty to integrations by @hamnis in https://github.com/http4s/http4s/pull/6848
* Add fs2-data to integrations docs page by @ybasket in https://github.com/http4s/http4s/pull/6850
* Update `Uri.fromString` return type in client docs by @yoshinorin in https://github.com/http4s/http4s/pull/6861
### Behind the scenes
* Update http4s-circe, http4s-ember-client to 0.23.16 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6687
* Update scalacheck to 1.17.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6686
* Setup linking paths for OpenSSL on macOS by @armanbilge in https://github.com/http4s/http4s/pull/6691
* Update epollcat to 0.1.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6695
* Update scala-library, scala-reflect to 2.12.17 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6693
* Update sbt-http4s-org to 0.14.5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6688
* Update sbt-scoverage to 2.0.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6707
* Use ubuntu-22.04 in ci by @armanbilge in https://github.com/http4s/http4s/pull/6715
* Invoke brew via full path by @armanbilge in https://github.com/http4s/http4s/pull/6722
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6723
* Provide a dev shell per Java version by @rossabaker in https://github.com/http4s/http4s/pull/6728
* Update sbt to 1.7.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6729
* Use Nix for CI by @armanbilge in https://github.com/http4s/http4s/pull/6727
* Update scala3-library, ... to 3.2.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6730
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6731
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6734
* Update sbt-scoverage to 2.0.5 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6742
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6743
* Update netty-buffer, netty-codec-http to 4.1.84.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6744
* Ember-Core / H2Client /  fromSocket: split for comprehension. by @diesalbla in https://github.com/http4s/http4s/pull/6618
* Ember-Core H2Server: Extract "fulfill push promises" procedure. by @diesalbla in https://github.com/http4s/http4s/pull/6624
* H2Stream - changes and code rewrites. by @diesalbla in https://github.com/http4s/http4s/pull/6660
* Make StreamForking resource safe in the face of cancelation by @RafalSumislawski in https://github.com/http4s/http4s/pull/6745
* Ember-Core H2Server: get streamCreationLock from H2Connection by @diesalbla in https://github.com/http4s/http4s/pull/6638
* Update scala-library, scala-reflect to 2.13.10 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6750
* Update sbt-http4s-org to 0.14.7 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6748
* Ember-Core: factor to H2Server loops from H2Client, H2Server. by @diesalbla in https://github.com/http4s/http4s/pull/6754
* FS2 Streams: replace "evalMap" with "foreach". by @diesalbla in https://github.com/http4s/http4s/pull/6757
* Update scalafmt-core to 3.6.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6760
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6761
* Update sbt-scoverage to 2.0.6 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6764
* A bit more efficient string building from `Seq` collections by @danicheg in https://github.com/http4s/http4s/pull/6766
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6773
* Don't instantiate redundant `NonEmptyList` in `Writer#addList` by @danicheg in https://github.com/http4s/http4s/pull/6777
* Update sbt to 1.7.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6779
* Update scalafmt-core to 3.6.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6782
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6783
* Remove unnecessary native settings by @armanbilge in https://github.com/http4s/http4s/pull/6784
* Fix some typos by @danicheg in https://github.com/http4s/http4s/pull/6785
* Fix the wrong `Http4sClientDsl` location by @danicheg in https://github.com/http4s/http4s/pull/6788
* Scala specific nowarnX annotations by @satorg in https://github.com/http4s/http4s/pull/6751
* Update scala3-library, ... to 3.2.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6792
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6794
* Update sbt to 1.8.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6803
* Update netty-buffer, netty-codec-http to 4.1.85.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6797
* Update to Cats Effect 3.4.0-RC2 by @armanbilge in https://github.com/http4s/http4s/pull/6747
* Rm redundant comments in `Status` by @danicheg in https://github.com/http4s/http4s/pull/6807
* Update sbt-http4s-org to 0.14.8 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6808
* Update jnr-unixsocket to 0.38.18 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6809
* Update cats-effect, cats-effect-std, ... to 3.4.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6811
* Update locales-minimal-en_us-db to 1.5.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6799
* Update munit to 1.0.0-M7 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6813
* Update epollcat to 0.1.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6816
* Fix some deprecation warning messages by @danicheg in https://github.com/http4s/http4s/pull/6818
* Update sbt-http4s-org to 0.14.9 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6823
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6824
* Update nscplugin, sbt-scala-native, ... to 0.4.9 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6829
* Delete flake workflow by @armanbilge in https://github.com/http4s/http4s/pull/6836
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6837
* Update cats-effect, cats-effect-std, ... to 3.4.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6838
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6849
* Mark method `CharPredicate.asMaskBased` as deprecated by @danicheg in https://github.com/http4s/http4s/pull/6847
* Update vault to 3.4.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6854
* Use cached value of `Right(())` to avoid allocations by @danicheg in https://github.com/http4s/http4s/pull/6856
* Update netty-buffer, netty-codec-http to 4.1.86.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6858
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6859
* Eliminate redundant conversions to list by @danicheg in https://github.com/http4s/http4s/pull/6865
* Tweak `Accept` usage to avoid variadic construction by @valencik in https://github.com/http4s/http4s/pull/6866
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6871
* Update cats-effect, cats-effect-std, ... to 3.4.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6877
* Replace numbered RFC rule objects by @rossabaker in https://github.com/http4s/http4s/pull/6863
* Fix formatting of some scaladocs in core by @danicheg in https://github.com/http4s/http4s/pull/6862
* Tweak stream compiling to hex string in `Logger` by @danicheg in https://github.com/http4s/http4s/pull/6881
* JS-Artifact: set header copyright to 2022 by @diesalbla in https://github.com/http4s/http4s/pull/6887
* Ember server reduce state by @diesalbla in https://github.com/http4s/http4s/pull/6888
* Tweak `UrlForm` constructors' performance by @danicheg in https://github.com/http4s/http4s/pull/6795
* Merge 0.22 -> 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/6891
* Use jdk8 in default devshell by @rossabaker in https://github.com/http4s/http4s/pull/6896

## New Contributors
* @yoshinorin made their first contribution in https://github.com/http4s/http4s/pull/6708
* @hnaderi made their first contribution in https://github.com/http4s/http4s/pull/6714
* @samspills made their first contribution in https://github.com/http4s/http4s/pull/6709
* @CraigHammondDexcom made their first contribution in https://github.com/http4s/http4s/pull/6741
* @mabasic made their first contribution in https://github.com/http4s/http4s/pull/6826
* @mattyjbrown made their first contribution in https://github.com/http4s/http4s/pull/6853

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.16...v0.23.17

# v0.22.15 (2023-01-04)

## What's Changed
### http4s-core
* Fixes [CVE-2023-22465](https://github.com/http4s/http4s/security/advisories/GHSA-54w6-vxfh-fw7f)

### Behind the scenes
* Set LoggingHandler in NettyTestServer to the default DEBUG level by @RafalSumislawski in https://github.com/http4s/http4s/pull/6497

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.22.14...v0.22.15

# v0.21.34 (2023-01-04)

## What's changed

### http4s-core
* Fixes [CVE-2023-22465](https://github.com/http4s/http4s/security/advisories/GHSA-54w6-vxfh-fw7f)

## Behind the scenes
* Don't publish website from 0.21 by @armanbilge in https://github.com/http4s/http4s/pull/6151

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.21.33...v0.21.34

# v0.23.16 (2022-09-15)

This release is binary compatible with the 0.23.x series. This is the first release that supports the [Scala Native](https://scala-native.org/) platform. All modules were cross-built, including:

* core, dsl, and laws
* server and client, including middlewares
    * middlewares relying on cryptography require [OpenSSL](https://www.openssl.org/)
* ember server and client, including support for HTTP/2 and TLS
    * requires an [I/O-integrated runtime](https://github.com/typelevel/cats-effect/discussions/3070) for Cats Effect such as [epollcat](https://github.com/armanbilge/epollcat/)
    * requires [s2n-tls](https://github.com/aws/s2n-tls) for TLS
* jawn and circe

In addition, a new [cURL](https://curl.se/)-based client backend is developed in a satellite repo.

* https://github.com/http4s/http4s-curl/

## What's Changed

* Scala Native by @armanbilge in https://github.com/http4s/http4s/pull/6661

### http4s-core

* RFC: Phase out log4s by @armanbilge in https://github.com/http4s/http4s/pull/6614
* Update fs2-core, fs2-io to 3.3.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6670

### http4s-server

* Default redactHeadersWhen to include any header with "token" #6186 by @Lasering in https://github.com/http4s/http4s/pull/6649

### http4s-client

* Deprecate some methods in `Client` by @danicheg in https://github.com/http4s/http4s/pull/6651
* Default redactHeadersWhen to include any header with "token" #6186 by @Lasering in https://github.com/http4s/http4s/pull/6649

### http4s-ember-server

* Expose `Network` constraint in ember builders by @armanbilge in https://github.com/http4s/http4s/pull/6526

### http4s-ember-client

* Expose `Network` constraint in ember builders by @armanbilge in https://github.com/http4s/http4s/pull/6526

### http4s-circe

* Update circe-core, circe-generic, ... to 0.14.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6679

### Behind the scenes

* Update http4s-circe, http4s-ember-client to 0.23.15 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6635
* Update netty-buffer, netty-codec-http to 4.1.80.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6640
* Update case-insensitive, ... to 1.3.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6642
* Pin slf4j to 1.x and cleanup by @armanbilge in https://github.com/http4s/http4s/pull/6644
* Pin `logback-classic` to the `1.2.x` series by @danicheg in https://github.com/http4s/http4s/pull/6647
* Update fs2-core, fs2-io to 3.2.14 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6656
* Update netty-buffer, netty-codec-http to 4.1.81.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6659
* Update to munit 1.0.0-M6 by @armanbilge in https://github.com/http4s/http4s/pull/6657
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6662
* Update netty-buffer, netty-codec-http to 4.1.82.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6663
* Update munit-cats-effect to 2.0.0-M3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6667
* Update ip4s-core, ip4s-test-kit to 3.2.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6666
* Update scalacheck-effect, ... to 2.0.0-M2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6668
* Update keypool to 0.4.8 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6673
* Update vault to 3.3.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6674
* Update sbt-scoverage to 2.0.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6672
* Update log4cats-core, log4cats-noop, ... to 2.5.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6678
* Update jawn-fs2 to 2.3.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6677

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.15...v0.23.16

# v0.23.15 (2022-08-22)

This release is binary compatible with the 0.23.x series.

## What's Changed

### http4s-core

* Prettify error messages when parsing by @danicheg in https://github.com/http4s/http4s/pull/6541
* Rewrite hashcode computation for `Uri.Path` by @FrancescoSerra in https://github.com/http4s/http4s/pull/6555
* Simplify type signature for internal logger by @bplommer in https://github.com/http4s/http4s/pull/6628
* Add SourceMap header by @cobr123 in https://github.com/http4s/http4s/pull/6622
* Update fs2-core, fs2-io to 3.2.12 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6603
* Update literally to 1.1.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6583

### http4s-server

* WebSocketBuilder2: make constructor public by @gvolpe in https://github.com/http4s/http4s/pull/6575
* Cross-compile `GZip` middlewares for JS by @armanbilge in https://github.com/http4s/http4s/pull/6606

### http4s-client

* Fix request logger to log in the case of no request body, when `logBody=true` by @dzanot in https://github.com/http4s/http4s/pull/6535
* Cross-compile `GZip` middlewares for JS by @armanbilge in https://github.com/http4s/http4s/pull/6606

### http4s-ember-core

* Set `serverNames` TLS parameter for ember h2 by @armanbilge in https://github.com/http4s/http4s/pull/6579
* Ember H2 - Do not respond to WindowUpdate with Ping by @ChristopherDavenport in https://github.com/http4s/http4s/pull/6593
* Ember H2 Connection Header Compliance by @ChristopherDavenport in https://github.com/http4s/http4s/pull/6600
* Don't backtrack in request/response prelude parsing by @TimWSpence in https://github.com/http4s/http4s/pull/6578
* Ember Core - H2Server - Split for comprehension by @diesalbla in https://github.com/http4s/http4s/pull/6613

### http4s-ember-server

* Always respond to client close frame with 1000 "normal closure" by @yurique in https://github.com/http4s/http4s/pull/6594
* Parse all WebSocket frames in a `Chunk` in ember-server by @buntec in https://github.com/http4s/http4s/pull/6587

### Documentation

* Fix typo: Add missing comma by @mikela in https://github.com/http4s/http4s/pull/6589
* Tweak client page by @danicheg in https://github.com/http4s/http4s/pull/6605
* Update quickstart guide to include Scala 3 branch by @dsusviela in https://github.com/http4s/http4s/pull/6620

### Behind the scenes

* Release v0.23.14 by @armanbilge in https://github.com/http4s/http4s/pull/6568
* Update http4s-circe, http4s-ember-client, ... to 0.23.14 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6570
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6571
* Update sbt-scoverage to 2.0.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6573
* Update sbt-native-packager to 1.9.10 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6599
* Update sbt-scoverage to 2.0.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6601
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6602
* Update scalafmt-core to 3.5.9 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6609
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6615
* Update sbt-native-packager to 1.9.11 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6616

## New Contributors

* @mikela made their first contribution in https://github.com/http4s/http4s/pull/6589
* @buntec made their first contribution in https://github.com/http4s/http4s/pull/6587
* @dzanot made their first contribution in https://github.com/http4s/http4s/pull/6535
* @TimWSpence made their first contribution in https://github.com/http4s/http4s/pull/6578
* @dsusviela made their first contribution in https://github.com/http4s/http4s/pull/6620
* @cobr123 made their first contribution in https://github.com/http4s/http4s/pull/6622

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.14...v0.23.15

# v0.23.14 (2022-07-25)

This release is binary compatible with 0.23.x and updates fs2 to v3.2.11 which includes a security patch for [GHSA-2cpx-6pqp-wf35](https://github.com/typelevel/fs2/security/advisories/GHSA-2cpx-6pqp-wf35).

## What's Changed

### http4s-core
* Deprecate `BackendBuilder#allocate` by @danicheg in https://github.com/http4s/http4s/pull/6556
* Don't instantiate redundant `List` in `Path#addSegment` by @danicheg in https://github.com/http4s/http4s/pull/6557
* Don't override `endsWithSlash` when adding an empty `Path` segment by @danicheg in https://github.com/http4s/http4s/pull/6564
* Update to fs2 3.2.11 by @armanbilge in https://github.com/http4s/http4s/pull/6566

### http4s-server
* fix typo in `Throttle` middleware `httpAapp->httpApp` by @jbwheatley in https://github.com/http4s/http4s/pull/6501

### http4s-ember-core
* Ember-Core: reimplement "combineArrays" by @diesalbla in https://github.com/http4s/http4s/pull/6518
* H2 Settings Acknowledgments do not need to block progress by @ChristopherDavenport in https://github.com/http4s/http4s/pull/6553

### http4s-ember-server
* Ember-Server: WebSocketHelper rewrite / optimisation by @diesalbla in https://github.com/http4s/http4s/pull/6388

### Documentation
* Release note tweaks for v0.23.13 by @rossabaker in https://github.com/http4s/http4s/pull/6494
* Don't use `Stream` in JSON client example by @armanbilge in https://github.com/http4s/http4s/pull/6511
* Add Hireproof to adopters by @taig in https://github.com/http4s/http4s/pull/6536
* Remove references to `HttpService` in `testing.md` by @danicheg in https://github.com/http4s/http4s/pull/6548
* Fix Blaze Doc for Websocket Server by @ChristopherDavenport in https://github.com/http4s/http4s/pull/6560
* Freshen up `Middleware` docs by @danicheg in https://github.com/http4s/http4s/pull/6554

### Behind the scenes
* Update http4s-circe, http4s-ember-client, ... to 0.23.13 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6493
* Update sbt-scoverage to 2.0.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6495
* Update sbt-scalajs, scalajs-compiler, ... to 1.10.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6496
* Update locales-minimal-en_us-db to 1.4.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6502
* Update cats-effect, cats-effect-std, ... to 3.3.13 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6503
* Update fs2-core, fs2-io to 3.2.9 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6505
* Create `.git-blame-ignore-revs` by @armanbilge in https://github.com/http4s/http4s/pull/6506
* Custom branch for update flake action by @armanbilge in https://github.com/http4s/http4s/pull/6498
* Smite `Seq` in tests by @danicheg in https://github.com/http4s/http4s/pull/6507
* Update cats-parse to 0.3.8 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6509
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6510
* Update jawn-parser to 1.4.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6513
* Update fs2-core, fs2-io to 3.2.10 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6522
* Update sbt to 1.7.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6527
* Update sbt to 1.7.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6530
* Update netty-buffer, netty-codec-http to 4.1.79.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6529
* Update cats-effect, cats-effect-std, ... to 3.3.14 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6532
* Update log4cats-core, log4cats-noop, ... to 2.4.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6534
* Update to update-flake-lock v10 by @armanbilge in https://github.com/http4s/http4s/pull/6531
* Update sbt-http4s-org to 0.14.4 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6537
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6546

## New Contributors
* @jbwheatley made their first contribution in https://github.com/http4s/http4s/pull/6501

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.13...v0.23.14

# v0.23.13 (2022-06-25)

This release is binary compatible with 0.23.x, and additionally includes the fixes in v0.22.14.

## What's Changed
### http4s-core
* Add `EntityDecoder[EventStream]` by @armanbilge in https://github.com/http4s/http4s/pull/6413
* Update to Vault 3.2.1 by @armanbilge in https://github.com/http4s/http4s/pull/6431
* Update scala-java-time to 2.4.0 by @typelevel-steward in https://github.com/http4s/http4s/pull/6434
* Update scodec-bits to 1.1.34 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6455
* Update fs2-core, fs2-io to 3.2.8 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6461
* Update cats-core, cats-laws to 2.8.0 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6471
* Add Ordering for MediaRange by @FrancescoSerra in https://github.com/http4s/http4s/pull/6486
* Add `Host#fromIp4sHost` method by @danicheg in https://github.com/http4s/http4s/pull/6489
* Make Uri.Path.merge compliant by @FrancescoSerra in https://github.com/http4s/http4s/pull/6481
### http4s-server
* Compose multiple subsequent `Message#putHeaders` calls by @danicheg in https://github.com/http4s/http4s/pull/6459
### http4s-ember-core
* Encoding of response with empty body by @christiankjaer in https://github.com/http4s/http4s/pull/6444
* Update log4cats-core, log4cats-noop, ... to 2.3.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6478
### Documentation
* Added note on possible circe import error in docs by @cgoldammer in https://github.com/http4s/http4s/pull/6450
* Add Wide Angle Analytics in adopters by @jrozanski in https://github.com/http4s/http4s/pull/6454
* Add sample curl command to quickstart.md by @ajelden in https://github.com/http4s/http4s/pull/6488
* Release v0.23.13 by @rossabaker in https://github.com/http4s/http4s/pull/6492
### Behind the scenes
* Cleanup unnecessary projects by @armanbilge in https://github.com/http4s/http4s/pull/6410
* Update http4s-circe, http4s-ember-client to 0.23.12 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6414
* Ember-Core - H2Client - Rewrite without alternative. by @diesalbla in https://github.com/http4s/http4s/pull/6422
* Merge 0.22 -> 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/6429
* Enable Scaladoc Linking Warnings by @isomarcte in https://github.com/http4s/http4s/pull/4027
* Add a Scala Steward workflow by @rossabaker in https://github.com/http4s/http4s/pull/6432
* Delete steward.yml by @rossabaker in https://github.com/http4s/http4s/pull/6438
* Update .mergify.yml by @armanbilge in https://github.com/http4s/http4s/pull/6439
* Update scodec-bits to 1.1.33 by @typelevel-steward in https://github.com/http4s/http4s/pull/6436
* Update scalafmt-core to 3.5.8 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6442
* Workflow to update flake weekly by @rossabaker in https://github.com/http4s/http4s/pull/6437
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6445
* Server Metrics Middleware by @diesalbla in https://github.com/http4s/http4s/pull/6246
* Ember-Core H2Server: replace pull with a recursive function by @diesalbla in https://github.com/http4s/http4s/pull/6424
* Throttle Server middleware: use recursion by @diesalbla in https://github.com/http4s/http4s/pull/6267
* Fix steward name in mergify config by @armanbilge in https://github.com/http4s/http4s/pull/6448
* Update sbt-http4s-org to 0.14.1 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6447
* MultipartParser: use `Pull.done` instead of Pull.pure. by @diesalbla in https://github.com/http4s/http4s/pull/6449
* Ember-Core H2Server: extract method to send initial request by @diesalbla in https://github.com/http4s/http4s/pull/6425
* Ember-Client: no Alternative by @diesalbla in https://github.com/http4s/http4s/pull/6426
* Backport of `Router#define` test by @danicheg in https://github.com/http4s/http4s/pull/6451
* Update sbt-http4s-org to 0.14.2 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6452
* Setup scoverage by @armanbilge in https://github.com/http4s/http4s/pull/6456
* Run coverage job for PRs, but don't upload results by @armanbilge in https://github.com/http4s/http4s/pull/6457
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6458
* Update sbt-http4s-org to 0.14.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6463
* series/0.22 -> series/0.23 by @armanbilge in https://github.com/http4s/http4s/pull/6465
* Remove scalafix migrations, plugin cleanup by @http4s-steward in https://github.com/http4s/http4s/pull/6460
* Update scala-library, scala-reflect to 2.12.16 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6462
* Update netty-buffer, netty-codec-http to 4.1.78.Final in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6468
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6466
* flake.lock: Update by @http4s-steward in https://github.com/http4s/http4s/pull/6480
* Update scala3-library, ... to 3.1.3 in series/0.23 by @http4s-steward in https://github.com/http4s/http4s/pull/6482
* Test control on time based tests by @FrancescoSerra in https://github.com/http4s/http4s/pull/6469
* Merge 0.22.14 -> 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/6487

## New Contributors
* @christiankjaer made their first contribution in https://github.com/http4s/http4s/pull/6444
* @cgoldammer made their first contribution in https://github.com/http4s/http4s/pull/6450
* @jrozanski made their first contribution in https://github.com/http4s/http4s/pull/6454
* @leoniv made their first contribution in https://github.com/http4s/http4s/pull/6473
* @FrancescoSerra made their first contribution in https://github.com/http4s/http4s/pull/6469
* @ajelden made their first contribution in https://github.com/http4s/http4s/pull/6488

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.12...v0.23.13
# v0.22.14 (2022-06-23)

This release is binary compatible with 0.22.x series.  Routine maintenance has stopped on 0.22.x, but we'll continue to entertain patches from the community.  All users are encouraged to upgrade to 0.23 (the latest stable series, on Cats-Effect 3).

## What's Changed
### http4s-core
* Fix Content-Disposition filename encoding by @leoniv in https://github.com/http4s/http4s/pull/6473
* Add filename property to Content-Disposition by @rossabaker in https://github.com/http4s/http4s/pull/6485
### Documentation
* Point upgrade docs at scalafix published in series/0.22 by @armanbilge in https://github.com/http4s/http4s/pull/6464
### Behind the scenes
* Delete hard-coded Scaladoc url by @armanbilge in https://github.com/http4s/http4s/pull/6402
* Update to sbt-http4s-org 0.13.4 by @armanbilge in https://github.com/http4s/http4s/pull/6428

## New Contributors
* @leoniv made their first contribution in https://github.com/http4s/http4s/pull/6473

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.22.13...v0.22.14

# v0.23.12 (2022-05-24)

This release is binary compatible with the 0.23.x series.

## The Great Schism

It is the first release after "The Great Schism", where several integrations are published separately.  These include:

* [http4s-async-http-client](https://github.com/http4s/http4s-async-http-client)
* [http4s-blaze-client](https://github.com/http4s/blaze)
* [http4s-blaze-server](https://github.com/http4s/blaze)
* [http4s-boopickle](https://github.com/http4s/http4s-boopickle)
* [http4s-dropwizard-metrics](https://github.com/http4s/http4s-dropwizard-metrics)
* [http4s-jetty-client](https://github.com/http4s/http4s-jetty)
* [http4s-jetty-server](https://github.com/http4s/http4s-jetty)
* [http4s-okhttp-client](https://github.com/http4s/http4s-okhttp-client)
* [http4s-play-json](https://github.com/http4s/http4s-play-json)
* [http4s-prometheus-metrics](https://github.com/http4s/http4s-prometheus-metrics)
* [http4s-scala-xml](https://github.com/http4s/http4s-scala-xml)
* [http4s-scalatags](https://github.com/http4s/http4s-scalatags)
* [http4s-servlet](https://github.com/http4s/http4s-servlet)
* [http4s-tomcat](https://github.com/http4s/http4s-tomcat)
* [http4s-twirl](https://github.com/http4s/http4s-twirl)

Be aware that versions of these modules will be untethered from the core version they depend on.
* These modules may not be republished with each core patch release, but will still work on the latest 0.23 core.
* Some of these modules will see breaking releases to upgrade their integrated dependencies, based on the 0.23 core.  These upgrades could previously not be undertaken without a breaking change of the entire http4s ecosystem.
* All modules will continue to adhere to [early semver](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html#early-semver-and-sbt-version-policy).
* We recommend [sbt-updates](https://github.com/rtimush/sbt-updates) or [Scala Steward](https://github.com/scala-steward-org/scala-steward) for all your dependencies, and heeding SBT's eviction warnings.
* We would like to welcome new maintainers to help out on each of these modules.  Look for the "help wanted" label in these repos.

## What's Changed
### http4s-core
* Make MimeDB go away on JS by @armanbilge in https://github.com/http4s/http4s/pull/6211
* Update sbt-scalajs, scalajs-compiler, ... to 1.10.0 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6245
* Use thread unsafe "lazy val"s to avoid deadlocks initializing MimeDB by @armanbilge in https://github.com/http4s/http4s/pull/6248
* Move `withContentLength` from Message to  Headers by @bplommer in https://github.com/http4s/http4s/pull/6285
* Follow links when accessing file attributes in `StaticFile` by @armanbilge in https://github.com/http4s/http4s/pull/6368
* Use `ce.std.Random` for `Multiparts` by @armanbilge in https://github.com/http4s/http4s/pull/6283
* Add max bytes handling to `Message#toStrict` by @danicheg in https://github.com/http4s/http4s/pull/6316
### http4s-server
* Relax ContextRouter's constraints by @danicheg in https://github.com/http4s/http4s/pull/6164
* Use CE `Random.javaSecuritySecureRandom` instead of Java `SecureRandom` by @armanbilge in https://github.com/http4s/http4s/pull/6252
* httpRoutes and httpApp shortcuts for Timeout middleware by @voidcontext in https://github.com/http4s/http4s/pull/6366
* httpRoutes and httpApp shortcuts for Throttle middleware by @voidcontext in https://github.com/http4s/http4s/pull/6365
* Highlight the uncancelable behavior in `Timeout` middleware scaladoc by @danicheg in https://github.com/http4s/http4s/pull/6407
* Tweak `Timeout` middleware scaladoc by @danicheg in https://github.com/http4s/http4s/pull/6409
### http4s-client
* Fix `Client#translate` and relax constraints by @armanbilge in https://github.com/http4s/http4s/pull/6139
* Add AttemptCountKey - Allow other middlewares access to what retry count we are on. by @ChristopherDavenport in https://github.com/http4s/http4s/pull/6367
* Drain response body in `DefaultClient#defaultOnError` by @danicheg in https://github.com/http4s/http4s/pull/6376
### http4s-ember-core
* Avoid array copies when splitting chunks in ember chunked decoder by @wemrysi in https://github.com/http4s/http4s/pull/6210
### http4s-ember-server
* Resolve broken filterPingPongs using WebSocketBuild2 with Ember by @CharlesAHunt in https://github.com/http4s/http4s/pull/6036
### Documentation
* Fixed Dead Links in further-reading.md by @dragonfly-ai in https://github.com/http4s/http4s/pull/6203
* Tweak badges in the readme by @danicheg in https://github.com/http4s/http4s/pull/6280
* Mark the 0.22 version as EOL at the website by @danicheg in https://github.com/http4s/http4s/pull/6334
* Tweak the contributing guide by @danicheg in https://github.com/http4s/http4s/pull/6338
* Add On Air Entertainment to list of adopters. by @OnAirEntertainment-Scala in https://github.com/http4s/http4s/pull/6385
### Behind the scenes
* Use parasitic EC in the blaze for Scala 2.13 by @danicheg in https://github.com/http4s/http4s/pull/6145
* Merge `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/6143
* Release v0.21.33 by @rossabaker in https://github.com/http4s/http4s/pull/6147
* Merge 0.22 to 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/6154
* Update cats-effect, cats-effect-laws, ... to 3.3.8 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6157
* Remove sbt-scalajs-crossproject dependency by @scala-steward in https://github.com/http4s/http4s/pull/6174
* Move WebSocketHandshake to blaze-core by @rossabaker in https://github.com/http4s/http4s/pull/6183
* Update cats-effect, cats-effect-laws, ... to 3.3.9 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6185
* Merge 0.22 -> 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/6178
* Get sbt-doctest from 0.22 by @rossabaker in https://github.com/http4s/http4s/pull/6194
* Merge 0.22 -> 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/6192
* Merge 0.22 -> 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/6196
* Update fs2-core, fs2-io, ... to 3.2.7 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6209
* Update http4s-crypto to 0.2.3 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6216
* Update cats-parse from 0.22 by @rossabaker in https://github.com/http4s/http4s/pull/6226
* Tweak `async` usage in `Http1Connection` by @danicheg in https://github.com/http4s/http4s/pull/6208
* Merge 0.22 -> 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/6227
* Update cats-effect, cats-effect-laws, ... to 3.3.10 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6231
* Ignore `scala-xml` updates by @danicheg in https://github.com/http4s/http4s/pull/6236
* Remove war example by @rossabaker in https://github.com/http4s/http4s/pull/6238
* Update cats-effect, cats-effect-laws, ... to 3.3.11 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6251
* Move JS size-test-app to Test scope, fix flaky CI OOMs? by @armanbilge in https://github.com/http4s/http4s/pull/6255
* Server - Chunk Aggregator Middleware: code and docs by @diesalbla in https://github.com/http4s/http4s/pull/6258
* Server - JsonP Middleware - extract auxiliary function. by @diesalbla in https://github.com/http4s/http4s/pull/6244
* Move `SegmentEncoderSuite` to correct location by @armanbilge in https://github.com/http4s/http4s/pull/6265
* Avoid Alternative-Guard by @diesalbla in https://github.com/http4s/http4s/pull/6259
* Ignore `java-websocket` updates by @danicheg in https://github.com/http4s/http4s/pull/6275
* Merge `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/6278
* Spin off servlet, jetty-server, and tomcat modules by @rossabaker in https://github.com/http4s/http4s/pull/6240
* Update scalacheck to 1.16.0 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6264
* Update discipline-core to 1.5.1 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6303
* Merge `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/6310
* Tweak unused args suppressing by @danicheg in https://github.com/http4s/http4s/pull/6300
* Remove update ignorings in Scala Steward conf for the 0.23 by @danicheg in https://github.com/http4s/http4s/pull/6333
* Update log4cats-core, log4cats-noop, ... to 2.3.0 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6340
* Merge 0.22 -> 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/6343
* Update scalacheck-effect, ... to 1.0.4 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6345
* Update scodec-bits to 1.1.31 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6330
* Server - ContextRouter - Simpler Code  by @diesalbla in https://github.com/http4s/http4s/pull/6241
* Fix test name by @armanbilge in https://github.com/http4s/http4s/pull/6351
* Use UTC for JS tests, remove tzdb test dep by @armanbilge in https://github.com/http4s/http4s/pull/6350
* Delete servlet and jetty-server srcs by @armanbilge in https://github.com/http4s/http4s/pull/6354
* Bye-bye boopickle by @armanbilge in https://github.com/http4s/http4s/pull/6353
* Actually delete boopickle srcs by @armanbilge in https://github.com/http4s/http4s/pull/6359
* Update log4cats-core, log4cats-noop, ... to 2.3.1 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6362
* Only create scalafix job for 2.13 by @armanbilge in https://github.com/http4s/http4s/pull/6361
* Update scala3-library, ... to 3.1.2 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6291
* Update netty-buffer, netty-codec-http to 4.1.77.Final in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6364
* Ember-Core: merge evalMap blocks in writeLoop by @diesalbla in https://github.com/http4s/http4s/pull/6162
* Ember-Core microptimisation: avoid lists by @diesalbla in https://github.com/http4s/http4s/pull/6161
* Publish internal scalafixes by @armanbilge in https://github.com/http4s/http4s/pull/6268
* Bye-bye scala-xml by @armanbilge in https://github.com/http4s/http4s/pull/6352
* Parallelize some requests in `RetrySuite` by @danicheg in https://github.com/http4s/http4s/pull/6380
* Update ip4s-core, ip4s-test-kit to 3.1.3 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6382
* Promote using of `Headers#contains` by @danicheg in https://github.com/http4s/http4s/pull/6386
* Use `GenTemporal` for proceeding with timeouts by @danicheg in https://github.com/http4s/http4s/pull/6391
* JS refactoring in preparation for client backend schism by @armanbilge in https://github.com/http4s/http4s/pull/6390
* Use `parTraverse` in tests by @danicheg in https://github.com/http4s/http4s/pull/6393
* Update locales-minimal-en_us-db to 1.4.0 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6399
* Update circe-core, circe-generic, ... to 0.14.2 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6398
* Publish `http4s-client-testkit` module by @armanbilge in https://github.com/http4s/http4s/pull/6394
* Make Node.js interop APIs private by @armanbilge in https://github.com/http4s/http4s/pull/6404
* Update cats-effect, cats-effect-laws, ... to 3.3.12 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6406
* Ember Core: write readLoop without Streams. by @diesalbla in https://github.com/http4s/http4s/pull/6163
### http4s-circe
* Update to circe 0.14.2 by @armanbilge in https://github.com/http4s/http4s/pull/6401

## New Contributors
* @teigen made their first contribution in https://github.com/http4s/http4s/pull/6057
* @takapi327 made their first contribution in https://github.com/http4s/http4s/pull/6166
* @zainab-ali made their first contribution in https://github.com/http4s/http4s/pull/6098
* @dragonfly-ai made their first contribution in https://github.com/http4s/http4s/pull/6203
* @CharlesAHunt made their first contribution in https://github.com/http4s/http4s/pull/6036
* @OnAirEntertainment-Scala made their first contribution in https://github.com/http4s/http4s/pull/6385

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.11...v0.23.12

# v0.22.13 (2022-05-20)

This release is binary compatible with 0.22.x series. 
Routine maintenance has stopped on 0.22.x, but we'll continue to entertain patches from the community.
All users are encouraged to upgrade to 0.23 (the latest stable series, on Cats-Effect 3). 


* http4s-core
    * Remove redundant draining of request/response body by @danicheg in https://github.com/http4s/http4s/pull/6128
    * Use predefined Close connection header by @danicheg in https://github.com/http4s/http4s/pull/6167
    * Fix comment in org.http4s.Message.scala by @takapi327 in https://github.com/http4s/http4s/pull/6166
    * Render a trailing newline on multipart close-delimiter by @rossabaker in https://github.com/http4s/http4s/pull/6170
    * Add mapK to MetricsOps by @hamnis in https://github.com/http4s/http4s/pull/6172
    * Add Random shim for Cats-Effect 2 by @rossabaker in https://github.com/http4s/http4s/pull/6165
    * Add Scalafix explicit result type rule by @danicheg in https://github.com/http4s/http4s/pull/6134
    * Add withContentLength and toStrict helpers to Message by @rossabaker in https://github.com/http4s/http4s/pull/6176
    * Improvements to multipart boundaries by @rossabaker in https://github.com/http4s/http4s/pull/6169
    * Update cats-parse to 0.3.7 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6224
    * Fix `SelectOpsMultiple#renderString` by @danicheg in https://github.com/http4s/http4s/pull/6307
    * Update fs2-core, fs2-io, ... to 2.5.11 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6322
    * Update to Cats Effect 2.5.5 by @armanbilge in https://github.com/http4s/http4s/pull/6392

* http4s-server
    * Routing on variable segments by @teigen in https://github.com/http4s/http4s/pull/6057
    * Resolve #6068 digestauth challenge redux by @blast-hardcheese in https://github.com/http4s/http4s/pull/6138
    * Integrate Random into DigestAuth by @rossabaker in https://github.com/http4s/http4s/pull/6177
    * Update scalafmt-core to 3.5.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6223
    * Deprecate PushSupport by @rossabaker in https://github.com/http4s/http4s/pull/6247

* http4s-client
    * Clarify connection lifecycle by @rossabaker in https://github.com/http4s/http4s/pull/6313

* http4s-blaze-core
    * Use tryScheduling within IdleTimeoutStage by @hamnis in https://github.com/http4s/http4s/pull/6198
    * Remove synchronizations in `TestHead`, `QueueTestHead`, `SlowTestHead` by @danicheg in https://github.com/http4s/http4s/pull/6249

* http4s-blaze-server
    * Clean up `BlazeServerBuilder` scaladoc by @danicheg in https://github.com/http4s/http4s/pull/6180
    * Blaze server enhancements by @danicheg in https://github.com/http4s/http4s/pull/6179

* http4s-blaze-client
    * Fix counting of current allocated connections in the blaze client pool manager by @danicheg in https://github.com/http4s/http4s/pull/6254
    * Roll back the #6254 by @danicheg in https://github.com/http4s/http4s/pull/6332

* http4s-tomcat
    * Update tomcat-catalina, tomcat-coyote, ... to 9.0.62 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6214
    
* http4s-scala-xml
    * Update scala-xml to 2.1.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6234
    
* http4s-async-http-client
    * Update netty-buffer, netty-codec-http to 4.1.76.Final in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6292

* Behind the scenes
    * Add unidocs project to root aggregate by @armanbilge in https://github.com/http4s/http4s/pull/6129
    * Update tomcat-catalina, tomcat-coyote, ... to 9.0.60 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6132
    * Update http4s-circe, http4s-ember-client to 0.23.11 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6149
    * Merge after 0.21.33 by @rossabaker in https://github.com/http4s/http4s/pull/6153
    * Don't override api url by @armanbilge in https://github.com/http4s/http4s/pull/6158
    * Fix StatusSpec sanitization property by @rossabaker in https://github.com/http4s/http4s/pull/6184
    * Update sbt-doctest to 0.10.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6190
    * Update sbt-http4s-org to 0.13.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6197
    * Pin http4s-crypto to 0.1.x in 0.22 by @armanbilge in https://github.com/http4s/http4s/pull/6218
    * Update jetty-client, jetty-http, ... to 9.4.46.v20220331 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6220
    * Update scalafmt-core to 3.5.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6256
    * Update sbt-scalafix, scalafix-core, ... to 0.10.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6260
    * Update Java-WebSocket to 1.5.3 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6270
    * Fix deprecated since versions by @danicheg in https://github.com/http4s/http4s/pull/6279
    * Remove ignoring some files for `doctest` by @danicheg in https://github.com/http4s/http4s/pull/6284
    * Update sbt-http4s-org to 0.13.2 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6287
    * Update scalafmt-core to 3.5.2 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6318
    * Upgrade jawn-fs2, keypool, log4cats, vault to last CE2 versions by @rossabaker in https://github.com/http4s/http4s/pull/6383

* New Contributors
    * @teigen made their first contribution in https://github.com/http4s/http4s/pull/6057
    * @takapi327 made their first contribution in https://github.com/http4s/http4s/pull/6166

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.22.12...v0.22.13

# v0.21.33 (2022-03-18)

This is a courtesy release for the 0.21.x series.  This series remains officially unmaintained except for urgent security patches.  It is binary compatible with the 0.21.x series.

* http4s-ember-core
    * Support parsing response bodies without Content-Length in ember (0.21) by @wemrysi in https://github.com/http4s/http4s/pull/6136

* Behind the scenes
    * Build tweaks in case there's another 0.21 by @rossabaker in https://github.com/http4s/http4s/pull/6034

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.21.32...v0.21.33

# v0.23.11 (2022-03-18)

This is a maintenance release, binary compatible with the 0.23.x series.  It also includes the changes in 0.22.12.

* http4s-core
    * Update sbt-scalajs, scalajs-compiler, ... to 1.9.0 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6045
    * Update http4s-crypto to 0.2.2 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6053
    * Update fs2-core, fs2-io, ... to 3.2.5 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6065
    * Update cats-effect, cats-effect-laws, ... to 3.3.7 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6093

* http4s-server
    * Add Additional ErrorHandling Options by @ChristopherDavenport in https://github.com/http4s/http4s/pull/6047

* http4s-client
    * Make `WSClient` and friends public by @armanbilge in https://github.com/http4s/http4s/pull/6005
    * Add OptionT Based Versions Of expectOption* by @isomarcte in https://github.com/http4s/http4s/pull/6135

* http4s-ember-core
    * Replace npm hpack.js with pure Scala.js hpack by @armanbilge in https://github.com/http4s/http4s/pull/6009

* http4s-ember-client
    * Add scaladocs for EmberClientBuilder by @valencik in https://github.com/http4s/http4s/pull/5999
    * Fix ember client cancellation bug by @ChristopherDavenport in https://github.com/http4s/http4s/pull/6085

* http4s-blaze-core
    * Optimize CachingChunkWriter for Chunk.empty case by @wjoel in https://github.com/http4s/http4s/pull/6092
    * Add micro-opts for `CachingChunkWriter` by @danicheg in https://github.com/http4s/http4s/pull/6096
    * Further reduce ExecutionContexts in blaze by @rossabaker in https://github.com/http4s/http4s/pull/6118

* http4s-servlet
    * Adds the async timeout as a method param by @yuferpegom in https://github.com/http4s/http4s/pull/6037
    * Use blocking EC in the `BlockingServletIo` by @danicheg in https://github.com/http4s/http4s/pull/6133

* Behind the scenes
    * Merge Jetty forward from 0.22 by @rossabaker in https://github.com/http4s/http4s/pull/6020
    * Merge 0.22 into 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/6004
    * Merge 0.22 -> 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/6031
    * Build tweaks in case there's another 0.21 by @rossabaker in https://github.com/http4s/http4s/pull/6034
    * Merge 0.22 -> 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/6054
    * Update cats-effect, cats-effect-laws, ... to 3.3.6 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/6073
    * Caching Middleware - Small Rewrites by @diesalbla in https://github.com/http4s/http4s/pull/6066
    * Ignore `sbt-buildinfo` updates by @danicheg in https://github.com/http4s/http4s/pull/6084
    * Update Scala Steward config by @danicheg in https://github.com/http4s/http4s/pull/6089
    * Message: add pipeBodyThrough method by @diesalbla in https://github.com/http4s/http4s/pull/6011
    * Merge 0.22 -> 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/6094
    * Remove unused ExecutionContexts in blaze-core by @rossabaker in https://github.com/http4s/http4s/pull/6100
    * Deprecate internal Trampoline by @rossabaker in https://github.com/http4s/http4s/pull/6119
    * Backport mergify config to 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/6126
    * Merge 0.22 -> 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/6125
    * Fix the unidoc artifact by @armanbilge in https://github.com/http4s/http4s/pull/6142

* New Contributors
    * @yuferpegom made their first contribution in https://github.com/http4s/http4s/pull/6037

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.10...v0.23.11

# v0.22.12 (2022-03-14)

This is a maintenance release, binary compatible with the 0.22.x series.  It also includes all the bugfixes from 0.21.32.

* http4s-core
    * More tolerant cookie parsing by @kailuowang in https://github.com/http4s/http4s/pull/6082

* http4s-tomcat
    * Update tomcat-catalina, tomcat-coyote, ... to 9.0.59 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6075

* http4s-jetty-server
    * Upgrade to jetty-9.4.45.v20220203 on series/0.22 by @rossabaker in https://github.com/http4s/http4s/pull/6023

* http4s-async-http-client
    * Update netty-buffer, netty-codec-http to 4.1.75.Final in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6102
    * Deprecate async-http-client by @rossabaker in https://github.com/http4s/http4s/pull/6114

* Documentation
    * docs: remove workarounds for issues in Laika 0.18.0 by @jenshalm in https://github.com/http4s/http4s/pull/6021
    * Docs: ember supports websockets by @Daenyth in https://github.com/http4s/http4s/pull/6097

* Behind the scenes
    * Update http4s-circe, http4s-ember-client to 0.23.10 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6002
    * Add convenience methods to `WSConnectionHighLevel` by @armanbilge in https://github.com/http4s/http4s/pull/6001
    * Update scalafmt-core to 3.4.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6006
    * Update scalafmt-core to 3.4.2 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6010
    * Ignore Jetty HTTP/2 updates by @rossabaker in https://github.com/http4s/http4s/pull/6022
    * Update netty-buffer, netty-codec-http to 4.1.74.Final in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6025
    * Update sbt-http4s-org to 0.12.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6024
    * Update slf4j-api to 1.7.36 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6030
    * Build tweaks in case there's another 0.21 by @rossabaker in https://github.com/http4s/http4s/pull/6034
    * Update scalafmt-core to 3.4.3 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6038
    * Update sbt-native-packager to 1.9.8 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6043
    * Update sbt-http4s-org to 0.12.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6052
    * Update sbt-native-packager to 1.9.9 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6070
    * Update sbt-http4s-org to 0.12.2 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6081
    * Update sbt-buildinfo to 0.11.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6062
    * Update logback-classic to 1.2.11 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6088
    * Type annotations for public API things by @danicheg in https://github.com/http4s/http4s/pull/5822
    * Update metrics-core, metrics-json to 4.2.9 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6099
    * Update sbt-http4s-org to 0.13.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/6103
    * Merge 0.21 -> 0.22 by @rossabaker in https://github.com/http4s/http4s/pull/6121
    * Exclude series/0.21 from release notes by @rossabaker in https://github.com/http4s/http4s/pull/6122
    * Simplify website configuration by @armanbilge in https://github.com/http4s/http4s/pull/6087

* New Contributors
    * @Daenyth made their first contribution in https://github.com/http4s/http4s/pull/6097

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.22.11...v0.22.12

# v0.21.32 (2022-02-09)

This is an unplanned bugfix release for the 0.21.x series.  This series remains officially unmaintained except for urgent security patches.  It plugs a tiny leak on the server backends when a resource is canceled between acquisition and compiling its body.

* http4s-servlet
    * Flush the prelude in non-blocking Servlet IO by @rossabaker in https://github.com/http4s/http4s/pull/6027
    * Render continually between response prelude and body by @rossabaker in https://github.com/http4s/http4s/pull/6028

* http4s-blaze-server
    * Render continually between response prelude and body by @rossabaker in https://github.com/http4s/http4s/pull/6028

* http4s-ember-server
    * Render continually between response prelude and body by @rossabaker in https://github.com/http4s/http4s/pull/6028

* Behind the scenes
    * Merge series/0.20 to series/0.21 by @rossabaker in https://github.com/http4s/http4s/pull/5818

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.21.31...v0.21.32

# v0.23.10 (2022-02-03)

This is a maintenance release, binary compatible with 0.23.x.  It also includes merges of all the changes in 0.22.10.

* http4s-ember-core
    * Don't force npm onto ember.js users by @armanbilge in https://github.com/http4s/http4s/pull/5992

* Documentation
    * Use ember in docs by @valencik in https://github.com/http4s/http4s/pull/5970
    * Update static content docs, fix deprecations by @valencik in https://github.com/http4s/http4s/pull/5973
    * Tweak json.md to work on scala 2.13 by @valencik in https://github.com/http4s/http4s/pull/5984
    * Mdoc warnings v23 by @valencik in https://github.com/http4s/http4s/pull/5979
    * Remove most mdoc nest modifiers by @valencik in https://github.com/http4s/http4s/pull/5977

* Behind the scenes
    * Update scala3-library, ... to 3.1.1 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5985
    * Merge 0.22 to 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/5987
    * Merge from 0.22 by @rossabaker in https://github.com/http4s/http4s/pull/5995
    * Improve the `WSClient` API by @armanbilge in https://github.com/http4s/http4s/pull/5974

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.9...v0.23.10

# v0.22.11 (2022-02-02)

This is a bugfix release, binary compatible with the 0.22.x series.

* http4s-dsl
    * Fix regression in routing dsl by @hamnis in https://github.com/http4s/http4s/pull/5991

* http4s-server
    * Respect URI locality in UrlFormLifter by @dfahritdinov in https://github.com/http4s/http4s/pull/5994

* http4s-dropwizard-metrics
    * Update metrics-core, metrics-json to 4.2.8 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5993

* Behind the scenes
    * Update scalafmt-core to 3.4.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5969
    * Update http4s-circe, http4s-ember-client to 0.23.9 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5972
    * Update sbt-http4s-org to 0.11.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5968
    * Update sbt to 1.6.2 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5976
    * Improve `WSClient` API by @armanbilge in https://github.com/http4s/http4s/pull/5975

* New Contributors
    * @dfahritdinov made their first contribution in https://github.com/http4s/http4s/pull/5994

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.22.10...v0.22.11

# v0.23.9 (2022-01-29)

This release is binary compatible with the 0.23.x series.  It merges forward the changes of v0.22.10.

The signficant new feature is HTTP/2 support for Ember.  Turn it on with a `.withHttp2` on either the `EmberClientBuilder` or `EmberServerBuilder`.

* http4s-ember-core
    * Ember H2 support by @ChristopherDavenport in https://github.com/http4s/http4s/pull/5657

* Behind the scenes
    * Ignore slf4j-api by @rossabaker in https://github.com/http4s/http4s/pull/5949
    * Remove janky thread pool from BlazeHttp1ClientSuite by @rossabaker in https://github.com/http4s/http4s/pull/5945
    * Clean up Throwables in HTTP/2 by @rossabaker in https://github.com/http4s/http4s/pull/5955
    * Fix HTTP/2 warnings and restore compiler settings by @rossabaker in https://github.com/http4s/http4s/pull/5954
    * Get http4s upgrades for build from 0.22 by @rossabaker in https://github.com/http4s/http4s/pull/5958
    * Merge from 0.22 by @rossabaker in https://github.com/http4s/http4s/pull/5957
    * Update cats-effect, cats-effect-laws, ... to 3.3.5 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5967

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.8...v0.23.9

# v0.22.10 (2022-01-28)

This release is focused on the client.  The blaze-client is substantially refactored internally to improve resource safety.  A new internal websocket client is published for implementation across repos.  We expect to release this as a public API in the next release.

* http4s-core
    * Update slf4j-api to 1.7.35 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5947

* http4s-blaze-client
    * Resourcify blaze client by @RafalSumislawski in https://github.com/http4s/http4s/pull/5385
    * Add withMaxIdleDuration setter to BlazeClientBuilder by @rossabaker in https://github.com/http4s/http4s/pull/5950

* Behind the scenes
    * Update http4s-circe, http4s-ember-client to 0.23.8 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5952
    * Remove mdoc/laika sbt plugins by @armanbilge in https://github.com/http4s/http4s/pull/5963
    * Create WebSocket client API by @armanbilge in https://github.com/http4s/http4s/pull/5520
    * Privatize web socket client pending concrete implementation by @rossabaker in https://github.com/http4s/http4s/pull/5965

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.22.9...v0.22.10

# v0.23.8 (2022-01-25)

This is a maintenance release, binary compatible with the 0.23.x series.  It additionally includes a merge forward of the changes in v0.22.9.

Scala.js users must upgrade to at least 1.8.0 as of this release.

* http4s-core
    * Update sbt-scalajs, scalajs-compiler, ... to 1.8.0 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5716
    * Update fs2-core, fs2-io, ... to 3.2.4 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5788
    * Update cats-effect, cats-effect-laws, ... to 3.3.4 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5838
    * Update locales-minimal-en_us-db to 1.3.0 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5853

* http4s-server
    * cross-platforming server FileService by @yurique in https://github.com/http4s/http4s/pull/5758
    * Fix FileService Config linking on JS by @armanbilge in https://github.com/http4s/http4s/pull/5825
    * Deprecate the `BracketRequestResponse#exitCaseToOutcome` by @danicheg in https://github.com/http4s/http4s/pull/5875

* http4s-ember-core
    * Support parsing response bodies without Content-Length in ember by @wemrysi in https://github.com/http4s/http4s/pull/5881

* http4s-ember-server
    * Update jnr-unixsocket to 0.38.17 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5827

* http4s-ember-client
    * Remove jvm log4cats dependency in ember client js by @kubukoz in https://github.com/http4s/http4s/pull/5757

* http4s-jetty-server
    * Use blocking thread pool on joining Jetty's thread pool by @danicheg in https://github.com/http4s/http4s/pull/5886

* http4s-jetty-client
    * Use blocking thread pool in the `JettyClient#resource` by @danicheg in https://github.com/http4s/http4s/pull/5897

* http4s-jawn
    * Update jawn-fs2 to 2.2.0 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5821

* http4s-prometheus-metrics
    * Use blocking thread pool in the Prometheus metrics by @danicheg in https://github.com/http4s/http4s/pull/5887

* Documentation
    * Remove some outdated params in scaladocs by @danicheg in https://github.com/http4s/http4s/pull/5729
    * Remove mentioning of `ContextShift` in the AHC scaladoc by @danicheg in https://github.com/http4s/http4s/pull/5885

* Behind the scenes
    * Update jnr-unixsocket to 0.38.15 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5681
    * Netty based ServerScaffold (0.23) by @RafalSumislawski in https://github.com/http4s/http4s/pull/5587
    * Merge 0.22 to 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/5701
    * Merge branch `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/5718
    * Merge `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/5730
    * Merge `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/5753
    * Code cleanup (mostly variance-related) by @bplommer in https://github.com/http4s/http4s/pull/5755
    * Update cats-effect, cats-effect-laws, ... to 3.3.1 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5770
    * Merge branch `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/5778
    * Update cats-effect, cats-effect-laws, ... to 3.3.2 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5801
    * Update cats-effect, cats-effect-laws, ... to 3.3.3 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5803
    * Merge series/0.20 to series/0.21 by @rossabaker in https://github.com/http4s/http4s/pull/5818
    * Merge `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/5833
    * Re-enable broken ember client test by @armanbilge in https://github.com/http4s/http4s/pull/5840
    * Enable fatal warnings in `lint`/`quicklint` commands by @danicheg in https://github.com/http4s/http4s/pull/5843
    * FileService Server Middleware: readability by @diesalbla in https://github.com/http4s/http4s/pull/5837
    * Merge from v0.22 by @rossabaker in https://github.com/http4s/http4s/pull/5847
    * Steward exclusions in 0.23 for dependencies merged forward from 0.22 by @bplommer in https://github.com/http4s/http4s/pull/5856
    * Merge series/0.22 into series/0.23 by @bplommer in https://github.com/http4s/http4s/pull/5860
    * Auto-select available port for ember suites by @armanbilge in https://github.com/http4s/http4s/pull/5848
    * Refactor some `Resource` usage by @danicheg in https://github.com/http4s/http4s/pull/5884
    * Merge `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/5888
    * Update log4cats-noop, log4cats-slf4j, ... to 2.2.0 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5891
    * Use `port` syntax by @danicheg in https://github.com/http4s/http4s/pull/5896
    * Add sbt-http4s-org to steward ignores by @armanbilge in https://github.com/http4s/http4s/pull/5924
    * Non-deadlocking RouterInServletSuite by @rossabaker in https://github.com/http4s/http4s/pull/5928
    * Merge scalafmt-core forward from 0.22 by @rossabaker in https://github.com/http4s/http4s/pull/5933
    * Merge 0.22 -> 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/5926
    * Merge from 0.22 by @rossabaker in https://github.com/http4s/http4s/pull/5940
    * Delete zombie specs2 code by @rossabaker in https://github.com/http4s/http4s/pull/5943
    * Replace our TestExecutionContext by @rossabaker in https://github.com/http4s/http4s/pull/5944

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.7...v0.23.8

# v0.22.9 (2022-01-24)

This release is binary compatible with 0.22.x series.  The jawn upgrade mitigates [CVE-2022-21653](https://github.com/typelevel/jawn/security/advisories/GHSA-vc89-hccf-rq55) out of the box for `http4s-play-json`.

* http4s-core
    * Add `Trailer` header by @danicheg in https://github.com/http4s/http4s/pull/5614
    * Model If-Range header by @mcarolan in https://github.com/http4s/http4s/pull/5613
    * Make the output of `asCurl` more human-readable by @danicheg in https://github.com/http4s/http4s/pull/5786
    * Path info index fix by @RafalSumislawski in https://github.com/http4s/http4s/pull/5793
    * Law-driven-design of Path#concat and Path#splitAt by @RafalSumislawski in https://github.com/http4s/http4s/pull/5794
    * Add Request#isIdempotent by @rossabaker in https://github.com/http4s/http4s/pull/5859
    * Update slf4j-api to 1.7.33 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5869
    * Add `EntityEncoder[F, ByteVector]` by @sideeffffect in https://github.com/http4s/http4s/pull/5907

* http4s-laws
    * Update discipline to 1.4.0 by @rossabaker in https://github.com/http4s/http4s/pull/5696

* http4s-client
    * Add `apply`-builder for `Http4sClientDsl` by @armanbilge in https://github.com/http4s/http4s/pull/5742
    * Deprecate client.Connection and client.ConnectionBuilder by @rossabaker in https://github.com/http4s/http4s/pull/5871

* http4s-server
    * HttpMethodOverrider: simplify by @diesalbla in https://github.com/http4s/http4s/pull/5835

* http4s-ember-core
    * Update log4cats-slf4j, log4cats-testing to 1.5.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5880

* http4s-ember-client
    * Disable logging for Ember client internal retry by @RaasAhsan in https://github.com/http4s/http4s/pull/5496

* http4s-blaze-core
    * Update blaze-http to 0.15.3 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5873

* http4s-blaze-client
    * Fix customDnsResolver in BlazeClientBuilder by @rossabaker in https://github.com/http4s/http4s/pull/5864
    * Stale connection mitigation in blaze-client by @rossabaker in https://github.com/http4s/http4s/pull/5861
    * maxIdleDuration on blaze connections by @rossabaker in https://github.com/http4s/http4s/pull/5899

* http4s-tomcat
    * Update tomcat-catalina, tomcat-coyote, ... to 9.0.58 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5915

* http4s-async-http-client
    * Update netty-buffer, netty-codec-http to 4.1.73.Final in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5857

* http4s-jawn
    * Update jawn-parser to 1.3.2 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5815
    * Update jawn-fs2 to 1.2.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5820

* http4s-dropwizard-metrics
    * Update metrics-core, metrics-json to 4.2.7 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5763

* Documentation
    * Fix scaladoc references to `io.chrisdavenport.vault` in the `series/0.22` by @danicheg in https://github.com/http4s/http4s/pull/5765
    * Fix some scaladoc references to headers by @danicheg in https://github.com/http4s/http4s/pull/5766
    * Fix links to the license on the site by @danicheg in https://github.com/http4s/http4s/pull/5829
    * Fix some links in the scaladoc by @danicheg in https://github.com/http4s/http4s/pull/5876

* Behind the scenes
    * Update http4s-circe, http4s-ember-client to 0.23.7 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5693
    * Update tomcat-catalina, tomcat-coyote, ... to 9.0.56 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5687
    * Configure mergify & release notes by @armanbilge in https://github.com/http4s/http4s/pull/5690
    * Update metrics-core, metrics-json to 4.2.5 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5698
    * Netty based ServerScaffold (0.22)  by @RafalSumislawski in https://github.com/http4s/http4s/pull/5601
    * Fix of the #5691 for `series/0.22` by @danicheg in https://github.com/http4s/http4s/pull/5700
    * Update netty-buffer, netty-codec-http to 4.1.71.Final in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5705
    * Update sbt to 1.5.6 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5713
    * Introduce blaze-client connection reuse tests by @RafalSumislawski in https://github.com/http4s/http4s/pull/5319
    * Mark the failing tests as flaky because they sometimes pass by @RafalSumislawski in https://github.com/http4s/http4s/pull/5728
    * Update laika-sbt to 0.18.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5726
    * Speed up shutdown of NioEventLoopGroups by @RafalSumislawski in https://github.com/http4s/http4s/pull/5723
    * Update netty-buffer, netty-codec-http to 4.1.72.Final in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5732
    * Update logback-classic to 1.2.8 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5738
    * Mark one more connection reuse test as flaky by @RafalSumislawski in https://github.com/http4s/http4s/pull/5735
    * `Http1ClientStageSuite` purity by @RafalSumislawski in https://github.com/http4s/http4s/pull/5741
    * Update sbt to 1.5.7 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5747
    * Update logback-classic to 1.2.9 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5751
    * Update sbt to 1.5.8 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5768
    * Collection micro-opts by @danicheg in https://github.com/http4s/http4s/pull/5767
    * Rm `sbt-updates` plugin by @danicheg in https://github.com/http4s/http4s/pull/5777
    * Improve ClientTimeoutSuite by @RafalSumislawski in https://github.com/http4s/http4s/pull/5761
    * Update logback-classic to 1.2.10 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5781
    * Update sbt to 1.6.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5790
    * Update sbt to 1.6.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5796
    * switch from tut to mdoc for 0.18.x by @niij in https://github.com/http4s/http4s/pull/5808
    * Replace deprecated object in the imports by @danicheg in https://github.com/http4s/http4s/pull/5810
    * Merge #5808 to 0.20 by @rossabaker in https://github.com/http4s/http4s/pull/5811
    * Merge series/0.20 to series/0.21 by @rossabaker in https://github.com/http4s/http4s/pull/5818
    * Merge branch 'series/0.21' into series/0.22 by @rossabaker in https://github.com/http4s/http4s/pull/5824
    * Refactor some `Http1Connection` methods by @danicheg in https://github.com/http4s/http4s/pull/5789
    * Update scalafmt-core to 3.2.2 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5784
    * Update scalafmt-core to 3.3.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5834
    * Fix the `lint` command by @danicheg in https://github.com/http4s/http4s/pull/5842
    * Update dev shell by @rossabaker in https://github.com/http4s/http4s/pull/5846
    * Fix description for pinned CE2 dependencies by @bplommer in https://github.com/http4s/http4s/pull/5855
    * Update sbt-scalafix to 0.9.34 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5851
    * Upgrade typelevel-nix to pick up Metals by @rossabaker in https://github.com/http4s/http4s/pull/5863
    * Use loopback address for Netty scaffold by @armanbilge in https://github.com/http4s/http4s/pull/5901
    * Update to sbt-http4s-org 0.10.0 by @armanbilge in https://github.com/http4s/http4s/pull/5900
    * Update scalafmt-core to 3.3.2 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5910
    * Enable snapshots for 0.22 by @armanbilge in https://github.com/http4s/http4s/pull/5908
    * Revert "Enable snapshots for 0.22" (#5908) by @rossabaker in https://github.com/http4s/http4s/pull/5920
    * Add `scalafixInternalRules` to root project by @armanbilge in https://github.com/http4s/http4s/pull/5918
    * Update sbt-http4s-org to 0.10.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5922
    * Update scalafmt-core to 3.3.3 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5931
    * Update sbt-http4s-org to 0.11.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5935
    * remove empty excludes from release notes config by @armanbilge in https://github.com/http4s/http4s/pull/5937
    * Push behind-the-scenes label down the changelog by @rossabaker in https://github.com/http4s/http4s/pull/5939

* New Contributors
    * @niij made their first contribution in https://github.com/http4s/http4s/pull/5808
    * @mcarolan made their first contribution in https://github.com/http4s/http4s/pull/5613

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.22.8...v0.22.9

# v0.23.7 (2021-12-07)

This is a maintenance release, binary compatible with the 0.23.x series.  It additionally includes a merge forward of the changes in v0.22.8.

http4s-server and http4s-ember-server are now cross-built for the Scala.js platform.

Scala 3 users must upgrade to at least Scala 3.1.0 as of this release.

* http4s-core
    * Scala 3.1 and friends by @rossabaker in https://github.com/http4s/http4s/pull/5468
    * Implement `Request#remoteHost` via `ip4s.Dns` by @armanbilge in https://github.com/http4s/http4s/pull/5473
    * (scalajs linking) StaticFile: make staticFileKey a lazy val by @yurique in https://github.com/http4s/http4s/pull/5618
    * Update `cats-effect` version to 3.3.0 by @danicheg in https://github.com/http4s/http4s/pull/5619
    * Update ip4s-core, ip4s-test-kit to 3.1.2 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5631
    * Update scodec-bits to 1.1.30 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5632
    * Update fs2-core, fs2-io, ... to 3.2.3 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5670

* http4s-server
    * Cross most of server (but `Server`) for JS on 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/5563
    * Cross `Server` and ember server to JS in 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/5663

* http4s-ember-server
    * Cross `Server` and ember server to JS in 0.23 by @armanbilge in https://github.com/http4s/http4s/pull/5663

* Documentation
    * Expand docs on client middlewares by @kubukoz in https://github.com/http4s/http4s/pull/5416
    * Port migration of the website to Laika for the `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/5548
    * EPUB download of the docs for the `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/5642
    * Fix scaladoc references to io.chrisdavenport.vault by @MasseGuillaume in https://github.com/http4s/http4s/pull/5622

* Behind the scenes
    * Update http4s-circe, http4s-ember-client to 0.23.6 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5413
    * Non-trivial merge to 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/5431
    * Non-trivial merge to 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/5444
    * Pin scala-library_sjs1 by @rossabaker in https://github.com/http4s/http4s/pull/5448
    * Enable fatal warnings in CI for Scala 3.1 by @armanbilge in https://github.com/http4s/http4s/pull/5474
    * Use Scala 3 cross-compatible `@nowarn` by @armanbilge in https://github.com/http4s/http4s/pull/5518
    * Non-trivial merge to 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/5506
    * Non-trivial merge into 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/5529
    * Add scalafix linter for use of fs2 Sync compiler by @bplommer in https://github.com/
http4s/http4s/pull/5536
    * Non-trivial merge to 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/5540
    * Fix or exclude scalafix warnings by @bplommer in https://github.com/http4s/http4s/pull/5549
    * Non-trivial merge into 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/5557
    * Add sbt check for misplaced sources by @armanbilge in https://github.com/http4s/http4s/pull/5578
    * Use `MonadCancel` in the `Http1Writer.write` by @danicheg in https://github.com/http4s/http4s/pull/5600
    * Non-trivial merge to 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/5602
    * Merge branch `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/5635
    * Update jnr-unixsocket to 0.38.14 in series/0.23 by @scala-steward in https://github.com/http4s/http4s/pull/5639
    * relax Client.translate bound on `G[_]` to MonadCancelThrow by @bpholt in https://github.com/http4s/http4s/pull/5634
    * EPUB download of the docs for the `series/0.22` by @danicheg in https://github.com/http4s/http4s/pull/5652
    * Merge branch `series/0.22` into `series/0.23` by @danicheg in https://github.com/http4s/http4s/pull/5653
    * Merge 0.22 to 0.23 by @rossabaker in https://github.com/http4s/http4s/pull/5669

* New Contributors
    * @yurique made their first contribution in https://github.com/http4s/http4s/pull/5618
    * @MasseGuillaume made their first contribution in https://github.com/http4s/http4s/pull/5622

**Full Changelog**: https://github.com/http4s/http4s/compare/v0.23.6...v0.23.7

# v0.22.8 (2021-12-07)

This is a maintenance release, binary compatible with the 0.22.x series.

* http4s-core
    * Update case-insensitive, ... to 1.2.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5434
    * Fix Origin parsing on hosts starting with a number by @gaspb in https://github.com/http4s/http4s/pull/5504
    * Deprecate `DefaultCharset`, use `UTF-8` directly by @bplommer in https://github.com/http4s/http4s/pull/5512
    * Access control allow methods by @rcardin in https://github.com/http4s/http4s/pull/5376
    * Uri Path Segment Encoder by @zarthross in https://github.com/http4s/http4s/pull/5519
    * Update cats-parse to 0.3.6 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5598
    * Update cats-core, cats-laws to 2.7.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5625
    * Add DefaultQueryParamDecoderMatcher class by @sbly in https://github.com/http4s/http4s/pull/5564

* http4s-laws
    * Update discipline-core to 1.2.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5418

* Various backends
    * Update log4cats-slf4j, log4cats-testing to 1.4.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5638

* http4s-blaze-server
    * Make websocket buffer size configurable by @DeviLab in https://github.com/http4s/http4s/pull/5381

* http4s-async-http-client
    * Update netty-buffer, netty-codec-http to 4.1.70.Final in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5532

* http4s-okhttp-client
    * Update okhttp to 4.9.3 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5605

* http4s-jawn
    * Update jawn-parser to 1.3.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5580

* http4s-scalatags
    * Update scalatags to 0.10.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5441

* http4s-tomcat
    * Update tomcat-catalina, tomcat-coyote, ... to 9.0.55 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5584

* Scalafixes
    * Scalafix for CIString, Header, Headers for 0.22 by @bplommer in https://github.com/http4s/http4s/pull/5387
    * Add scalafix for User-Agent header change by @bplommer in https://github.com/http4s/http4s/pull/5388
    * Fix the package in the AHC scalafix by @rossabaker in https://github.com/http4s/http4s/pull/5643

* Documentation
    * QueryOps +? -> ++? doctest and migration guide by @zmccoy in https://github.com/http4s/http4s/pull/5379
    * Fix `scaladoc` for some `Headers` methods by @danicheg in https://github.com/http4s/http4s/pull/5491
    * Clean up Blaze examples: weaken TC constraints, address linter warnings by @bplommer in https://github.com/http4s/http4s/pull/5524
    * Port migration of the website to Laika for the `series/0.22` by @danicheg in https://github.com/http4s/http4s/pull/5551
    * Fix dead links to RFC by @danicheg in https://github.com/http4s/http4s/pull/5592
    * EPUB download of the docs for the `series/0.22` by @danicheg in https://github.com/http4s/http4s/pull/5652
    * Add favicon to the `0.22` website and docs by @danicheg in https://github.com/http4s/http4s/pull/5676
    * Update nav-docs.html by @rtar in https://github.com/http4s/http4s/pull/5481

* Behind the scenes
    * Update scala3-library to 3.0.2 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5400
    * Update sbt-mdoc to 2.2.24 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5424
    * Reproduce deadlocks in PoolManager by @RafalSumislawski in https://github.com/http4s/http4s/pull/5384
    * fix MatchError by @RafalSumislawski in https://github.com/http4s/http4s/pull/5383
    * Backport to 0.22: Expand docs on client middlewares by @kubukoz in https://github.com/http4s/http4s/pull/5430
    * Ember Server connection tests by @RaasAhsan in https://github.com/http4s/http4s/pull/5382
    * Pin dependencies for 0.22 by @rossabaker in https://github.com/http4s/http4s/pull/5443
    * Update sbt-unidoc to 0.5.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5471
    * Mark some test-suites in the `blaze-client` as flaky by @danicheg in https://github.com/http4s/http4s/pull/5485
    * Thin ci matrix by @armanbilge in https://github.com/http4s/http4s/pull/5476
    * Port #5477 to the series/0.22 by @danicheg in https://github.com/http4s/http4s/pull/5483
    * Remove redundant setting in scalafix build by @bplommer in https://github.com/http4s/http4s/pull/5499
    * Remove redundant val in the `Header.Raw` constructor by @danicheg in https://github.com/http4s/http4s/pull/5500
    * Format sbt files, add command alias for pre-PR linting by @bplommer in https://github.com/http4s/http4s/pull/5498
    * Run Scalafix in CI by @bplommer in https://github.com/http4s/http4s/pull/5505
    * Use predefined names of headers instead of creating them in the `Headers` by @danicheg in https://github.com/http4s/http4s/pull/5492
    * Pin okio to 2.x by @rossabaker in https://github.com/http4s/http4s/pull/5522
    * Add custom scalafix rules by @bplommer in https://github.com/http4s/http4s/pull/5521
    * Update scalafmt by @bplommer in https://github.com/http4s/http4s/pull/5534
    * run scalafmt on series/0.22 by @bplommer in https://github.com/http4s/http4s/pull/5544
    * Send multiple chunks when testing chunked requests by @RafalSumislawski in https://github.com/http4s/http4s/pull/5552
    * Fix or exclude scalafix warnings by @bplommer in https://github.com/http4s/http4s/pull/5549
    * Add further scalafix rules by @bplommer in https://github.com/http4s/http4s/pull/5550
    * Update logback-classic to 1.2.7 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5560
    * Don't ignore the `Part.covary` test suite in the `MultipartSuite` by @danicheg in https://github.com/http4s/http4s/pull/5576
    * Clean up `ServerTestRoutes` by @danicheg in https://github.com/http4s/http4s/pull/5575
    * Use an instance of `Arbitrary[Year]` from ScalaCheck by @danicheg in https://github.com/http4s/http4s/pull/5577
    * Update sbt-native-packager to 1.9.7 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5589
    * Use `assertEquals` instead of `assert` by @danicheg in https://github.com/http4s/http4s/pull/5591
    * Use `Arbitrary[Uri]` in some tests by @danicheg in https://github.com/http4s/http4s/pull/5586
    * Remove redundant isScala3 from build.sbt by @bplommer in https://github.com/http4s/http4s/pull/5595
    * Add advice of using `assertEquals()` in the contributing guide by @danicheg in https://github.com/http4s/http4s/pull/5594
    * Don't ignore `covary` tests in the MessageSuite by @danicheg in https://github.com/http4s/http4s/pull/5596
    * Use `Bracket` in the `Http1Writer.write` by @danicheg in https://github.com/http4s/http4s/pull/5565
    * Update scalafmt-core to 3.1.2 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5606
    * Update sbt-scalafix, scalafix-testkit to 0.9.33 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5616
    * Close some resources in tests by @danicheg in https://github.com/http4s/http4s/pull/5633
    * Update scalafmt-core to 3.2.1 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5650
    * Pin scalatags to 0.10 by @rossabaker in https://github.com/http4s/http4s/pull/5647
    * Update munit-cats-effect-2 to 1.0.7 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5667
    * Update sbt-http4s-org to 0.9.0 in series/0.22 by @scala-steward in https://github.com/http4s/http4s/pull/5659
    * Add the fast path for `Uri.decode` implementation by @plokhotnyuk in https://github.com/http4s/http4s/pull/5556

* New Contributors
    * @DeviLab made their first contribution in https://github.com/http4s/http4s/pull/5381
    * @gaspb made their first contribution in https://github.com/http4s/http4s/pull/5504
    * @sbly made their first contribution in https://github.com/http4s/http4s/pull/5564
    * @plokhotnyuk made their first contribution in https://github.com/http4s/http4s/pull/5556

# v1.0.0-M29 (2021-10-11)

This is the latest development milestone in the 1.x series.  It is not binary compatible with previous milestones.  It includes all the changes through 0.23.6.

* http4s-server
    * Breaking changes
        * [#5346](https://github.com/http4s/http4s/pull/5346): Relax `Async` constraint to `Sync` in `CookieJar`, `ConcurrentRequests`, and `MaxActiveRequests` middleware.
    * Enhancements
        * [#5353](https://github.com/http4s/http4s/pull/5353): Include the `BodyCache` middleware that was introduced in 0.22 but not properly merged to main.

# v0.23.6 (2021-10-12)

This is a routine maintenance release.  It is binary compatible with the v0.22.x series and includes the changes in v0.22.7.

* http4s-core
    * Noteworthy refactorings
        * [#5340](https://github.com/http4s/http4s/pull/5340): Replace our internal `decode` with FS2's `decodeWithCharset`.  This only affects non-UTF-8 encodings.

* http4s-client
    * Breaking changes
        * [#5348](https://github.com/http4s/http4s/pull/5348): Scala.js only: remove `JavaNetClientBuilder`.  It already failed to link, and now it will fail to compile.
    * Bug fixes
        * [#5349](https://github.com/http4s/http4s/pull/5349): Fix deadlocks in `Retry` and `FollowRedirect` middlewares.  We no longer attempt to acquire a second connection before releasing the first, potentially starving the connection pool.

* Dependency updates
    * scalajs-1.7.1

# v0.22.7 (2021-10-12)

This is a routine maintenance release.  It is binary compatible with the v0.22.x series and includes the changes in v0.21.31.

* http4s-core
    * Enhancements
        * [#5165](https://github.com/http4s/http4s/pull/5165): Add `Keep-Alive` header.
    * Compatibility
        * [#5344](https://github.com/http4s/http4s/pull/5344): Reintroduce deprecated aliases at `Header.apply(String, String)`, `Header.of(Header.ToRaw*)`, and `util.CaseInsensitiveString` to ease migration from 0.21.x.

* http4s-server
    * Noteworthy refactoring
        * [#5189](https://github.com/http4s/http4s/pull/5189), [#5368](https://github.com/http4s/http4s/pull/5368): In `GZip`, use `fs2.compress.gzip` for compression.

* http4s-blaze-client
    * Compatibility
        * [#5344](https://github.com/http4s/http4s/pull/5344): Reintroduce deprecated alias for `org.http4s.client.blaze.BlazeClientBuilder` to ease migration from 0.21.x.

* http4s-blaze-server
    * Compatibility
        * [#5344](https://github.com/http4s/http4s/pull/5344): Reintroduce deprecated alias for `org.http4s.server.blaze.BlazeServerBuilder` to ease migration from 0.21.x.

* http4s-ember-core
    * Semantic change
        * [#5341](https://github.com/http4s/http4s/pull/5341): Add `EmberException.ReadTimeout` and `EmberException.RequestHeadersTimeout` (unobservable) to distinguish backend timeouts that close the connection from application `TimeoutExceptions` that can be handled to generate a `503` response or similar.

* Dependency updates

* fs2-2.5.10
* netty-4.1.69.Final
* scalacheck-effect-1.0.3

# v0.21.31 (2021-10-11)

This is a maintenance release.  The only changes are to increase forward source compatibility with 0.22.  It is binary compatible with the 0.21.x series.

* http4s-core
    * Compatibility
        * [#5291](https://github.com/http4s/http4s/pull/5291): Undeprecate `Headers.apply`.  Something similar exists in 0.22.

* http4s-blaze-server
    * Compatibility
        * [#5291](https://github.com/http4s/http4s/pull/5291): Add `org.http4s.blaze.server.BlazeServerBuilder` to `org.http4s.server.blaze.BlazeServerBuilder`.  In 0.22, the alias becomes the canonical name for consistency with the other backends.

* http4s-blaze-client
    * Compatibility
        * [#5291](https://github.com/http4s/http4s/pull/5291): Add `org.http4s.blaze.client.BlazeClientBuilder` to `org.http4s.client.blaze.BlazeClientBuilder`.  In 0.22, the alias becomes the canonical name for consistency with the other backends.

# v1.0.0-M28 (2021-10-06)

This is the latest development milestone in the 1.x series.  It is not binary compatible with previous milestones.

The http4s-dom-core, http4s-dom-fetch-client, and http4s-dom-service-worker modules have been moved to the [http4s-dom repo](https://github.com/http4s/http4s-dom) and are now on their own release cycle.

* Various modules
    * Noteworthy refactoring
        * [#5303](https://github.com/http4s/http4s/pull/5303): Many of the changes on the 1.x line were backported to 0.23.x.  This resynchronized those branches for continuing merges.  Nothing significant should have changed here that isn't already noted in 0.23.5.

* http4s-core
    * Breaking changes
        * [#5328](https://github.com/http4s/http4s/pull/5328): `HttpVersion#copy` is removed from the public API.  It was deprecated in 0.22.6.
        * [#5329](https://github.com/http4s/http4s/pull/5329): Custom status reason phrases are removed.  They were deprecated in 0.22.6.

* http4s-client
    * Breaking changes
        * [#5287](https://github.com/http4s/http4s/pull/5287): Weaken constraint on `DestinationAttribute` to `MonadCancelThrow`. Does not break source compatibility.

* http4s-server
    * Breaking changes
        * [#5327](https://github.com/http4s/http4s/pull/5327): `WebSocketBuilder2` is renamed back to `WebSocketBuilder`.  A deprecated alias is left as `WebSocketBuilder` to aid migration.

# v0.23.5 (2021-10-06)

This is a maintenance release.  It is binary compatible with 0.23.4, and includes the changes in 0.22.6.

Scala.js support is backported for a large subset of the modules present in 1.0.
Additional Scala.js-only modules for using http4s in the browser have been spun off as https://github.com/http4s/http4s-dom.

* http4s-core
    * Cross Builds
        * [#5298](https://github.com/http4s/http4s/pull/5298): Add support for Scala.js
    * Deprecations
        * [#5226](https://github.com/http4s/http4s/pull/5226): Migrate to the `fs2.io.file` APIs in `EntityDecoder`, `StaticFile`, and `Part`.

* http4s-laws
    * Cross Builds
        * [#5298](https://github.com/http4s/http4s/pull/5298): Add support for Scala.js

* http4s-client
    * Cross Builds
        * [#5298](https://github.com/http4s/http4s/pull/5298): Add support for Scala.js

* http4s-server
    * Deprecations
        * [#5226](https://github.com/http4s/http4s/pull/5226): Migrate to the `fs2.io.file` APIs in `FileService`.

* http4s-blaze-server
    * Bug fixes
        * [#5152](https://github.com/http4s/http4s/pull/5152): Pass a `WebSocketBuilder`, now named `WebSocketBuilder2`, when adding an `HttpApp`.  This, combined with the new `imapK` method, lets web socket applications vary the local effect.  Previously, this threw a `ClassCastException`.

* http4s-blaze-client
    * Enhancements
        * [#5201](https://github.com/http4s/http4s/pull/5201): Adds an `BlazeClientBuilder.apply` method that uses the `ExecutionContext` from the `Async[F]` instance.  The old constructor that required an explicit `ExecutionContext` is now deprecated.  Users who need a custom `ExecutionContext` for blaze should call `withExecutionContext`.

* http4s-ember-core
    * Cross Builds
        * [#5298](https://github.com/http4s/http4s/pull/5298): Add support for Scala.js

* http4s-ember-server
    * Bug fixes
        * [#5152](https://github.com/http4s/http4s/pull/5152): Pass a `WebSocketBuilder`, now named `WebSocketBuilder2`, when adding an `HttpApp`.  This, combined with the new `imapK` method, lets web socket applications vary the local effect.  Previously, this threw a `ClassCastException`.
    * Enhancements
        * [#5219](https://github.com/http4s/http4s/pull/5219): Add support for Unix sockets.  This works on Linux and Darwin, but not on Windows. Use the new `withUnixSocketConfig` method on `EmberServerBuilder` to bind to an `fs2.io.net.unixsocket.UnixSocketAddress`.

* http4s-ember-client
    * Cross Builds
        * [#5298](https://github.com/http4s/http4s/pull/5298): Add support for Scala.js
    * Enhancements
        * [#5219](https://github.com/http4s/http4s/pull/5219): Add support for Unix sockets.  This works on Linux and Darwin, but not on Windows.  Use the new `UnixSocket` middleware to route requests to an `fs2.io.net.unixsocket.UnixSocketAddress`.

* Dependency versions
    * fs2-3.1.4
    * ip4s-3.0.4

# v0.22.6 (2021-10-06)

This is a routine maintenance release.  It is binary compatible with v0.22.5 and includes the changes in v0.21.30.

* http4s-core
    * Enhancements
        * [#5189](https://github.com/http4s/http4s/pull/5189): Add `Order[HttpDate]` and `Hash[HttpDate]` instance
        * [#5265](https://github.com/http4s/http4s/pull/5265): Add `Ordering[QValue]` instance
        * [#5279](https://github.com/http4s/http4s/pull/5279): Add constants for `HTTP/3`, `HTTP/2` (deprecating `HTTP/2.0`), and `HTTP/0.9`.
        * [#5294](https://github.com/http4s/http4s/pull/5294): Implement `DNT` header
        * [#5296](https://github.com/http4s/http4s/pull/5296): Add `Accept-Post` header
    * Deprecation
        * [#5260](https://github.com/http4s/http4s/pull/5260): Deprecate `HttpVersion#copy`, which circumvents validation and could create out-of-bounds HTTP protocol versions.
        * [#5253](https://github.com/http4s/http4s/pull/5253): Deprecate custom status reason phrases.  They are a security risk for something that not all backends support and the spec does not require us to support.
        * [#5331](https://github.com/http4s/http4s/pull/5331): Deprecate `Status.apply`, which does not validate the code. Use `fromInt` instead.
    * Notable refactoring
        * [#5139](https://github.com/http4s/http4s/pull/5139): Add dependency on new `http4s-crypto` library, which abstracts the target platform.  All of its uses should be internal.  Scala.js support is added in later branches, but this aids maintenance.
        * [#5308](https://github.com/http4s/http4s/pull/5308): Use `Uri.unsafeFromString` in `Uri` literal macro to ease WartRemover usage.

* http4s-laws
    * Deprecation
        * [#5274](https://github.com/http4s/http4s/pull/5274): Deprecate `ArbitraryInstances`, which was redundant with the `arbitrary` object.  The latter is packaged consistently with Cats' arbitraries.

* http4s-server
    * Enhancements
        * [#5323](https://github.com/http4s/http4s/pull/5323): In `CORSPolicy`, add `withAllowHeadersStatic`, which supports a static list of `Access-Control-Allow-Headers` whether the `Access-Control-Request-Headers` values match or not.

* http4s-blaze-client
    * Semantic change
        * [#5032](https://github.com/http4s/http4s/pull/5032): Wrap `EOF` when borrowing a dead connection in a `java.net.SocketException` with information on which host failed.

* http4s-ember-client
    * Enhancements
        * [#5271](https://github.com/http4s/http4s/pull/5271): Eliminate exception allocation on the parser hot path
        * [#5290](https://github.com/http4s/http4s/pull/5290): Retry on `IOException` with `"Connection reset by peer"` or `"Broken pipe"` in the message

* http4s-ember-server
    * Semantic change
        * [#5286](https://github.com/http4s/http4s/pull/5286): On `requestHeaderTimeout` and `idleTimeout`, close the connection without rendering a `500 Internal Server Error` response.  The HTTP/1.1 spec is not prescrptive on this matter, but this behavior is more consistent with prevaling usage in http4s and a sampling of other servers.  Furthermore, an empty response is retriable (assuming request idempotence) by clients, whereas a `500 Internal Server Error` is not.

* Enhancements
    * [#5271](https://github.com/http4s/http4s/pull/5271): Eliminate exception allocation on the parser hot path

* Dependency updates
    * ip4s-2.0.4
    * jetty-9.4.44.v20210927
    * metrics-4.2.4
    * munit-cats-effect-1.0.6
    * okhttp-4.9.2
    * scodec-bits-1.2.29
    * tomcat-9.0.54

# v1.0.0-M27 (2021-09-21)

This release includes security patches for [GHSA-5vcm-3xc3-w7x3](https://github.com/http4s/http4s/security/advisories/GHSA-5vcm-3xc3-w7x3) for blaze-client, blaze-server, ember-client, ember-server, and jetty-client.  It forward-merges 0.23.4.

* http4s-dom-fetch-client
    * Enhancements
        * [#5101](https://github.com/http4s/http4s/pull/5101): Add a request timeout and new `FetchOptions` to configure the fetch client.

* http4s-dom-service-worker
    * Breaking changes
        * [#5089](https://github.com/http4s/http4s/pull/5089): Simplify service worker API and make safe(r).  The `ServerWorkerApp` is now replaced by a method that suspends into `SyncIO`.

# v0.23.4 (2021-09-21)

This release includes security patches for [GHSA-5vcm-3xc3-w7x3](https://github.com/http4s/http4s/security/advisories/GHSA-5vcm-3xc3-w7x3) for blaze-client, blaze-server, ember-client, ember-server, and jetty-client.  It is binary compatible with v0.22.4, and forward-merges 0.22.5.

* http4s-client
    * Enhancements
        * [#5190](https://github.com/http4s/http4s/pull/5190): Add an `effect` constructor for calculating effectual classifiers. Note that it is a mistake to consume the request body unless it is cached external to this call.

* Dependency updates
    * cats-effect-3.2.8
    * fs2-3.1.2
    * keypool-0.4.7

# v0.22.5 (2021-09-21)

This release includes security patches for [GHSA-5vcm-3xc3-w7x3](https://github.com/http4s/http4s/security/advisories/GHSA-5vcm-3xc3-w7x3) for blaze-client, blaze-server, ember-client, ember-server, and jetty-client.  It is binary compatible with v0.22.4, and forward-merges 0.21.29.

* http4s-core
    * Bug fixes
        * [#5166](https://github.com/http4s/http4s/pull/5166): Fix deduction of cipher lengths, and detect length of more ciphers
        * [#5196](https://github.com/http4s/http4s/pull/5196): Parse `Set-Cookie` headers with no space between the semi-colon delimeter and the next attribute.  Such cookies are invalid to emit per spec, but must be parsed per spec.
    * Enhancements
        * [#5168](https://github.com/http4s/http4s/pull/5168): Add `Ordering` instance for Oauth1 `ProtocolParameter`.
        * [#5176](https://github.com/http4s/http4s/pull/5176): Add model for the `X-Forwarded-Proto` header.
        * [#5195](https://github.com/http4s/http4s/pull/5195): Restore the `EntityDecoder[F, ByteVector]`.
        * [#5171](https://github.com/http4s/http4s/pull/5171): Add model for the `Access-Control-Max-Age` header.
        * [#5202](https://github.com/http4s/http4s/pull/5202): Use concrete types for overridden `Request` and `Response` methods. This should be transparent.
        * [#5175](https://github.com/http4s/http4s/pull/5175): Introduce a `HeaderCompanion` helper to reduce boilerplate when defining modeled headers.

* http4s-blaze-client
    * [#5158](https://github.com/http4s/http4s/pull/5158): Add a `resourceWithState` method to the `BlazeClientBuilder` for monitoring the connection pool.

* http4s-ember-core
    * Enhancements
        * [#5216](https://github.com/http4s/http4s/pull/5216): Improve performance of requet and response parsers

* http4s-ember-server
    * Bug fixes
        * [#5130](https://github.com/http4s/http4s/pull/5130): Populate `SecureSession` request attribute in ember-server.

* Dependency updates
    * cats-effect-2.5.4
    * netty-4.1.68
    * scalafix-0.9.31
    * tomcat-9.0.53

# v0.21.30 (2021-10-06)

This is a bugfix release. Routine maintenance has stopped on 0.21.x, but we'll continue to entertain PRs from the community.  It is binary compatible wit hthe 0.21.x series.

* blaze-client

    * Compatibility restorations

* [#5288](https://github.com/http4s/http4s/pull/5288): Allow `' '` when rendering URI. This is against the spec, but bug-compatible with previous versions and not a security threat. It has come up for users trimming strings from config. Starting in 0.22, such whitespace is encoded properly.

* ember-client

    * Bugfixes

* [#5247](https://github.com/http4s/http4s/pull/5247): Match on `ClosedChannelException` when detecting connections that terminated inside the pool.

    * Compatibility restorations

* [#5288](https://github.com/http4s/http4s/pull/5288): Allow `' '` when rendering URI. This is against the spec, but bug-compatible with previous versions and not a security threat. It has come up for users trimming strings from config. Starting in 0.22, such whitespace is encoded properly.

# v0.21.29 (2021-09-21)

This release includes security patches for blaze-client, blaze-server, ember-client, ember-server, and jetty-client.  It is binary compatible with the 0.21.x series.

* Various modules
    * [GHSA-5vcm-3xc3-w7x3](https://github.com/http4s/http4s/security/advisories/GHSA-5vcm-3xc3-w7x3): Patches a vulnerability when unencoded user inputs are rendered in the model.  Malicious characters in these inputs can be used in [splitting attacks](https://owasp.org/www-community/attacks/HTTP_Response_Splitting).
        * Header values.  `\r`, `\n`, and `\u0000` values are now replaced with spaces.
        * Header names.  Headers with invalid names are now dropped.
        * Status reason phrases.  Invalid phrases are now omitted.
        * URI authority registered names.  Requests with invalid reg-names now raise an exception.
        * URI paths.  Requests with invalid URI paths now raise an exception.

# v1.0.0-M26 (2021-09-02)

This release is a forward port of all the changes in v0.23.3.

# v0.23.3 (2021-09-02)

This is binary compatible with v0.23.3.  It includes the fixes in v0.22.2.

* http4s-ember-server
    * Bugfixes
        * [#5138](https://github.com/http4s/http4s/pull/5138): Correctly populate the `SecureSession` response attribute.

# v0.22.4 (2021-09-02)

This is binary compatibile with v0.22.3.  It includes the CORS bugfix in v0.21.28.

* http4s-server
    * Bugfixes
        * [#5130](https://github.com/http4s/http4s/pull/5130): Fix the parsing of empty `Origin` headers to be a parse failure instead of `Origin.Null`.

* Enhancements
    * [#5321](https://github.com/http4s/http4s/pull/5321): Add `BodyCaching` middleware.

* Dependency updates
    * scodec-bits-1.1.28

# v0.21.28 (2021-09-02)

This is a bugfix to yesterday's patch.  It is not a security issue, but a correctness issue.

This release is binary compatible with 0.21.x.

* http4s-server
    * Breaking changes
        * [#5144](https://github.com/http4s/http4s/pull/5144): In the `CORS` middleware, respond to preflight `OPTIONS` requests with a 200 status.  It was previously passing through to the wrapped `Http`, most of which won't respond to `OPTIONS`.  The breaking change is that the constraint is promoted from `Functor` to `Applicative`.  The `Functor` version is left for binary compatibility with a runtime warning.

# v1.0.0-M25 (2021-09-01)

This is the latest development release.  No binary compatibility is promised yet.  Includes all changes in v0.23.2.

* http4s-core
    * Breaking changes
        * [#5051](https://github.com/http4s/http4s/pull/5051): Per spec, `Access-Control-Allow-Headers` and `Access-Control-Expose-Headers` can be empty.
        * [#5082](https://github.com/http4s/http4s/pull/5082): Remodel `Origin` header. `Origin.Null` is changed to `Origin.null`.  The obsolete `Origin.HostList` is gone in favor of `Origin.Host` being an `Origin`.  Fixes parsing of an empty header to be an error instead of returning `null`.

* http4s-dom-core
    * Enhancements
        * [#5049](https://github.com/http4s/http4s/pull/5049): Implement `EntityEncoder` for `File` and `ReadableStream[Unit8Array]`.
        * [#5094](https://github.com/http4s/http4s/pull/5094), [#5103](https://github.com/http4s/http4s/pull/5103): Fix readable stream cancellation bug in Firefox

* Dependency updates
    * simpleclient-0.12.0 (Prometheus)
    * scalajs-dom-1.2.0

# v0.23.2 (2021-09-01)

This release includes a security patch to  [GHSA-52cf-226f-rhr6](https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6), along with all changes in v0.22.3.

This release is binary compatible with the 0.23 series.

* http4s-core
    * Enhancements
        * [#5085](https://github.com/http4s/http4s/pull/5085): Make `EntityEncoder`s for `File`, `Path`, and `InputStream` implicit.  Since 0.23, they no longer require an explicit `Blocker` parameter, using Cats-Effect 3's runtime instead.

* http4s-blaze-server
    * Bug fixes
        * [#5118](https://github.com/http4s/http4s/pull/5118): Don't block the `TickWheelExecutor` on cancellation.  In-flight responses are canceled when a connection shuts down.  If the response cancellation hangs, it blocks the `TickWheelScheduler` thread.  When this thread blocks, subsequent scheduled events are not processed, and memory leaks with each newly scheduled event.
    * Enhancements
        * [#4782](https://github.com/http4s/http4s/pull/4782): Use `Async[F].executionContext` as a default `ExecutionContext` in `BlazeServerBuilder`.

* http4s-ember-server
    * [#5106](https://github.com/http4s/http4s/pull/5106): Demote noisy `WebSocket connection terminated with exception` message to trace-level logging on broken pipes.  This relies on exception message parsing and may not work well in all locales.

* Dependency updates
    * cats-effect-3.2.5
    * fs2-3.1.1

# v0.22.3 (2021-09-01)

This release includes a security patch to  [GHSA-52cf-226f-rhr6](https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6), along with all changes in 0.21.26 and 0.21.27.

Binary compatible with 0.22.2 series, with the exception of static forwarders in `HttpApp.apply`, `HttpApp.local`.  Unless you are calling `HttpApp` from a language other than Scala, you are not affected.

* http4s-core
    * Binary breaking changes
        * [#5071](https://github.com/http4s/http4s/pull/5071): Weakens constraints on `HttpApp.apply` and `HttpApp.local` from `Sync` to `Defer`.  This change is technically binary breaking, but will only affect static methods called via interop from a language other than Scala.
    * Semantic changes
        * [#5073](https://github.com/http4s/http4s/pull/5073): `withEntity` now replaces any existing headers with the same name with the headers from the `EntityEncoder`.  In v0.21, known single headers were replaced and recurring headers were appended.  Beginning in 0.22.0, everything was appended, which commonly resulted in duplicate `Content-Type` headers.  There is no longer a global registry of headers to infer singleton vs. recurring semantics, but in practice, `EntityEncoder` headers are single, so this is more correct and more similar to the pre-0.22 behavior.
    * Bugfixes
        * [#5070](https://github.com/http4s/http4s/pull/5070): Fix `Accept-Language` parser on the wildcard (`*`) tag with a quality value
        * [#5105](https://github.com/http4s/http4s/pull/5105): Parse `UTF-8` charset tag on `Content-Disposition` filenames case-insensitively. This was a regression from 0.21.
    * Enhancements
        * [#5042](https://github.com/http4s/http4s/pull/5042): Add a modeled header for `Access-Control-Request-Method`.
        * [#5076](https://github.com/http4s/http4s/pull/5076): Create `Uri.Host` from an ip4s `IpAddress`
    * Documentation
        * [#5061](https://github.com/http4s/http4s/pull/5061): Document that the `Allow` header MUST return the allowed methods.
    * Dependency updates
        * blaze-0.15.2
* http4s-client
    * Enhancements
        * [#5023](https://github.com/http4s/http4s/pull/5023): Parameterize the signature algorithm in the OAuth 1 middleware.  HMAC-SHA256 and HMAC-SHA512 are now supported in addition to HMAC-SHA1.

* http4s-server
    * Bugfixes
        * [#5056](https://github.com/http4s/http4s/pull/5056): In `GZip` middleware, don't add a `Content-Encoding` header if the response type doesn't support an entity.
    * Enhancements
        * [#5112](https://github.com/http4s/http4s/pull/5112): Make `CORS` middleware configurable via `toHttpRoutes` and `toHttpApp` constructors.

* http4s-blaze-core
    * Bugfixes
        * [#5126](https://github.com/http4s/http4s/pull/5126): Upgrades to a Blaze version that uses a monotonic timer in the `TickWheelExecutor`.  This will improve scheduling correctness in the presence of an erratic clock.

* http4s-blaze-server
    * Bugfixes
        * [#5075](https://github.com/http4s/http4s/pull/5075): Render the blaze version correctly in the default startup banner

* http4s-ember-core
    * Bugfixes
        * [#5043](https://github.com/http4s/http4s/pull/5043): Fix several bugs where a body stream silenty ends if the peer closes its end of the socket without finishing writing. This now raises an error.

* http4s-ember-client
    * Bugfixes
        * [#5041](https://github.com/http4s/http4s/pull/5041): Don't keep alive HTTP/1.0 connections without a `Connection: keep-alive` header.

* http4s-ember-server
    * Deprecations
        * [#5040](https://github.com/http4s/http4s/pull/5040): `maxConcurrency` is renamed to `maxConnections`.  The former is now deprecated.

* http4s-dsl
    * Enhancements
        * [#5063](https://github.com/http4s/http4s/pull/5063): Added `->>` infix extractor for a resource-oriented view of routing. Use this to define resource paths only once, and generate proper `405` responses with a correct `Allow` header when the method is not handled.

* Dependency updates
    * blaze-0.15.2
    * netty-4.1.67

# v0.21.27 (2021-08-31)

This is a security release.  It is binary compatible with the 0.21.x series.

* http4s-server
    * Security patches [GHSA-52cf-226f-rhr6](https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6):
        * Deprecates `apply` method that takes a `CORSConfig`, and `httpRoutes` anad `httpApp` that take no config.  The default configuration disables most actual CORS protection, and has several deficiences even when properly configured.  See the GHSA for a full discussion.  tl;dr: start from `CORS.policy`.
        * The deprecated implementation now ignores the `allowCredentials` setting when `anyOrigin` is true, and logs a warning.  If you insist on using the deprecated version, old behavior can be restored by setting `anyOrigin` to false and `allowOrigins` to `Function.const(true)`.
        * No longer renders an `Access-Control-Allow-Credentials: false` headerFor safety, the `allowCredentials` setting is now Please see the GHSA for a full discussion.
        * The replacement implementation, created from the new `CORS.policy`, additionally fixes the following defects:
        * No longer returns a `403 Forbidden` response when CORS checks fail.  The enforcement point of CORS is the user agent.  Any failing checks just suppress CORS headers in the http4s response.
        * Add  `Access-Control-Request-Headers` to the `Vary` header on preflight responses when it can affect the response. This is important for caching.
        * Validate the  `Access-Control-Request-Headers`, and return no CORS headers if any of the headers are disallowed.
        * Remote `Vary: Access-Control-Request-Method` and `Access-Control-Max-Age` headers from non-preflight responses.  These are only relevant in preflight checks.

* http4s-blaze-server
    * Bugfixes
        * [#5125](https://github.com/http4s/http4s/pull/5125): Upgrade to a blaze that uses monotonic time in the `TickWheelExecutor`. This is unrelated to the GHSA, but guards against a theoretical scheduling problem if the system clock is erratic.

* Dependency updates
    * blaze-0.14.18

# v0.21.26 (2021-08-12)

The 0.21 series is no longer actively maintained by the team, but we'll continue to entertain binary compatible patches.  All users are still encouraged to upgrade to 0.22 (for Cats-Effect 2) or 0.23 (the latest stable series, on Cats-Effect 3).

# v1.0.0-M24 (2020-08-07)

This release adds support for Scala.js, including an Ember client and server, serverless apps, a browser client backed by fetch, and browser service worker apps.

This is the first significant divergence from the 0.23 line since it was forked off an earlier 1.0 milestone.  It is not binary compatible with 0.23.x or 1.0.0-M23.

Includes all changes through 0.23.1.

* http4s-core
    * Subtle changes
        * [#4938](https://github.com/http4s/http4s/pull/4938): JsonDebugErrorHandler now logs the class name instead of the canonical class name, which is not supported in Scala.js.  The difference is some dots vs. dollar signs.  This is neither source nor binary breaking.
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* http4s-laws
    * Breaking changes
        * [#4938](https://github.com/http4s/http4s/pull/4938): Binary compatibility is broken by replacing ip4s-testkit instances with a copy that suits our Scala.js needs.
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* http4s-server
    * Breaking changes
        * [#4938](https://github.com/http4s/http4s/pull/4938): `DigestAuth` and `CSRF` middleware now require an `Async` constraint
        * [#4938](https://github.com/http4s/http4s/pull/4938): `Server.address` is now an `com.comast.ip4s.SocketAddress` instead of a `java.net.InetSocketAddress`.
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* http4s-client
    * Breaking changes
        * [#4938](https://github.com/http4s/http4s/pull/4938): `oauth1.signRequest` now requires an `Async` constraint
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* http4s-ember-core
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* http4s-ember-client
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* http4s-ember-server
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* http4s-node-serverless
    * New module
        * [#4938](https://github.com/http4s/http4s/pull/4938): Run `HttpApp` in a serverless environment

* http4s-dom-core
    * New module
        * [#4938](https://github.com/http4s/http4s/pull/4938): Base library for DOM support.  Scala 2 only for now.

* http4s-dom-fetch-client
    * New module
        * [#4938](https://github.com/http4s/http4s/pull/4938): http4s-client backend built on browser fetch API.  Scala 2 only for now.

* http4s-dom-service-worker
        * [#4938](https://github.com/http4s/http4s/pull/4938): Run `HttpApp` in a service worker in the browser.  Scala 2 only for now.

* http4s-dsl
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* http4s-boopickle
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* http4s-jawn
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* http4s-circe
    * Enhancements
        * [#4938](https://github.com/http4s/http4s/pull/4938): Add Scala.js support

* Dependency updates
    * circe-0.15.0-M1
    * scala-java-locales-1.2.1 (new)
    * scala-java-time-2.3.0 (new)
    * scala-js-dom-1.1.0 (new)

* [#5064](https://github.com/http4s/http4s/pull/5064): Add a conservative retry policy for dead connections.  Connections can be terminated on the server side while idling in our pool, which does not manifest until we attempt to read the response.  This is now raised as a `java.nio.channels.ClosedChannelException`.   A `retryPolicy` configuration has been added to the `EmberClientBuilder`.  The default policy handles the error and resubmits the request if:
    * The request method is idempotent OR has an `Idempotency-Key` header
    * Less than 2 attempts have been made
    * Ember detects that the connection was closed without reading any bytes

# v0.23.1 (2021-08-06)

Includes all changes through v0.22.2.

* Dependency updates
    * cats-effect-3.2.2
    * fs2-3.1.0
    * vault-3.0.4

# v0.22.2 (2021-08-06)

* http4s-core
    * Enhancements
        * [#5011](https://github.com/http4s/http4s/pull/5011): Add constant  for status code `418 I'm a teapot`. #save418 🫖
        * [#5013](https://github.com/http4s/http4s/pull/5013): Create `RequestPrelude` and `ResponsePrelude` views of `Request` and `Response`, respectively.  These projections omit the body and vault attributes, which permit an `Order` and `Hash` (and therefore `Eq`) instance that `Request` and `Response` do not.  These can be useful in logging, metrics, and caching.
    * Deprecations
        * [#5015](https://github.com/http4s/http4s/pull/5015): Deprecate the old `Uri.uri`, `MediaType.mediaType`, and `QValue.q` literals.  Intepolators for each are available via `org.http4s.implicits._`

* Dependency updates
    * cats-effect-2.5.3
    * tomcat-9.0.52

# v0.23.0 (2021-07-30)

This is the first production release with Cats-Effect 3 support.  All subsequent 0.23.x releases will be binary compatible with this.

Includes all changes through v0.22.1.

* http4s-core
    * Breaking changes
        * [#4997](https://github.com/http4s/http4s/pull/4997): Refresh MimeDB from the IANA registry.  It shuffles some constants in ways that offend MiMa, but you almost certainly won't notice.
    * Enhancements
        * [#4915](https://github.com/http4s/http4s/pull/4915): Add file-based multipart decoder with better resource handling.  This deprecates the priod `mixedMultipart` decoder in favor of a `mixedMultipartResource`, which cleans up temporary storage on release of the resource.  Please see the scaladoc for a usage example.

* Various modules
    * Breaking changes
        * [#4998](https://github.com/http4s/http4s/pull/4998): Removes everything deprecated since 0.20.0, over 24 months and three breaking releases ago.  See the pull request for a comprehensive list.
    * Refactoring
        * [#4986](https://github.com/http4s/http4s/pull/4986): Light refactoring of fs2 pipes in Ember and Blaze backends.  Should not be visible.

* Dependency updates
    * cats-effect-3.2.0
    * fs2-3.0.6
    * jawn-fs2-2.1.0
    * keypool-0.4.6

# v0.22.1 (2021-07-30)

* http4s-core
    * Bugfixes
        * [#4956](https://github.com/http4s/http4s/pull/4956): Catch non-fatal exceptions, notably `DateTimeException`, in `QueryParamDecoder`s.
    * Enhancements
        * [#4956](https://github.com/http4s/http4s/pull/4956): Add `QueryParamCodec`s for more `java.time` types.

* Documentation
        * [#5012](https://github.com/http4s/http4s/pull/5012): Document `MatrixVar` support;

* http4s-client
    * Bugfixes
        * [#4933](https://github.com/http4s/http4s/pull/4933): Append the `EntityDecoder`'s `Accept` headers to any explicit headers instead of replacing them.  This was a regression from the 0.21 line.

* http4s-boopickle
    * Cross builds
        * [#4991](https://github.com/http4s/http4s/pull/4991): `http4s-boopickle` is now cross-published for Scala 3

* Dependency updates
    * boopickle-1.4.0
    * cats-effect-2.5.2
    * dropwizard-metrics-4.2.3
    * scala-xml-2.0.1
    * slf4j-api-1.7.32

# v0.22.0

This is the first production release with Scala 3 support, and continues to support Cats-Effect 2.  All users of the 0.21 series are encouraged to upgrade to at least this version.  Users needing Cats-Effect 3 are invited to upgrade to http4s-0.23.

All subsequent 0.22.x releases will be binary compatible with this.

Includes all changes from v0.21.25.

* http4s-core
    * Bugfixes
        * [#4933](https://github.com/http4s/http4s/pull/4933): Don't eagerly parse non-matching headers
    * Breaking changes
        * [#4895](https://github.com/http4s/http4s/pull/4895): Refresh MimeDb.  This is pedantically incompatible, but we don't think you'll notice.

* http4s-dsl
    * Bugfixes
        * [#4923](https://github.com/http4s/http4s/pull/4923): Define `as` as an infix operator in Scala 3

* http4s-blaze-client
    * Documentation
        * [#4930](https://github.com/http4s/http4s/pull/4930): Add scaladoc to `BlazeClientBuilder`

* http4s-ember-server
    * Enhancements
        * [#4803](https://github.com/http4s/http4s/pull/4803): Add web socket support

* http4s-jetty-server
    * Bugfixes
        * [#4967](https://github.com/http4s/http4s/pull/4967): Fix error parsing IPv6 addresses

* Dependency updates
    * jawn-1.2.0
    * prometheus-client-0.11.0

# v0.21.25 (2021-07-18)

* http4s-blaze-client
    * Bugfixes
        * [#4831](https://github.com/http4s/http4s/pull/4831): Fix blaze-client handling of early responses
        * [#4958](https://github.com/http4s/http4s/pull/4958): Reuse idle timeout stage.  This also addresses a performance regression identified in v0.21.23.
    * Enhancements
        * [#4906](https://github.com/http4s/http4s/pull/4906): Recycle more connections than before

* Dependency updates
    * dropwizard-metrics-4.2.2
    * fs2-2.5.9
    * jetty-9.4.43
    * log4s-1.10.0
    * netty-4.1.66
    * slf4j-1.7.31
    * tomcat-9.0.50

# v1.0.0-M23 (2021-05-26)

Functionally equivalent to v0.23.0-RC1. Keeps the 1.0 milestones current as we continue our roadmap. Includes the [vulnerability fix](https://github.com/http4s/http4s-ghsa-6h7w-fc84-x7p6) to `StaticFile.fromUrl`.

# v0.23.0-RC1 (2021-05-26)

Includes the changes of v0.22.0-RC1, including the [vulnerability fix](https://github.com/http4s/http4s-ghsa-6h7w-fc84-x7p6) to `StaticFile.fromUrl`.

* http4s-core
    * Breaking changes
        * [#4884](https://github.com/http4s/http4s/pull/4884): Use `Monad` instead of `Defer` constraints on `HttpApp`, `HttpRoutes`, `AuthedRoutes`, `ContextRoutes`, and related syntax. This avoids diverging implicits when only a `Concurrent` constraint is available in Cats-Effect-3.
    * Noteworthy refactoring
        * [#4773](https://github.com/http4s/http4s/pull/4787): Refactor the internals of the `Multipart` parser.

* http4s-ember-client
    * Noteworthy refactoring
        * [#4882](https://github.com/http4s/http4s/pull/4882): Use `Network` instead of `Network.forAsync` to get the socket group.

* http4s-ember-server
    * Noteworthy refactoring
        * [#4882](https://github.com/http4s/http4s/pull/4882): Use `Network` instead of `Network.forAsync` to get the socket group.

# v0.22.0-RC1 (2021-05-26)

Includes the changes of 0.21.24, including the [vulnerability fix](https://github.com/http4s/http4s-ghsa-6h7w-fc84-x7p6) to `StaticFile.fromUrl`.

* http4s-core
    * Breaking changes
        * [#4787](https://github.com/http4s/http4s/pull/4787): Various header selection refinements:
        * `Header.Select#toRaw` now takes an `F[A]` and returns a `NonEmptyList[Header.Raw]`. This is necessary because headers without a `Semigroup` (e.g., `Set-Cookie`) can't be combined into a single header value.
        * The old `Header.Select#toRaw` is renamed to `toRaw1`.  This version still accepts a single value and returns a single raw header.
        * `Header.Select#from` now returns an `Option[Ior[NonEmptyList[ParseFailure], NonEmptyList[A]]]`. The `Ior` lets us return both a value and "warnings" when a repeating header contains both valid and invalid entries.
        * Add `Headers#getWithWarnings` to return the `Ior` result.
        * [#4788](https://github.com/http4s/http4s/pull/4788): Extend `ServerSentEvent` with comments.  The `data` field is now optional. `retry` is changed from a `Long` to a `FiniteDuration`.  `data` spanning multiple lines are now rendered as multiple `data:` fields per the spec.
    * Bugfixes
        * [#4873](https://github.com/http4s/http4s/pull/4873): Catch exceptions in `ParseResult.fromParser`. Don't throw when parsing a media range in the `Content-Type` parser.

* Dependency updates
    * blaze-0.15.1
    * circe-0.14.1
    * play-json-2.9.2 (downgrade)

# v0.21.24 (2021-05-26)

0.21 is EOL.  Bugfixes and community submissions will be considered for discretionary releases, but the development team will now focus on later branches.

Contains a vulnerability fix for `StaticFile.fromUrl`.

* http4s-blaze-core
    * Vulnerability fixes
        * [GHSA-6h7w-fc84-x7p6](https://github.com/http4s/http4s/security/advisories/GHSA-6h7w-fc84-x7p6): Don't leak the existence of a directory serverside when using `StaticFile.fromUrl` with non-file URLs.
    * Enhancements
        * [#4880](https://github.com/http4s/http4s/pull/4880): Handle exceptions when the tick wheel executor is shutdown as a warning instead of a stack trace error.

* http4s-ember-client
    * Enhancements
        * [#4881](https://github.com/http4s/http4s/pull/4881): Add `checkEndpointIdentification` flag to Ember. When true, sets `HTTPS` as the endpoint validation algorithm. Defaults to true.

* Dependency Updates
    * blaze-0.14.17

# v1.0.0-M22 (2021-05-21)

Functionally equivalent to v0.23.0-M1.  Keeps the 1.0 milestones current as we continue our roadmap.

# v0.23.0-M1 (2021-05-21)

We are opening an 0.23 series to offer full support for Scala 3 and Cats-Effect 3 while giving ourselves a bit more time to finish our more ambitious goals for 1.0.  We will release v0.23.0 with production support as soon as circe-0.14 is out.

This release picks up from v1.0.0-M21 with its Cats-Effect 3 support, and includes all improvements from v0.22.0-M8.

* Documentation
    * [#4845](https://github.com/http4s/http4s/pull/4845): Mention `Client.fromHttpApp`

* Dependency updates
    * cats-effect-3.1.0
    * fs2-3.0.4
    * ip4s-3.0.2
    * jawn-fs2-2.0.2
    * keypool-0.4.5
    * log4cats-2.1.1
    * scalacheck-effect-1.0.2
    * vault-3.0.3

# v0.22.0-M8 (2021-05-21)

Includes the changes of v0.21.23.  This is the first release with support for Scala 3.0.0.  We will release v0.22.0 with production support as circe-0.14 is out.

There are several package renames in the backends.  To help, we've provided a Scalafix:

1. Add to your `projects/plugins.sbt`:

   ```scala
   addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.28")
   ```

2. Run the following:

   ```sh
   sbt ";scalafixEnable; scalafix github:http4s/http4s/v0_22"
   ```

* Crossbuilds
    * Adds Scala 3
    * Drops Scala-3.0.0-RC2

* http4s-async-http-client
    * Breaking changes
        * [#4854](https://github.com/http4s/http4s/pull/485)4: Rename package from `org.http4s.client.asynchttpclient` to `org.http4s.asynchttpclient`

* http4s-client
    * Breaking changes
        * [#4747](https://github.com/http4s/http4s/pull/4747): Move `ConnectionManager`, `PoolManager`, and `WaitQueueTimeoutException` into blaze-client and make private. It did not prove to be a generally useful connection pool outside blaze.

* http4s-core
    * Breaking changes
        * [#4757](https://github.com/http4s/http4s/pull/4757): Response is no longer a case class. It is not a proper product type, and no equality should be implied given the streaming bodies.
    * Bug fixes
        * [#4739](https://github.com/http4s/http4s/pull/4739): Postpone using query model until we need it.  Helps with various corner cases linked in the ticket.
        * [#4756](https://github.com/http4s/http4s/pull/4756): Tweak default `responseColor` of `Logger.colored` so it can be called.
        * [#4824](https://github.com/http4s/http4s/pull/4824): Fix `Message#removeCookie` to allow removing multiple cookies.
    * Enhancements
        * [#4797](https://github.com/http4s/http4s/pull/4797): Add `Header.ToRaw[Headers]` instance

* http4s-blaze-client
    * Breaking changes
        * [#4838](https://github.com/http4s/http4s/pull/4838): Rename package from `org.http4s.client.blaze` to `org.http4s.blaze.client`

* http4s-blaze-server
    * Breaking changes
        * [#4847](https://github.com/http4s/http4s/pull/4847): Rename package from `org.http4s.server.blaze` to `org.http4s.blaze.server`

* http4s-jetty-client
    * Breaking changes
        * [#4743](https://github.com/http4s/http4s/pull/4743): Rename package from `org.http4s.client.jetty` to `org.http4s.jetty.client`

* http4s-jetty-server
    * Breaking changes
        * [#4743](https://github.com/http4s/http4s/pull/4743): Rename package from `org.http4s.server.jetty` to `org.http4s.jetty.server`
        * [#4746](https://github.com/http4s/http4s/pull/4746): Module renamed from `http4s-jetty` to `http4s-jetty-server`.

* http4s-server
    * Breaking changes
        * [#4785](https://github.com/http4s/http4s/pull/4785): Remove unsued `Functor[G]` parameter to `AutoSlash` middleware
        * [#4827](https://github.com/http4s/http4s/pull/4827): Convert `CORSConfig` from a case class to an abstract type for future binary compatibility

* Dependency updates
    * blaze-0.15.0
    * cats-parse-0.3.4
    * case-insensitive-1.1.4
    * circe-0.14.0-M7
    * ip4s-2.0.3
    * jawn-1.1.2
    * jawn-fs2-1.1.3
    * keypool-0.3.5
    * literally-1.0.2
    * log4cats-1.3.1
    * log4s-1.10.0-M7
    * scala-xml-2.0.0
    * vault-2.1.13

# v0.21.23 (2021-05-16)

This is the final planned release in the 0.21 series.  Bugfixes and community submissions will be considered for discretionary releases, but the development team will now focus on later branches.

* http4s-blaze-client
    * Bugfixes
        * [#4810](https://github.com/http4s/http4s/pull/4810): Read from idle blaze-client connections to prevent retaining (and trying to use) half-closed connections.
        * [#4812](https://github.com/http4s/http4s/pull/4812): Remove request retry on EOF from blaze-client. This could theoretically resubmit non-idempotent requests. The problem the retry attempted to solve is mitigated by #4810.
        * [#4815](https://github.com/http4s/http4s/pull/4815): Fix "`IdleTimeoutStage` isn't connected" errors by waiting for the final write to finish before returning the connection to the pool.

* http4s-blaze-core
    * Bugfixes
        * [#4796](https://github.com/http4s/http4s/pull/4796): Reset the idle timeout after `readRequest` completes, not when it's called.  Affects both blaze-server and blaze-client.

* http4s-blaze-server
    * Bugfixes
        * [#4753](https://github.com/http4s/http4s/pull/4753): Distinguish between reserved and unknown websocket frame opcodes. Resolves a `MatchError`.
        * [#4792](https://github.com/http4s/http4s/pull/4792): Fixes HTTP/2 connections on modern JDKs by replacing legacy ALPN libraries.
        * [#4796](https://github.com/http4s/http4s/pull/4796): Reset idle timeout when `readRequest` completes, not when it's called.
    * Enhancements
        * [#4761](https://github.com/http4s/http4s/pull/4761): Use the `TickWheelExecutor` to schedule timeouts with less locking.  Change how the parser is acquired to reduce lock contention in `Http1ServerStage`.  Significant increases in throughput are observed on small requests with many cores.

* http4s-circe
    * Enhancements
        * [#4736](https://github.com/http4s/http4s/pull/4736): Add `streamJsonArrayDecoder`

* http4s-ember-core
    * Enhancements
        * [#4735](https://github.com/http4s/http4s/pull/4735): Simplify message parsing by parsing everything up to the `\r\n` in one pass. The max header size and max prelude size settings should keep memory consumption limited.

* http4s-ember-server
    * Bugfixes
        * [#4750](https://github.com/http4s/http4s/pull/4750): Drain the socket's read buffer only after the response is written to the socket. Resolves several flavors of network error.
        * [#4823](https://github.com/http4s/http4s/pull/4823): Implement persistent connection logic for HTTP/1.0 requests.

* http4s-jetty
    * Bugfixes
        * [#4783](https://github.com/http4s/http4s/pull/4783): Fix bug with shared `ThreadPool` being destroyed. Prefer a `Resource[F, ThreadPool]` whose lifecycle shares Jetty's.  For compatibility, prevent the default from being destroyed.

* http4s-server
    * Enhancements
        * [#4793](https://github.com/http4s/http4s/pull/4793): Make use of IPv4 vs. IPv6 as default address explicit. Applies to all backends.

* Dependency updates
    * blaze-0.14.16
    * cats-2.6.1
    * cats-effect-2.5.1
    * dropwizard-metrics-4.2.0
    * discipline-core-1.1.5
    * jackson-databind-2.12.3
    * fs2-2.5.6
    * scalacheck-1.15.4
    * scodec-bits-1.1.27
    * tomcat-9.0.46

# v1.0.0-M21 (2021-04-10)

Contains all the changes of v0.22.0-M7.

* Dependency updates
    * cats-effect-3.0.1
    * jawn-fs2-2.0.1
    * keypool-0.4.1
    * log4cats-2.0.1
    * vault-3.0.1

# v0.22.0-M7 (2021-04-10)

Contains all the changes of v0.21.22.

* Cross builds
    * Add Scala-3.0.0-RC2
    * Drop Scala-3.0.0-RC1

* http4s-play-json
    * There is not yet an http4s-play-json build for Scala 3.0.0-RC2 because play-json doesn't yet support it.  A PR is open upstream, and we will revive it in the next release.

* Dependency updates
    * blaze-0.15.0-M3
    * case-insensitive-1.1.2
    * cats-parse-0.3.2
    * circe-0.14.0-M5
    * ip4s-2.0.1
    * jawn-1.1.1
    * jawn-fs2-1.1.1
    * keypool-0.3.3
    * log4cats-1.2.2
    * log4s-1.10.0-M6
    * literally-1.0.0
    * scala-xml-2.0.0-RC1
    * vault-2.1.9

# v0.21.22 (2021-04-06)

* http4s-blaze-client
    * Enhancements
        * [#4699](https://github.com/http4s/http4s/pull/4699): Add custom DNS resolver support to `BlazeClientBuilder`

* http4s-ember-server
        * [#4715](https://github.com/http4s/http4s/pull/4715): Fix regression in SSL handshake resulting in Connection Refused errors

* Dependency upgrades
    * cats-2.5.0
    * cats-effect-2.4.1
    * fs2-2.5.4
    * netty-4.1.63
    * scodec-bits-1.1.25
    * tomcat-9.0.45
    * twirl-1.5.1

# v1.0.0-M20 (2021-03-29)

Includes all the changes of v0.21.21 and v0.22.0-M6.

* Dependency updates
    * cats-effect-3.0.0
    * fs2-3.0.0
    * jawn-fs2-2.0.0
    * keypool-0.4.0
    * log4cats-2.0.0
    * vault-3.0.0

# v0.22.0-M6 (2021-03-29)

Includes all the changes of v0.21.21.

* http4s-core
    * Breaking changes
        * [#4588](https://github.com/http4s/http4s/pull/4588): Additional consistency in modeled headers around `.value` (removed in favor of the typeclass) and `.parse` (present on the companion)
        * [#4580](https://github.com/http4s/http4s/pull/4580): Rename `Uri.Path.fromString` to `Uri.Path.unsafeFromString`.
        * [#4581](https://github.com/http4s/http4s/pull/4581): Rename `Query.fromString` to `Query.unsafeFromString`.
        * [#4602](https://github.com/http4s/http4s/pull/4602): Remove `ipv4` and `ipv6` macros that clash with ip4s'.
        * [#4603](https://github.com/http4s/http4s/pull/4603): Drop deprecated members of `org.http4s.util`.
        * [#4604](https://github.com/http4s/http4s/pull/4604): Fix rendering of UTF-8-encoded `Content-Disposition` parameters.  The `parameters` map is now keyed by a `CIString`.
        * [#4630](https://github.com/http4s/http4s/pull/4630): Use Typesafe Literally to implement the literal interpolators. This should have zero impact on user code, but does affect binary compatibility.

* http4s-dsl
    * Breaking changes
        * [#4640](https://github.com/http4s/http4s/pull/4640): Change `apply(Headers.ToRaw*)` syntax to `headers(Headers.Raw)`. The overload from 0.21 was ambiguous with the new header model in 0.22.

* http4s-boopickle
    * Breaking changes
        * [#4590](https://github.com/http4s/http4s/pull/4590): Move implicits to `org.http4s.booPickle.implicits._`. The thinking is evolving here, and this is likely to be reverted before 0.22.0 final.

* http4s-scala-xml
    * Breaking changes
        * [#4621](https://github.com/http4s/http4s/pull/4621): Revert the `implicits` decoding back to how it was in 0.21.  Set up safer defaults for the default `SAXParserFactory`, corresponding to what will be in scala-xml-2.0.

* Dependency updates
    * async-http-client-2.12.3
    * case-insensitive-1.1.0
    * jackson-databind-2.12.2
    * literally-1.0.0-RC1 (new)
    * log4cats-1.2.1
    * vault-2.1.8

# v0.21.21 (2021-03-29)

* http4s-client
    * Enhancements
        * [#4614](https://github.com/http4s/http4s/pull/4614): Support for `Idempotent-Key` header in `Retry` middleware
        * [#4636](https://github.com/http4s/http4s/pull/4636): Add `isErrorOrStatus` to `RetryPolicy` to support retrying on different response statuses than the default set

* http4s-server
    * Bugfixes
        * [#4638](https://github.com/http4s/http4s/pull/4646): In `Caching` middleware, don't send `private` alongside `no-store`. These are contradictory directives.
        * [#4654](https://github.com/http4s/http4s/pull/4654): Return a 404 instead of 500 when requesting a path whose parent is a file instead of a directory

* http4s-ember-client
    * Enhancements
        * [#4637](https://github.com/http4s/http4s/pull/4637): Clarify which timeout is firing in the error message

* http4s-ember-server
    * Bugfixes
        * [#4637](https://github.com/http4s/http4s/pull/4637): On reused connections, wait for the idle period, not the shorter header timeout, for the next byte.

* http4s-play-json
    * Enhancements
        * [#4595](https://github.com/http4s/http4s/pull/4595): Streamline `Writes[Uri]` and `Reads[Uri]` instances

* http4s-scala-xml
    * Bugfixes
        * [#4620](https://github.com/http4s/http4s/pull/4620): Make XML chraset inference compliant with RFC7303
    * Enhancements
        * [#4622](https://github.com/http4s/http4s/pull/4622): Encode `scala.xml.Elem` with an XML declaration, including the charset.

* Dependency updates
    * cats-effect-2.4.0
    * jetty-9.4.39
    * netty-4.1.60
    * scalatags-0.9.4
    * tomcat-9.0.44

# v1.0.0-M19 (2021-03-03)

This is the first 1.0 milestone with Scala 3 support.  Scala 3.0.0-RC1 is supported for all modules except http4s-boopickle, http4s-scalatags, and http4s-twirl.

This release contains all the changes of v0.22.0-M5.

# v0.22.0-M5 (2021-03-03)

This is the first release with Scala 3 support.  Scala 3.0.0-RC1 is supported for all modules except http4s-boopickle, http4s-scalatags, and http4s-twirl.

* http4s-core
    * Breaking: New header model

      This release brings a new model for headers.  The model based on subtyping and general type projection used through http4s-0.21 is replaced by a `Header` typeclass.

        * There is no longer a `Header.Parsed`.  All headers are stored in `Headers` as `Header.Raw`.
        * `Header.Raw` is no longer a subtype of `Header`.  `Header` is now a typeclass.
        * New modeled headers can be registered simply by providing an instance of `Header`. The global registry, `HttpHeaderParser`, is gone.
        * `Headers` are created and `put` via a `Header.ToRaw` magnet pattern.  Instances of `ToRaw` include `Raw`, case classes with a `Header` instance, `(String, String)` tuples, and `Foldable[Header.ToRaw]`.  This makes it convenient to create headers from types that don't share a subtyping relationship, and preserves a feel mostly compatible with the old `Headers.of`.
        * `HeaderKey` is gone. To retrieve headers from the `Headers`, object, pass the type in `[]` instead of `()` (e.g., `headers.get[Location]`).
        * `from` no longer exists on the companion object of modeled headers.  Use the `get[X]` syntax.
        * `unapply` no longer exists on most companion objects of modeled headers.  This use dto be an alias to `from`.
        * "Parsed" headers are no longer memoized, so calling `headers.get[X]` twice will reparse any header with a name matching `Header[X].name` a second time.  It is not believed that headers were parsed multiple times often in practice.  Headers are still not eagerly parsed, so performance is expected to remain about the same.
        * The `Header` instance carries a phantom type, `Recurring` or `Single`.  This information replaces the old `HeaderKey.Recurring` and `HeaderKey.Singleton` marker classes, and is used to determine whether we return the first header or search for multiple headers.
        * Given `h1: Headers` and `h2.Headers`, `h1.put(h2)` and `h1 ++ h2` now replace all headers in `h1` whose key appears in `h2`.  They previously replaced only singleton headers and appended recurring headers.  This behavior was surprising to users, and required the global registry.
        * An `add` operation is added, which requires a value with a `HeaderKey.Recurring` instance.  This operation appends to any existing headers.
        * `Headers#toList` is gone, but `Headers#headers` returns a `List[Header.Raw]`. The name was changed to call attention to the fact that the type changed to raw headers.

        * See [#4415](https://github.com/http4s/http4s/pull/4415), [#4526](https://github.com/http4s/http4s/pull/4526), [#4536](https://github.com/http4s/http4s/pull/4536), [#4538](https://github.com/http4s/http4s/pull/4538), [#4537](https://github.com/http4s/http4s/pull/4537), [#5430](https://github.com/http4s/http4s/pull/5430), [#4540](https://github.com/http4s/http4s/pull/4540), [#4542](https://github.com/http4s/http4s/pull/4542), [#4543](https://github.com/http4s/http4s/pull/4543), [#4546](https://github.com/http4s/http4s/pull/4546), [#4549](https://github.com/http4s/http4s/pull/4549), [#4551](https://github.com/http4s/http4s/pull/4551), [#4545](https://github.com/http4s/http4s/pull/4545), [#4547](https://github.com/http4s/http4s/pull/4547), [#4552](https://github.com/http4s/http4s/pull/4552), [#4555](https://github.com/http4s/http4s/pull/4555), [#4559](https://github.com/http4s/http4s/pull/4559), [#4556](https://github.com/http4s/http4s/pull/4556), [#4562](https://github.com/http4s/http4s/pull/4562), [#4558](https://github.com/http4s/http4s/pull/4558), [#4563](https://github.com/http4s/http4s/pull/4563), [#4564](https://github.com/http4s/http4s/pull/4564), [#4565](https://github.com/http4s/http4s/pull/4565), [#4566](https://github.com/http4s/http4s/pull/4566), [#4569](https://github.com/http4s/http4s/pull/4569), [#4571](https://github.com/http4s/http4s/pull/4571), [#4570](https://github.com/http4s/http4s/pull/4570), [#4568](https://github.com/http4s/http4s/pull/4568), [#4567](https://github.com/http4s/http4s/pull/4567), [#4537](https://github.com/http4s/http4s/pull/4537), [#4575](https://github.com/http4s/http4s/pull/4575), [#4576](https://github.com/http4s/http4s/pull/4576).

    * Other breaking changes
        * [#4554](https://github.com/http4s/http4s/pull/4554): Remove deprecated `DecodeResult` methods
    * Enhancements
        * [#4579](https://github.com/http4s/http4s/pull/4579): Regenerate MimeDB from the IANA registry

# v1.0.0-M18 (2021-03-02)

Includes changes from v0.22.0-M4.

* http4s-core
    * Breaking changes
        * [#4516](https://github.com/http4s/http4s/pull/4516): Replace `Defer: Applicative` constraint with `Monad` in `HttpRoutes.of` and `ContextRoutes.of`.  This should be source compatible for nearly all users.  Users who can't abide this constraint can use `.strict`, at the cost of efficiency in combinining routes.
    * Enhancements
        * [#4351](https://github.com/http4s/http4s/pull/4351): Optimize multipart parser for the fact that pull can't return empty chunks
        * [#4485](https://github.com/http4s/http4s/pull/4485): Drop dependency to `cats-effect-std`. There are no hard dependencies on `cats.effect.IO` outside the tests.

* http4s-blaze-core
    * Enhancements
        * [#4425](https://github.com/http4s/http4s/pull/4425): Optimize entity body writer

* http4s-ember-server
    * Breaking changes
        * [#4471](https://github.com/http4s/http4s/pull/4471): `EmberServerBuilder` takes an ip4s `Option[Host]` and `Port` in its config instead of `String` and `Int`.
        * [#4515](https://github.com/http4s/http4s/pull/4515): Temporarily revert the graceful shutdown until a new version of FS2 suports it.

* Dependency updates
    * cats-effect-3.0.0-RC2
    * fs2-3.0.0-M9
    * jawn-fs2-2.0.0-RC3
    * ip4s-3.0.0-RC2
    * keypool-0.4.0-RC2
    * log4cats-2.0.0-RC1
    * vault-3.0.0-RC2

~~# v1.0.0-M17 (2021-03-02)~~

Missed the forward merges from 0.22.0-M4. Proceed directly to 1.0.0-M18.

# v0.22.0-M4 (2021-03-02)

Includes changes from v0.21.19 and v0.21.20.

* http4s-core
    * Breaking changes
        * [#4242](https://github.com/http4s/http4s/pull/4242): Replace internal models of IPv4, IPv6, `java.net.InetAddress`, and `java.net.SocketAddress` with ip4s.  This affects the URI authority, various headers, and message attributes that refer to IP addresses and hostnames.
        * [#4352](https://github.com/http4s/http4s/pull/4352): Remove deprecated `Header.Recurring.GetT` and ``Header.get(`Set-Cookie`)``.
        * [#4364](https://github.com/http4s/http4s/pull/4364): Remove deprecated `AsyncSyntax` and `NonEmpyListSyntax`. These were unrelated to HTTP.
        * [#4407](https://github.com/http4s/http4s/pull/4407): Relax constraint on `EntityEncoder.fileEncoder` from `Effect` to `Sync`. This is source compatible.
        * [#4519](https://github.com/http4s/http4s/pull/4519): Drop unused `Sync` constraints on `MultipartParser`, `Part`, and `KleisliSyntax`. This is source compatible.

* http4s-laws
    * Breaking changes
        * [#4519](https://github.com/http4s/http4s/pull/4519): Drop unused `Arbitrary` and `Shrink` constraints on `LawAdapter#booleanPropF`. This is source compatible.

* http4s-server
    * Breaking changes
        * [#4519](https://github.com/http4s/http4s/pull/4519): Drop unused `Functor` constraints in `HSTS`, `Jsonp`, `PushSupport`, `TranslateUri`, and `UriTranslation` middlewares. Drop unused `Sync` and `ContextShift` constraints in `staticcontent` package. These are source compatible.

* http4s-server
    * Breaking changes
        * [#4519](https://github.com/http4s/http4s/pull/4519): Drop unused `Async` constraint in `ServletContainer`. Drop unused `ContextShift` in `ServletContextSyntax`. These are source compatible.

* http4s-async-http-client
    * Breaking changes
        * [#4519](https://github.com/http4s/http4s/pull/4519): Drop unused `Sync` constraint on `AsyncHttpClientStats`. This is source compatible.

* http4s-prometheus
    * Breaking changes
        * [#4519](https://github.com/http4s/http4s/pull/4519): Drop unused `Sync` constraint on `PrometheusExportService`. This is source compatible.

* http4s-argonaut
    * Removal
        * [#4409](https://github.com/http4s/http4s/pull/4409): http4s-argonaut is no longer published

* http4s-json4s
    * Removal
        * [#4410](https://github.com/http4s/http4s/pull/4410): http4s-json4s, http4s-json4s-native, and http4s-json4s-jackson are no longer published

* http4s-play-json
    * Breaking changes
        * [#4371](https://github.com/http4s/http4s/pull/4371): Replace jawn-play with an internal copy of the facade to work around `withDottyCompat` issues.

* http4s-scala-xml
    * Breaking changes
        * [#4380](https://github.com/http4s/http4s/pull/4380): Move the implicits from the root package to a Cats-like encoding.  Suggest replacing `import org.http4s.scalaxml._` with `import org.http4s.scalaxml.implicits._`.

* Dependencies
    * blaze-0.15.0-M2
    * case-insensitive-1.0.0
    * cats-parse-0.3.1
    * circe-0.14.0-M4
    * ip4s-2.0.0-RC1
    * jawn-1.1.0
    * jawn-play (dropped)
    * keypool-0.3.0
    * log4cats-1.2.0
    * log4s-1.0.0-M5
    * play-json-2.10.0-RC2
    * scala-xml-2.0.0-M5
    * vault-2.1.7

# v0.21.20 (2021-03-02)

* http4s-core
    * Enhancements
        * [#4479](https://github.com/http4s/http4s/pull/4479): Add a `Hash[QValue]` instance
        * [#4512](https://github.com/http4s/http4s/pull/4512): Add `DecodeResult.successT` and `DecodeResult.failureT`, consistent with `EitherT`.  Deprecate the overloaded versions they replace.
    * Deprecations
        * [#4444](https://github.com/http4s/http4s/pull/4444): Deprecate the `RequestCookieJar` in favor of the `CookieJar` middleware

* http4s-ember-core
    * Bugfixes
        * [#4429](https://github.com/http4s/http4s/pull/4429), [#4466](https://github.com/http4s/http4s/pull/4466): Fix a few corner cases in the parser with respect to chunk boundaries

* http4s-servlet
    * Enhancements
        * [#4544](https://github.com/http4s/http4s/pull/4544): Remove redundant calculation and insertion of request attributes into the Vault

* Dependency upgrades
    * cats-2.4.1
    * cats-effect-2.3.2
    * dropwizard-metrics-4.1.18
    * fs2-2.5.3
    * jetty-9.4.38
    * json4s-3.6.11
    * scalacheck-1.15.3

# v0.21.19 (2021-02-13)

* http4s-core
    * Deprecations
        * [#4337](https://github.com/http4s/http4s/pull/4337): Deprecate `Header.Recurring.GetT`, which is unused

* http4s-client
    * Bugfixes
        * [#4403](https://github.com/http4s/http4s/pull/4403): Remove `Content-Coding` and `Content-Length` headers after decompressing in the `GZip` middleware.

* http4s-ember-core
    * Bugfixes
        * [#4348](https://github.com/http4s/http4s/pull/4348): Handle partially read bodies in persistent connections when the connection is recycled.

* http4s-ember-server
    * Enhancements
        * [#4400](https://github.com/http4s/http4s/pull/4400): Implement the `ConnectionInfo` and `SecureSession` request vault attributes, for parity with the Blaze and Servlet backends

* http4s-argonaut
        * [#4366](https://github.com/http4s/http4s/pull/4370): Deprecate http4s-argonaut.  It won't be published starting in 0.22.

* http4s-json4s, http4s-json4s-jackson, http4s-json4s-native
    * Deprecations
        * [#4370](https://github.com/http4s/http4s/pull/4370): Deprecate the http4s-json4s modules.  They won't be published starting in 0.22.

* http4s-scalatags
    * Enhancements
        * [#3850](https://github.com/http4s/http4s/pull/3850): Relax constraint on encoders from `TypedTag[String]` to `Frag[_, String]`

* Dependency updates
    * cats-2.4.1
    * netty-4.1.59.Final
    * okio-2.9.0
    * tomcat-9.0.43

# v1.0.0-M16 (2021-02-02)

Inherits the fixes of v0.21.18

~~# v1.0.0-M15 (2021-02-02)~~

~~Build failure.~~

Accidentally published from the 0.21.x series after a series of unfortunate events. Do not use.

# v0.22.0-M3 (2021-02-02)

Inherits the fixes of v0.21.18

# v0.21.18 (2021-02-02)

* http4s-blaze-server
    * Bug fixes
        * [#4337](https://github.com/http4s/http4s/pull/4337): Pass the `maxConnections` parameter to the blaze infrastructure correctly. The `maxConnections` value was being passed as the `acceptorThreads`, leaving `maxConnections` set to its Blaze default of 512.

* http4s-ember-core
    * Bug fixes
        * [#4335](https://github.com/http4s/http4s/pull/4335): Don't render an empty body with chunked transfer encoding on response statuses that don't permit a body (e.g., `204 No Content`).

# v1.0.0-M14

* [GHSA-xhv5-w9c5-2r2w](https://github.com/http4s/http4s/security/advisories/GHSA-xhv5-w9c5-2r2w): Additionally to the fix in v0.21.17, drops support for NIO2.

* http4s-okhttp-client
    * Breaking changes
        * [#4299](https://github.com/http4s/http4s/pull/4299): Manage the `Dispatcher` internally in `OkHttpBuilder`. `create` becomes a private method.
    * Documentation
        * [#4306](https://github.com/http4s/http4s/pull/4306): Update the copyright notice to 2021.

# v0.22.0-M2 (2021-02-02)

This release fixes a [High Severity vulnerability](https://github.com/http4s/http4s/security/advisories/GHSA-xhv5-w9c5-2r2w) in blaze-server.

* http4s-blaze-server
    * [GHSA-xhv5-w9c5-2r2w](https://github.com/http4s/http4s/security/advisories/GHSA-xhv5-w9c5-2r2w): Additionally to the fix in v0.21.17, drops support for NIO2.

* http4s-core
    * Enhancements
        * [#4286](https://github.com/http4s/http4s/pull/4286): Improve performance by using `oneOf` and caching a URI parser. This was an identified new hotspot in v0.22.0-M1.
    * Breaking changes
        * [#4259](https://github.com/http4s/http4s/pull/4259): Regenerate `MimeDb` from the IANA database. This shifts around some constants in a binary incompatible way, but almost nobody will notice.
        * [#4327](https://github.com/http4s/http4s/pull/4237): Shifted the parsers around in `Uri` to prevent deadlocks that appeared since M1.  This should not be visible, but is binary breaking.

* http4s-prometheus
    * Breaking changes
        * [#4273](https://github.com/http4s/http4s/pull/4273): Change metric names from `_count` to `_count_total` to match Prometheus' move to the OpenMetrics standard.  Your metrics names will change!  See [prometheus/client_java#615](https://github.com/prometheus/client_java/pull/615) for more details from the Prometheus team.

* Dependency updates
    * jawn-fs2-1.0.1
    * keypool-0.3.0-RC1 (moved to `org.typelevel`)
    * play-json-2.10.0-RC1
    * simpleclient-0.10.0 (Prometheus)

# v0.21.17 (2021-02-02)

This release fixes a [High Severity vulnerability](https://github.com/http4s/http4s/security/advisories/GHSA-xhv5-w9c5-2r2w) in blaze-server.

* http4s-blaze-server
    * Security patches
        * [GHSA-xhv5-w9c5-2r2w](https://github.com/http4s/http4s/security/advisories/GHSA-xhv5-w9c5-2r2w): blaze-core, a library underlying http4s-blaze-server, accepts connections without bound.  Each connection claims a file handle, a scarce resource, leading to a denial of service vector.
          `BlazeServerBuilder` now has a `maxConnections` property, limiting the number of concurrent connections.  The cap is not applied to the NIO2 socket server, which is now deprecated.

* http4s-ember-core
    * Enhancements
        * [#4331](https://github.com/http4s/http4s/pull/4331): Don't render an empty chunked payload if a request has neither a `Content-Length` or `Transfer-Encoding` and the method is one of `GET`, `DELETE`, `CONNECT`, or `TRACE`. It is undefined behavior for those methods to send payloads.

* http4s-ember-server
    * Bugfixes
        * [#4281](https://github.com/http4s/http4s/pull/4281): Add backpressure to ember startup, so the server is up before `use` returns.
    * Enhancements
        * [#4244](https://github.com/http4s/http4s/pull/4244): Internal refactoring of how the stream of server connections is parallelized and terminated.
        * [#4287](https://github.com/http4s/http4s/pull/4287): Replace `onError: Throwable => Response[F]` with `withErrorHandler: PartialFunction[Thrwable, F[Response[F]]`.  Error handling is invoked earlier, allowing custom responses to parsing and timeout failures.

* http4s-ember-client
    * Enhancements
        * [#4301](https://github.com/http4s/http4s/pull/4301): Add an `idleConnectionTime` to `EmberClientBuilder`. Discard stale connections from the pool and try to acquire a new one.

* http4s-servlet
    * Bugfixes
        * [#4309](https://github.com/http4s/http4s/pull/4309): Call `GenericServlet.init` when intializing an `Http4sServlet`.  Avoids `NullPointerExceptions` from the `ServletConfig`.

* Documentation
        * [#4261](https://github.com/http4s/http4s/pull/4261): Better `@see` links throughout the Scaladoc

* Dependency upgrades
    * blaze-0.14.15
    * okhttp-4.9.1

# v1.0.0-M13 (2021-01-25)

This is the first milestone built on Cats-Effect 3.  To track Cats-Effect 2 development, please see the new 0.22.x series.  Everything in 0.22.0-M1, including the cats-parse port, is here.

* http4s-core
    * Breaking changes
        * [#3784](https://github.com/http4s/http4s/pull/3784), [#3865](https://github.com/http4s/http4s/pull/3784): Inexhaustively,
            * Many `EntityDecoder` constraints relaxed from `Sync` to `Concurrent`.
            * File-related operations require a `Files` constraint.
            * `Blocker` arguments are no longer required.
            * `ContextShift` constraints are no longer required.
            * The deprecated, non-HTTP `AsyncSyntax` is removed.
        * [#3886](https://github.com/http4s/http4s/pull/3886):
            * Relax `Sync` to `Defer` in `HttpApp` constructor.
            * Relax `Sync` to `Concurrent` in `Logger` constructors.
            * Remove `Sync` constraint from `Part` constructors.
            * Relax `Sync` to `Functor` in various Kleisli syntax.

* http4s-laws
    * Breaking changes
        * [#3807](https://github.com/http4s/http4s/pull/3807): Several arbitraries and cogens now require a `Dispatcher` and a `TestContext`.

* http4s-client
    * [#3857](https://github.com/http4s/http4s/pull/3857): Inexhaustively,
        * `Monad: Clock` constraints changed to `Temporal`
        * `Client.translate` requires an `Async` and `MonadCancel`
        * Removal of `Blocker` from `JavaNetClientBuilder`
        * `PoolManager` changed from `Concurrent` to `Async`
        * Many middlewares changed from `Sync` to `Async`
    * [#4081](https://github.com/http4s/http4s/pull/4081): Change `Metrics` constraints from `Temporal` to `Clock: Concurrent`

* http4s-server
    * [#3857](https://github.com/http4s/http4s/pull/3857): Inexhaustively,
        * `Monad: Clock` constraints changed to `Temporal`
        * Many middlewares changed from `Sync` to `Async`
    * [#4081](https://github.com/http4s/http4s/pull/4081): Change `Metrics` constraints from `Temporal` to `Clock: Concurrent`

* http4s-async-http-client
    * Breaking changes
        * [#4149](https://github.com/http4s/http4s/pull/4149): `ConcurrentEffect` constraint relaxed to `Async`. `apply` method changed to `fromClient` and returns a `Resource` to account for the `Dispatcher`.

* http4s-blaze-core
    * Breaking changes
        * [#3894](https://github.com/http4s/http4s/pull/3894): Most `Effect` constraints relaxed to `Async`.

* http4s-blaze-server
    * Breaking changes
        * [#4097](https://github.com/http4s/http4s/pull/4097), [#4137](https://github.com/http4s/http4s/pull/4137): `ConcurrentEffect` constraint relaxed to `Async`. Remove deprecated `BlazeBuilder`

* http4s-blaze-client
    * Breaking changes
        * [#4097](https://github.com/http4s/http4s/pull/4097): `ConcurrentEffect` constraint relaxed to `Async`

* http4s-ember-client
    * Breaking changes
        * [#4256](https://github.com/http4s/http4s/pull/4256): `Concurrent: Timer: ContextShift` constraint turned to `Async`

* http4s-ember-server
    * Breaking changes
        * [#4256](https://github.com/http4s/http4s/pull/4256): `Concurrent: Timer: ContextShift` constraint turned to `Async`

* http4s-okhttp-client
    * Breaking changes
    * [#4102](https://github.com/http4s/http4s/pull/4102), [#4136](https://github.com/http4s/http4s/pull/4136):
    * `OkHttpBuilder` takes a `Dispatcher`
    * `ConcurrentEffect` and `ContextShift` constraints replaced by `Async`

* http4s-servlet
    * Breaking changes
        * [#4175](https://github.com/http4s/http4s/pull/4175): Servlets naow take a `Dispatcher`. The blocker is removed from `BlockingIo`. `ConcurrentEffect` constraint relaxed to `Async`.

* http4s-jetty-client
    * Breaking changes
        * [#4165](https://github.com/http4s/http4s/pull/4165): `ConcurrentEffect` constraint relaxed to `Async`

* http4s-jetty
    * Breaking changes
        * [#4191](https://github.com/http4s/http4s/pull/4191): `ConcurrentEffect` constraint relaxed to `Async`

* http4s-tomcat
    * Breaking changes
        * [#4216](https://github.com/http4s/http4s/pull/4216): `ConcurrentEffect` constraint relaxed to `Async`

* http4s-jawn
    * Breaking changes
        * [#3871](https://github.com/http4s/http4s/pull/3871): `Sync` constraints relaxed to `Concurrent`

* http4s-argonaut
    * Breaking changes
        * [#3961](https://github.com/http4s/http4s/pull/3961): `Sync` constraints relaxed to `Concurrent`

* http4s-circe
    * Breaking changes
        * [#3965](https://github.com/http4s/http4s/pull/3965): `Sync` constraints relaxed to to `Concurrent`.

* http4s-json4s
    * Breaking changes
        * [#3885](https://github.com/http4s/http4s/pull/3885): `Sync` constraints relaxed to to `Concurrent`.

* http4s-play-json
    * Breaking changes
        * [#3962](https://github.com/http4s/http4s/pull/3962): `Sync` constraints relaxed to to `Concurrent`.

* http4s-scala-xml
    * Breaking changes
        * [#4054](https://github.com/http4s/http4s/pull/4054): `Sync` constraints relaxed to to `Concurrent`.

* http4s-boopickle
    * Breaking changes
        * [#3871](https://github.com/http4s/http4s/pull/3852): `Sync` constraints relaxed to `Concurrent`

* Dependency updates
    * cats-effect-3.0.0-M5
    * fs2-3.0.0-M7
    * jawn-1.0.3
    * jawn-fs2-2.0.0-M2
    * keypool-0.4.0-M1 (moved to `org.typelevel`)
    * log4cats-2.0.0-M1
    * vault-3.0.0-M1

~~# v1.0.0-M12 (2021-01-25)~~

Build failure

~~# v1.0.0-M11 (2021-01-25)~~

Partial publish after build failure

# v0.22.0-M1 (2021-01-24)

This is a new series, forked from main before Cats-Effect 3 support was merged.  It is binary incompatible with 0.21, but contains several changes that will be necessary for Scala 3 (Dotty) support. It builds on all the changes from v1.0.0-M1 through v1.0.0-M10, which are not echoed here.

The headline change is that all parboiled2 parsers have been replaced with cats-parse.

* Should I switch?

* Users who had been tracking the 1.0 series, but are not prepared for Cats Effect 3, should switch to this series.
* Users who wish to remain on the bleeding edge, including Cats Effect 3, should continue track the 1.0 series.
* Users who need a stable release should remain on the 0.21 series for now.

* http4s-core
    * Breaking changes
        * [#3855](https://github.com/http4s/http4s/pull/3855): All parboiled2 parsers are replaced by cats-parse.  parboiled2 was not part of the public API, nor are our cats-parse parsers.  Users may observe a difference in the error messages and subtle semantic changes.  We've attempted to minimize them, but this is a significant underlying change.  See also: [#3897](https://github.com/http4s/http4s/pull/3897), [#3901](https://github.com/http4s/http4s/pull/3901), [#3954](https://github.com/http4s/http4s/pull/3954), [#3958](https://github.com/http4s/http4s/pull/3958), [#3995](https://github.com/http4s/http4s/pull/3995), [#4023](https://github.com/http4s/http4s/pull/4023), [#4001](https://github.com/http4s/http4s/pull/4001), [#4013](https://github.com/http4s/http4s/pull/4013), [#4042](https://github.com/http4s/http4s/pull/4042), [#3982](https://github.com/http4s/http4s/pull/3982), [#4071](https://github.com/http4s/http4s/pull/4071), [#4017](https://github.com/http4s/http4s/pull/4017), [#4132](https://github.com/http4s/http4s/pull/4132), [#4154](https://github.com/http4s/http4s/pull/4154), [#4200](https://github.com/http4s/http4s/pull/4200), [#4202](https://github.com/http4s/http4s/pull/4202), [#4206](https://github.com/http4s/http4s/pull/4206), [#4201](https://github.com/http4s/http4s/pull/4201), [#4208](https://github.com/http4s/http4s/pull/4208), [#4235](https://github.com/http4s/http4s/pull/4235), [#4147](https://github.com/http4s/http4s/pull/4147), [#4238](https://github.com/http4s/http4s/pull/4238) [#4238](https://github.com/http4s/http4s/pull/4243)
        * [#4070](https://github.com/http4s/http4s/pull/4070): No longer publish a `scala.annotations.nowarn` annotation in the 2.12 build.  This is provided in the standard library in 2.12.13, and isn't necessary at runtime in any version.
        * [#4138](https://github.com/http4s/http4s/pull/4138): Replace boolean with `Weakness` sum type in `EntityTag` model
        * [#4148](https://github.com/http4s/http4s/pull/4148): Lift `ETag.EntityTag` out of header and into the `org.http4s` package
        * [#4164](https://github.com/http4s/http4s/pull/4164): Removal of several deprecated interfaces.  Most were non-public binary compatibility shims, or explicit cats instances that had been superseded by new implicits.  Some exceptions:
        * [#4145](https://github.com/http4s/http4s/pull/4145): Port macros in `org.http4s.syntax.literals` to Scala 3.  Deprecated macros that were on various companion objects will not be in the Scala 3 releases.
    * Bugfixes
        * [#4017](https://github.com/http4s/http4s/pull/4017): Render a final `-` in a byte ranges without an end value

* http4s-laws
    * Breaking changes
        * [#4144](https://github.com/http4s/http4s/pull/4144): Add `LawsAdapter` to create `PropF` for effectful properties.  Restate various Entity codec laws in terms of it.
        * [#4164](https://github.com/http4s/http4s/pull/4164): Removed arbitrary instances for `CIString`. These are provided by case-insensitive.

* http4s-server
    * Breaking changes
        * [#4164](https://github.com/http4s/http4s/pull/4164): Removed deprecated `SSLConfig`, `KeyStoreBits`, `SSLContextBits`, and `SSLBits`.

* http4s-testing
    * Breaking changes
        * [#4164](https://github.com/http4s/http4s/pull/4164): No longer a publicly published package. All public API was previously deprecated.

* Dependency upgrades
    * async-http-client-2.12.2
    * cats-parse-0.3.0
    * circe-0.14.0-M3
    * jackson-databind-2.12.1
    * jawn-1.0.3
    * log4cats-1.2.0-RC1 (now under `org.typelevel`)
    * log4s-1.0.0-M4
    * okio-2.10.0
    * vault-2.1.0-M14 (now under `org.typelevel`)

* Dependency removals
    * parboiled2

# v0.21.16 (2021-01-24)

* http4s-laws
    * Bugfixes
        * [#4243](https://github.com/http4s/http4s/pull/4243): Don't generate ipv6 addresses with only one section shorted by `::`

* http4s-blaze-core
    * Bugfixes
        * [#4143](https://github.com/http4s/http4s/pull/4143): Fix race condition that leads to `WritePendingException`. A tradeoff of this change is that some connections that were previously reused must now be closed.

* http4s-blaze-client
    * Bugfixes
        * [#4152](https://github.com/http4s/http4s/pull/4152): Omit implicit `Content-Length: 0` header when rendering GET, DELETE, CONNECT, and TRACE requests.

* http4s-ember-client
    * Bugfixes
        * [#4179](https://github.com/http4s/http4s/pull/4179): Render requests in "origin form", so the request line contains only the path of the request, and host information is only in the Host header.  We were previously rendering the fulll URI on the request line, which the spec mandates all servers to handle, but clients should not send when not speaking to a proxy.

* http4s-ember-server
    * Enhancements
        * [#4179](https://github.com/http4s/http4s/pull/4179): Support a graceful shutdown

* http4s-circe
    * Enhancements
        * [#4124](https://github.com/http4s/http4s/pull/4124): Avoid intermediate `ByteBuffer` duplication

# v1.0.0-M10 (2020-12-31)

* http4s-client
    * Enhancements
        * [#4051](https://github.com/http4s/http4s/pull/4051): Add `customized` function to `Logger` middleware that takes a function to produce the log string. Add a `colored` implementation on that that adds colors to the logs.

* Dependency updates
    * argonaut-6.3.3
    * dropwizard-metrics-4.1.17
    * netty-4.1.58.Final
    * play-json-29.9.2
    * scalatags-0.9.3

# v0.21.15 (2020-12-31)

* http4s-core
    * Enhancements
        * [#4014](https://github.com/http4s/http4s/pull/4014): Tolerate spaces in cookie headers. These are illegal per RFC6265, but commonly seen in the wild.
        * [#4113](https://github.com/http4s/http4s/pull/4113): Expose a mixed multipart decoder that buffers large file parts to a temporary file.

* http4s-server
    * Enhancements
        * [#4026](https://github.com/http4s/http4s/pull/4026): Add `Resource`-based constructors to the `BracketRequestResponse` middleware.
        * [#4037](https://github.com/http4s/http4s/pull/4037): Normalize some default settings between server backends to standard http4s defaults, to make a more similar experience between backends.  This changes some defaults for Ember and Jetty backends.

* http4s-jetty
    * Enhancements
        * [#4032](https://github.com/http4s/http4s/pull/4032): Add an `HttpConfiguration` parameter to the Jetty builder to support deeper configuration than what is otherwise available on the builer.  Use it for both HTTP/1 and HTTP/2.

* http4s-jetty-client
    * Enhancements
        * [#4110](https://github.com/http4s/http4s/pull/4110): Provide an `SslContextFactory` in the default configuration. Before this, secure requests would throw a `NullPointerException` unless a custom Jetty `HttpClient` was used.

* Documentation
    * [#4020](https://github.com/http4s/http4s/pull/4020): Improvements to scaladoc. Link to other projects' scaladoc where we can and various cleanups of our own.
    * [#4025](https://github.com/http4s/http4s/pull/4025): Publish our own API URL, so other scaladoc can link to us

* http4s-circe
    * [#4012](https://github.com/http4s/http4s/pull/4012): Add sensitive EntityDecoders for circe that filter JSON that couldn't be decoded before logging it.

* Dependency bumps
    * cats-2.3.1
    * cats-effect-2.3.1
    * discipline-core-1.1.3
    * fs2-2.5.0
    * jackson-databind-2.11.4
    * netty-4.1.56.Final
    * scodec-bits-1.1.23

# v1.0.0-M9 (2020-12-12)

* http4s-core
    * Breaking changes
        * [#3913](https://github.com/http4s/http4s/pull/3913): Regenerated the `MimeDb` trait from the IANA registry. This shifts a few constants around and is binary breaking, but the vast majority of users won't notice.

* Dependency updates
* jackson-databind-2.12.0

# v0.21.14 (2020-12-11)

* http4s-core
    * Bugfixes
        * [#3966](https://github.com/http4s/http4s/pull/3966): In `Link` header, retain the first `rel` attribute when multiple are present
    * Enhancements
        * [#3937](https://github.com/http4s/http4s/pull/3937): Add `Order[Charset]` and `Hash[Charset]` instances
        * [#3969](https://github.com/http4s/http4s/pull/3969): Add `Order[Uri]`, `Hash[Uri]`, and `Show[Uri]`. Add the same for its component types.
        * [#3966](https://github.com/http4s/http4s/pull/3966): Add `Order[Method]` instance

* http4s-server
    * Enhancements
        * [#3977](https://github.com/http4s/http4s/pull/3977): Add a `BracketRequestResponse` middleware, to reflect lifecycles between acquiring the `F[Response[F]]` and completion of the response body `Stream[F, Byte]`.  Introduces a new `ConcurrentRequests` middleware, and refactors `MaxActiveRequests` on top of it.

* http4s-okhttp-client
    * Bugfixes
        * [#4006](https://github.com/http4s/http4s/pull/4006): Set `Content-Length` header on requests where available instead of always chunking

* http4s-metrics
    * Bugfixes
        * [#3977](https://github.com/http4s/http4s/pull/3977): Changes from `BracketRequestResponse` middleware may address reported leaks in `decreaseActiveRequests`.  Corrects a bug in `recordHeadersTime`.  Also can now record times for abnormal terminations.

* Internals

    Should not affect end users, but noted just in case:

    * [#3964](https://github.com/http4s/http4s/pull/3964): Replace `cats.implicits._` imports with `cats.syntax.all._`. Should not be user visible.
    * [#3963](https://github.com/http4s/http4s/pull/3963), [#3983](https://github.com/http4s/http4s/pull/3983): Port several tests to MUnit. This helps with CI health.
    * [#3980](https://github.com/http4s/http4s/pull/3980): Integrate new sbt-http4s-org plugin with sbt-spiewak

* Dependency bumps
    * cats-2.3.0
    * cats-effect-2.3.0
    * dropwizard-metrics-4.1.16
    * scodec-bits-1.1.22

# v1.0.0-M8 (2020-11-26)

* Breaking changes
    * http4s-client
        * [#3903](https://github.com/http4s/http4s/pull/3903): Method apply syntax (e.g., `POST(body, uri)`) returns a `Request[F]` instead of `F[Request[F]]`

# v0.21.13 (2020-11-25)

* Bugfixes
    * Most modules
        * [#3932](https://github.com/http4s/http4s/pull/3932): Fix `NoClassDefFoundError` regression.  An example:

  ```
  [info]   java.lang.NoClassDefFoundError: cats/effect/ResourceLike
  [info]   at org.http4s.client.Client$.$anonfun$fromHttpApp$2(Client.scala:246)
  ```

  A test dependency upgrade evicted our declared cats-effect-2.2.0 dependency, so we built against a newer version than we advertise in our POM.  Fixed by downgrading the test dependency and inspecting the classpath.  Tooling will be added to avoid repeat failures.

# v0.21.12 (2020-11-25)

* Bugfixes
    * http4s-core
        * [#3911](https://github.com/http4s/http4s/pull/3911): Support raw query strings. Formerly, all query strings were stored as a vector of key-value pairs, which was lossy in the percent-encoding of sub-delimiter characters (e.g., '+' vs '%2B').  Queries constructed with `.fromString` will be rendered as-is, for APIs that assign special meaning to sub-delimiters.
        * [#3921](https://github.com/http4s/http4s/pull/3921): Fix rendering of URIs with colons. This was a regression in v0.21.9.
    * http4s-circe
        * [#3906](https://github.com/http4s/http4s/pull/3906): Fix streamed encoder for empty stream. It was not rendering the `[F`.

* Enhancements
    * http4s-core
        * [#3902](https://github.com/http4s/http4s/pull/3902): Add `Hash` and `BoundedEnumerable` instances for `HttpVersion`
        * [#3909](https://github.com/http4s/http4s/pull/3909): Add `Order` instance for `Header` and `Headers`

* Dependency upgrades
    * fs2-2.4.6
    * jetty-9.4.35.v20201120

# v1.0.0-M7 (2020-11-20)

* Breaking changes
    * http4s-dsl
        * [#3876](https://github.com/http4s/http4s/pull/3876): Replace `dsl.Http4sDsl.Path` with `core.Uri.Path`. The new `Path` in 1.0 is rich enough to support the DSL's routing needs, and this eliminates a conversion between models on every `->` extractor.  This change is source compatible in typical extractions.

* Dependency updates
    * argonaut-6.3.2

# v0.21.11 (2020-11-20)

* Enhancements
    * http4s-core
        * [#3864](https://github.com/http4s/http4s/pull/3864): Cache a `Right` of the common `HttpVersion`s for its `ParseResult`.
    * http4s-circe
        * [#3891](https://github.com/http4s/http4s/pull/3891): Encode JSON streams in their constituent chunks instead of a chunk-per-`Json`. This can significantly reduce the network flushes on most backends.
    * http4s-dsl
        * [#3844](https://github.com/http4s/http4s/pull/3844): Add `MatrixVar` extractor for [Matrix URIs](https://www.w3.org/DesignIssues/MatrixURIs.html)
    * http4s-async-http-client
        * [#3859](https://github.com/http4s/http4s/pull/3859): Add `AsyncHttpClient.apply` method that takes an already constructed async-http-client. This is useful for keeping a handle on bespoke of the client, such as its stats. Adds a functional `AsyncHttpClientStats` wrapper around the native stats class.

* Internals
    These changes should be transparent, but are mentioned for completeness.

    * Dotty preparations
        * [#3798](https://github.com/http4s/http4s/pull/3798): Parenthesize some arguments to lambda functions.
    * Build
        * [#3868](https://github.com/http4s/http4s/pull/3868), [#3870](https://github.com/http4s/http4s/pull/3870): Start building with sbt-github-actions.

* Dependency updates
    * discipline-1.1.2
    * dropwizard-metrics-4.1.15
    * jackson-databind-2.11.3
    * jawn-1.0.1
    * netty-4.1.54.Final
    * okio-2.9.0
    * tomcat-9.0.40

~~# v0.21.10 (2020-11-20)~~

Cursed release, accidentally tagged from main.
Proceed directly to 0.21.11.

# v1.0.0-M6 (2020-11-11)

* Breaking changes
    * [#3758](https://github.com/http4s/http4s/pull/3758): Refactor query param infix operators for deprecations in Scala 2.13. Not source breaking.
    * [#3366](https://github.com/http4s/http4s/pull/3366): Add `Method` and `Uri` to `UnexpectedStatus` exception to improve client error handling. Not source breaking in most common usages.

# v0.21.9 (2020-11-11)

* Bugfixes
    * [#3757](https://github.com/http4s/http4s/pull/3757): Restore mixin forwarders in `Http4sDsl` for binary compatibility back to v0.21.0.  These were removed in v0.21.6 by [#3492](https://github.com/http4s/http4s/pull/3492), but not caught by an older version of MiMa.
    * [#3752](https://github.com/http4s/http4s/pull/3752): Fix rendering of absolute `Uri`s with no scheme.  They were missing the `//`.
    * [#3810](https://github.com/http4s/http4s/pull/3810): In okhttp-client, render the request body synchronously on an okhttp-managed thread. There was a race condition that could truncate bodies.
* Enhancements
    * [#3609](https://github.com/http4s/http4s/pull/3609): Introduce `Forwarded` header
    * [#3789](https://github.com/http4s/http4s/pull/3789): In Ember, apply `Transfer-Encoding: chunked` in the absence of contrary information
    * [#3815](https://github.com/http4s/http4s/pull/3815): Add `Show`, `Hash`, and `Order` instances to `QueryParamKey` and `QueryParamValue`
    * [#3820](https://github.com/http4s/http4s/pull/3820): In jetty-client, eliminate uninformative request logging of failures

* Dotty preparations

    Dotty support remains [in progress](https://github.com/http4s/http4s/projects/5), though many http4s features can be used now in compatibility mode.

    * [#3767](https://github.com/http4s/http4s/pull/3767): Name "unbound placeholders."
    * [#3757](https://github.com/http4s/http4s/pull/3757): Replace `@silent` annotations with `@nowarn`.

* Dependency updates
    * blaze-0.14.14
    * discipline-specs2-1.1.1
    * dropwizard-metrics-4.1.14
    * fs2-2.4.5
    * jetty-9.4.34.v20201102
    * log4s-1.9.0
    * scalacheck-1.15.1

# v1.0.0-M5 (2020-10-16)

* Bugfixes
    * [#3714](https://github.com/http4s/http4s/pull/3638): Use correct prefix when composing with `Router`
    * [#3738](https://github.com/http4s/http4s/pull/3738): In `PrometheusExportService`, correctly match the `/metrics` endpoint
* Breaking changes
    * [#3649](https://github.com/http4s/http4s/pull/3649): Make `QueryParam` a subclass of `QueryParamLike`
    * [#3440](https://github.com/http4s/http4s/pull/3440): Simplify `Method` model. Drop `PermitsBody`, `NoBody`, and `Semantics` mixins. No longer a case class.
* Enhancements
    * [#3638](https://github.com/http4s/http4s/pull/3638): Model `Access-Control-Expose-Headers`
    * [#3735](https://github.com/http4s/http4s/pull/3735): Add `preferGzipped` parameter to `WebjarServiceBuilder`

* Dependency updates
    * argonaut-6.3.1

# v0.21.8 (2020-10-16)

* Security
    * [GHSA-8hxh-r6f7-jf45](https://github.com/http4s/http4s/security/advisories/GHSA-8hxh-r6f7-jf45): The version of Netty used by async-http-client is affected by [CVE-2020-11612](https://app.snyk.io/vuln/SNYK-JAVA-IONETTY-564897).  A server we connect to with http4s-async-http-client could theoretically respond with a large or malicious compressed stream and exhaust memory in the client JVM. This does not affect any release in the 1.x series.

* Bugfixes
    * [#3666](https://github.com/http4s/http4s/pull/3666): In CSRF middleware, always use the `onFailure` handler instead of a hardcoded 403 response
    * [#3716](https://github.com/http4s/http4s/pull/3716): Fail in `Method.fromString` when a token is succeeded by non-token characters.
    * [#3743](https://github.com/http4s/http4s/pull/3743): Fix `ListSep` parser according to RFC.

* Enhancements
    * [#3605](https://github.com/http4s/http4s/pull/3605): Improve header parsing in Ember
    * [#3634](https://github.com/http4s/http4s/pull/3634): Query parameter codecs for `LocalDate` and `ZonedDate`
    * [#3659](https://github.com/http4s/http4s/pull/3659): Make requests to mock client cancelable
    * [#3701](https://github.com/http4s/http4s/pull/3701): In `matchHeader`, only parse headers with matching names. This improves parsing laziness.
    * [#3641](https://github.com/http4s/http4s/pull/3641): Add `FormDataDecoder` to decode `UrlForm` to case classes via `QueryParamDecoder`

* Documentation
    * [#3693](https://github.com/http4s/http4s/pull/3693): Fix some typos
    * [#3703](https://github.com/http4s/http4s/pull/3703): Fix non-compiling example in streaming.md
    * [#3670](https://github.com/http4s/http4s/pull/3670): Add scaladocs for various headers, including RFC links
    * [#3692](https://github.com/http4s/http4s/pull/3692): Mention partial unification is no longer needed in Scala 2.13
    * [#3710](https://github.com/http4s/http4s/pull/3710): Add docs for `OptionalValidatingQueryParamDecoderMatcher`
    * [#3712](https://github.com/http4s/http4s/pull/3712): Add integrations.md with feature comparison of backends

* Miscellaneous
    * [#3742](https://github.com/http4s/http4s/pull/3742): Drop JDK14 tests for JDK15

* Dependency updates
    * dropwizard-metrics-4.1.13
    * cats-2.2.0
    * cats-effect-2.2.0
    * fs2-2.4.4
    * jetty-9.4.32.v20200930
    * json4s-3.6.10
    * netty-4.1.53.Final (async-http-client transitive dependency)
    * okhttp-4.9.0
    * play-json-2.9.1
    * scalafix-0.9.21
    * scalatags-0.9.2
    * tomcat-9.0.39

# v1.0.0-M4 (2020-08-09)

This milestone merges the changes in 0.21.7.
It is not binary compatible with 1.0.0-M3

* Breaking changes
    * [#3577](https://github.com/http4s/http4s/pull/3577): Add a model of the `Max-Forwards` header.
    * [#3567](https://github.com/http4s/http4s/pull/3577): Add a model of the `Content-Language` header.
    * [#3555](https://github.com/http4s/http4s/pull/3555): Support for UTF-8 basic authentication, per [RFC7617](https://tools.ietf.org/html/rfc7617). Attempt to decode Basic auth credentials as UTF-8, falling back to ISO-8859-1. Provide a charset to `BasicCredentials` that allows encoding with an arbitrary charset, defaulting to UTF-8.
    * [#3583](https://github.com/http4s/http4s/pull/3583): Allow configuration of `CirceInstances` to permit duplicate keys
    * [#3587](https://github.com/http4s/http4s/pull/3587): Model `Access-Control-Allow-Headers` header
* Documentation
    * [#3571](https://github.com/http4s/http4s/pull/3571): Fix comments in deprecated `AgentToken`, `AgentComment`, and `AgentProduct`.

* Dependency updates
    * dropwizard-metrics-4.1.12

# v0.21.7 (2020-08-08)

* Bugfixes
    * [#3548](https://github.com/http4s/http4s/pull/3548): Fixes `IllegalStateException` when a path matches a directory in `ResourceService`
    * [#3546](https://github.com/http4s/http4s/pull/3546): In ember, encode headers as ISO-8859-1. Includes performance improvements
    * [#3550](https://github.com/http4s/http4s/pull/3550): Don't attempt to decompress empty response bodies in `GZip` client middleware
    * [#3598](https://github.com/http4s/http4s/pull/3598): Fix connection keep-alives in ember-client
    * [#3594](https://github.com/http4s/http4s/pull/3594): Handle `FileNotFoundException` in `StaticFile.fromURL` by returning a 404 response
    * [#3625](https://github.com/http4s/http4s/pull/3625): Close `URLConnection` in `StaticFile.fromURL` when the resource is not expired
    * [#3624](https://github.com/http4s/http4s/pull/3624): Use client with the http4s defaults instead of a the Jetty defaults in `JettyClientBuilder#resource` and `JettyClientBuilder#stream`

* Enhancements
    * [#3552](https://github.com/http4s/http4s/pull/3552): Add `liftKleisli` operation to `Client.` This is useful for integration with [natchez](https://github.com/tpolecat/natchez).
    * [#3566](https://github.com/http4s/http4s/pull/3566): Expose `RetryPolicy.isErrorOrRetriablestatus`
    * [#3558](https://github.com/http4s/http4s/pull/3558): Add `httpRoutes` and `httpApp` convenience constructors to `HSTS` middleware
    * [#3559](https://github.com/http4s/http4s/pull/3559): Add `httpRoutes` and `httpApp` convenience constructors to `HttpsRedirect` middleware
    * [#3623](https://github.com/http4s/http4s/pull/3623): Add `configure` method to allow more configurations of async-http-client
    * [#3607](https://github.com/http4s/http4s/pull/3607): Add request key to the connection manager debug logs in blaze-client
    * [#3602](https://github.com/http4s/http4s/pull/3602): Support trailer headers in Ember.
    * [#3603](https://github.com/http4s/http4s/pull/3603): Enable connection reuse in ember-server.
    * [#3601](https://github.com/http4s/http4s/pull/3601): Improve ember-client by adding `keep-alive`, a `Date` header if not present, and a configurable `User-Agent` header if not present.

* Refactoring
    * [#3547](https://github.com/http4s/http4s/pull/3547): Refactor the ember request parser

* Documentation
    * [#3545](https://github.com/http4s/http4s/pull/3545): Refresh the getting started guide to match the current template.
    * [#3595](https://github.com/http4s/http4s/pull/3595): Show handling of `Year.of` exceptions in DSL tutorial

* Dependency upgrades
    * cats-effect-2.1.4
    * dropwizard-metrics-4.1.11
    * jetty-9.4.31.v20200723
    * okhttp-4.8.1
    * tomcat-9.0.37

# v1.0.0-M3 (2020-06-27)

This milestone merges the changes in 0.21.6.
It is binary compatible with 1.0.0-M2.

# v0.21.6 (2020-06-27)

* Bugfixes
    * [#3538](https://github.com/http4s/http4s/pull/3538): In ember, fix request and response parser to recognize chunked transfer encoding. In chunked messages, bodies were incorrectly empty.

* Enhancements
    * [#3492](https://github.com/http4s/http4s/pull/3538): Split the request extractors in the server DSL into `org.http4s.dsl.request`. This leaner DSL does not deal with bodies, and does not require an `F[_]` parameter. Use of the existing `http4s-dsl` is unaffected.

* Dependency updates
* blaze-0.14.13

# v1.0.0-M2 (2020-06-25)

This is the first milestone release in the 1.x series.
It is not binary compatible with prior releases.

* Where is M1?

Unpublished. The release build from the tag failed, and the fix required a new tag.

* Breaking changes
    * [#3174](https://github.com/http4s/http4s/pull/3174): Drop http4s-prometheus dependency on http4s-dsl
    * [#2615](https://github.com/http4s/http4s/pull/2615): Model the `Server` header
    * [#3206](https://github.com/http4s/http4s/pull/2615): Model the `Content-Location` header
    * [#3264](https://github.com/http4s/http4s/pull/3264): Remove unused `EntityEncoder` argument in `PlayInstances`.
    * [#3257](https://github.com/http4s/http4s/pull/3257): Make `SameSite` cookie attribute optional
    * [#3291](https://github.com/http4s/http4s/pull/3291): Remove unused `F[_]` parameter from `Server`
    * [#3241](https://github.com/http4s/http4s/pull/3241): Port all macros to blackbox in anticipation of Dotty support
    * [#3323](https://github.com/http4s/http4s/pull/3323): Drop deprecated `ArbitraryInstances#charsetRangesNoQuality`
    * [#3322](https://github.com/http4s/http4s/pull/3322): Drop deprecated `getAs` and `prepAs` methods from `Client`
    * [#3371](https://github.com/http4s/http4s/pull/3271): In http4s-metrics, add `rootCause` field to `TerminationType.Abnormal` and `TerminationType.Error`.  Add `TerminationType.Canceled`
    * [#3335](https://github.com/http4s/http4s/pull/3335): Remove unused `Bracket` instance in `Client#translate`
    * [#3390](https://github.com/http4s/http4s/pull/3390): Replace `org.http4s.util.CaseInsensitiveString` with `org.typelevel.ci.CIString`
    * [#3221](https://github.com/http4s/http4s/pull/3221): Implement a `Uri.Path` type to replace the type alias for `String`
    * [#3450](https://github.com/http4s/http4s/pull/3450): Model `Accept-Patch` header as a `NonEmptyList[MediaType]`
    * [#3463](https://github.com/http4s/http4s/pull/3450): Model `Access-Control-Allow-Credentials` header as a nullary case class.
    * [#3325](https://github.com/http4s/http4s/pull/3325): Add a WebSocket builder with a `Pipe[F, WebSocketFrame, WebSocketFrame]` to unify sending and receiving.
    * [#3373](https://github.com/http4s/http4s/pull/3373): Parameterize `ClassLoader` for `ResourceService` and `WebjarService`. Changes the `CacheStrategy`'s `uriPath` argument to `Uri.Path`.
    * [#3460](https://github.com/http4s/http4s/pull/3460): Remove deprecated `Service` and related aliases
    * [#3529](https://github.com/http4s/http4s/pull/3529): Refresh the `MediaType`s constants from the IANA registry. Not source breaking, but shifts constants in a binary breaking way.

* Enhancements
    * [#3320](https://github.com/http4s/http4s/pull/3320): Reimplement `Media#as` with `F.rethrow`

* Deprecations
    * [#3359](https://github.com/http4s/http4s/pull/3359): Deprecate the `org.http4s.util.execution` package.
    * [#3422](https://github.com/http4s/http4s/pull/3359): Deprecate `BlazeClientBuilder#withSslContextOption`.

* Documentation
    * [#3374](https://github.com/http4s/http4s/pull/3374): Add a deployment tutorial, including for GraalVM. See also #[3416](https://github.com/http4s/http4s/pull/3416).
    * [#3410](https://github.com/http4s/http4s/pull/3410): Suggest a global execution context for the argument to `BlazeClientBuilder`

* Internal refactoring
    * [#3386](https://github.com/http4s/http4s/pull/3386): Drop internal argonaut parser in favor of jawn's
    * [#3266](https://github.com/http4s/http4s/pull/3266): Replace `fs2.compress` with `fs2.compression`

* Dependency updates
    * argonaut-6.3.0
    * async-http-client-2.12.1
    * blaze-http-0.14.13
    * play-json-2.9.0
    * simpleclient-0.9.0 (Prometheus)

~~# v1.0.0-M1 (2020-06-25)~~

Did not publish successfully from tag.

# v0.21.5 (2020-06-24)

This release is fully backward compatible with 0.21.4.

* New modules
    * [#3372](https://github.com/http4s/http4s/pull/3372): `http4s-scalafix`: starting with this release, we have integrated Scalafix rules into the build.  All our Scalafix rules will be published as both snapshots and with core releases.  The http4s-scalafix version is equivalent to the output version of the scalafix rules.  The scalafix rules are intended to assist migrations with deprecations (within this series) and breaking changes (in the upcoming push to 1.0).

* Bugfixes
    * [#3476](https://github.com/http4s/http4s/pull/3476): Fix crash of `GZip` client middleware on responses to `HEAD` requests
    * [#3488](https://github.com/http4s/http4s/pull/3488): Don't call `toString` on input of `ResponseLogger` on cancellation. The input is usually a `Request`. We filter a set of default sensitive headers in `Request#toString`, but custom headers can also be sensitive and could previously be leaked by this middleware.
    * [#3521](https://github.com/http4s/http4s/pull/3521): In async-http-client, raise errors into response body stream when thrown after we've begun streaming. Previously, these errors were logged, but the response body was truncated with no value indicating failure.
    * [#3520](https://github.com/http4s/http4s/pull/3520): When adding a query parameter to a `Uri` with a blank query string (i.e., the URI ends in '?'), don't prepend it with a `'&'` character. This is important in OAuth1 signing.
    * [#3518](https://github.com/http4s/http4s/pull/3518): Fix `Cogen[ContentCoding]` in the testing arbitraries to respect the case-insensitivity of the coding field.
    * [#3501](https://github.com/http4s/http4s/pull/3501): Explicitly use `Locale.ENGLISH` when comparing two `ContentCoding`'s coding fields. This only matters if your default locale has different casing semantics than English for HTTP token characters.

* Deprecations
    * [#3441](https://github.com/http4s/http4s/pull/3441): Deprecate `org.http4s.util.threads`, which is not related to HTTP
    * [#3442](https://github.com/http4s/http4s/pull/3442): Deprecate `org.http4s.util.hashLower`, which is not related to HTTP
    * [#3466](https://github.com/http4s/http4s/pull/3466): Deprecate `util.decode`, which may loop infinitely on certain malformed input.  Deprecate `Media#bodyAsText` and `EntityDecoder.decodeString`, which may loop infinitely for charsets other than UTF-8.  The latter two methods are replaced by `Media#bodyText` and `EntityDecoder.decodeText`.
    * [#3372](https://github.com/http4s/http4s/pull/3372): Deprecate `Client.fetch(request)(f)` in favor of `Client#run(request).use(f)`. This is to highlight the dangers of using `F.pure` or similar as `f`, which gives access to the body after the client may have recycled the connection.  For training and code reviewing purposes, it's easier to be careful with `Resource#use` than convenience methods like `fetch` that are `use` in disguise. This change can be fixed with our new http4s-scalafix.

* Enhancements
    * [#3286](https://github.com/http4s/http4s/pull/3286): Add `httpRoutes` constructor for `Autoslash middleware`
    * [#3382](https://github.com/http4s/http4s/pull/3382): Use more efficient String compiler in `EntityDecoder[F, String]`
    * [#3439](https://github.com/http4s/http4s/pull/3439): Add `Hash[Method]` instance. See also [#3490](https://github.com/http4s/http4s/pull/3490).
    * [#3438](https://github.com/http4s/http4s/pull/3438): Add `PRI` method
    * [#3474](https://github.com/http4s/http4s/pull/3474): Add `httpApp` and `httpRoutes` constructors for `HeaderEcho` middleware
    * [#3473](https://github.com/http4s/http4s/pull/3473): Add `httpApp` and `httpRoutes` constructors for `ErrorHandling` middleware
    * [#3472](https://github.com/http4s/http4s/pull/3472): Add `httpApp` and `httpRoutes` constructors for `EntityLimiter` middleware
    * [#3487](https://github.com/http4s/http4s/pull/3487): Add new `RequestID` middleware.
    * [#3515](https://github.com/http4s/http4s/pull/3472): Add `httpApp` and `httpRoutes` constructors for `ErrorAction` middleware
    * [#3513](https://github.com/http4s/http4s/pull/3513): Add `httpRoutes` constructor for `DefaultHead`. Note that `httpApp` is not relevant.
    * [#3497](https://github.com/http4s/http4s/pull/3497): Add `logBodyText` functions to `Logger` middleware to customize the logging of the bodies

* Documentation
    * [#3358](https://github.com/http4s/http4s/pull/3358): Replaced tut with mdoc
    * [#3421](https://github.com/http4s/http4s/pull/3421): New deployment tutorial, including GraalVM
    * [#3404](https://github.com/http4s/http4s/pull/3404): Drop reference to http4s-argonaut61, which is unsupported.
    * [#3465](https://github.com/http4s/http4s/pull/3465): Update sbt version used in `sbt new` command
    * [#3489](https://github.com/http4s/http4s/pull/3489): Remove obsolete scaladoc about `Canceled` in blaze internals

* Internals
    * [#3478](https://github.com/http4s/http4s/pull/3478): Refactor `logMessage` in client and server logging middlewares

* Dependency updates
    * scala-2.13.2
    * boopickle-1.3.3
    * fs2-2.4.2
    * metrics-4.1.9 (Dropwizard)
    * jetty-9.4.30
    * json4s-3.6.9
    * log4cats-1.1.1
    * okhttp-4.7.2
    * scalafix-0.9.17
    * scalatags-0.9.1
    * tomcat-9.0.36

# v0.21.4 (2020-04-28)

This release is fully backward compatible with 0.21.3.

* Bugfixes
    * [#3338](https://github.com/http4s/http4s/pull/3338): Avoid incorrectly responding with an empty body in http4s-async-http-client

* Enhancements
    * [#3303](https://github.com/http4s/http4s/pull/3303): In blaze, cache `Date` header value
    * [#3350](https://github.com/http4s/http4s/pull/3350): Use stable host address in `ConnectionFailure` message. Makes code more portable post-JDK11.

* Deprecation
    * [#3361](https://github.com/http4s/http4s/pull/3361): Deprecate the `org.http4s.util.execution` package.

* Documentation
    * [#3279](https://github.com/http4s/http4s/pull/3279): Improve Prometheus middleware usage example

* Dependency updates
    * fs2-2.3.0
    * okhttp-4.5.0
    * scalafix-0.9.12
    * scala-xml-1.3.0
    * specs2-4.9.3

# v0.20.23 (2020-04-28)

This release restores backward compatibility with the 0.20 series.
This is the final planned release in the 0.20 series.

* Compatibility
    * [#3362](https://github.com/http4s/http4s/pull/3362): Restores binary compatibility in http4s-jetty back to 0.20.21.

# v0.20.22 (2020-04-28)

This release is backward compatible with 0.20, except for http4s-jetty.
This incompatibility will be corrected in 0.20.23.

* Breaking changes
    * [#3333](https://github.com/http4s/http4s/pull/3333): Add Http2c support to jetty-server. This accidentally broke binary compatibility, and will be patched in v0.20.23.

* Bugfixes
    * [#3326](https://github.com/http4s/http4s/pull/3326): In `WebjarService`, do not use OS-specific directory separators
    * [#3331](https://github.com/http4s/http4s/pull/3326): In `FileService`, serve index.html if request points to directory

* Enhancements
    * [#3327](https://github.com/http4s/http4s/pull/3327): Add `httpRoutes` and `httpApp` convenience constructors to `Date` middleware
    * [#3381](https://github.com/http4s/http4s/pull/3327): Add `httpRoutes` and `httpApp` convenience constructors to `CORS` middleware
    * [#3298](https://github.com/http4s/http4s/pull/3298): In `Logger` client and server middlewares, detect any media types ending in `+json` as non-binary

* Deprecations
    * [#3330](https://github.com/http4s/http4s/pull/3330): Deprecate `BlazeServerBuilder#apply()` in favor of passing an `ExecutionContext` explicitly.  Formerly, `ExecutionContext.global` was referenced by the default builder, and would spin up its thread pool even if the app never used the global execution context.
    * [#3361](https://github.com/http4s/http4s/pull/3361): Deprecate `org.http4s.util.bug`, which is for internal use only.

* Backports

    These appeared in previous releases, but have been backported to 0.20.x

    * [#2591](https://github.com/http4s/http4s/pull/2591): Change literal interpolator macros to use unsafe methods to avoid triggering Wartremover's EitherProjectionPartial warning
    * [#3115](https://github.com/http4s/http4s/pull/3115): Drop UTF-8 BOM when decoding
    * [#3148](https://github.com/http4s/http4s/pull/3148): Add `HttpRoutes.strict`
    * [#3185](https://github.com/http4s/http4s/pull/3185): In blaze, recover `EOF` on `bodyEncoder.write` to close connection
    * [#3196](https://github.com/http4s/http4s/pull/3196): Add convenience functions to `Caching` middleware

* Build improvements
    * Start testing on JDK14

* Dependency updates
    * blaze-0.14.12
    * metrics-4.1.6
    * jetty-9.4.28.v20200408
    * scala-2.12.11
    * tomcat-9.0.34

# v0.21.3 (2020-04-02)

This release is fully backward compatible with 0.21.2.

* Bugfixes
    * [#3243](https://github.com/http4s/http4s/pull/3243): Write ember-client request to socket before reading response

* Enhancements
    * [#3196](https://github.com/http4s/http4s/pull/3196): Add convenience functions to `Caching` middleware.
    * [#3155](https://github.com/http4s/http4s/pull/3155): Internal `j.u.c.CompletionStage` conversions.

* Dependency updates
    * cats-2.1.1
    * okhttp-4.4.1

# v0.20.21 (2020-04-02)

This release is fully backward compatible with 0.20.20.

* Dependency updates
    * argonaut-6.2.5
    * jetty-9.4.27.v20200227
    * metrics-4.1.5 (Dropwizard)
    * tomcat-9.0.33

# v0.21.2 (2020-03-24)

This release is fully backward compatible with 0.21.1.

* Security fixes
    * [GHSA-66q9-f7ff-mmx6](https://github.com/http4s/http4s/security/advisories/GHSA-66q9-f7ff-mmx6): Fixes a local file inclusion vulnerability in `FileService`, `ResourceService`, and `WebjarService`.
    * Request paths with `.`, `..`, or empty segments will now return a 400 in all three services.  Combinations of these could formerly be used to escape the configured roots and expose arbitrary local resources.
    * Request path segments are now percent-decoded to support resources with reserved characters in the name.

* Bug fixes
    * [#3261](https://github.com/http4s/http4s/pull/3261): In async-http-client, fixed connection release when body isn't run, as well as thread affinity.

* Enhancements
    * [#3253](https://github.com/http4s/http4s/pull/3253): Preparation for Dotty support. Should be invisible to end users, but calling out because it touches a lot.

# v0.20.20 (2020-03-24)

This release is fully backward compatible with 0.20.19.

* Security fixes
    * [GHSA-66q9-f7ff-mmx6](https://github.com/http4s/http4s/security/advisories/GHSA-66q9-f7ff-mmx6): Fixes a local file inclusion vulnerability in `FileService`, `ResourceService`, and `WebjarService`.
    * Request paths with `.`, `..`, or empty segments will now return a 400 in all three services.  Combinations of these could formerly be used to escape the configured roots and expose arbitrary local resources.
    * Request path segments are now percent-decoded to support resources with reserved characters in the name.

* Enhancements
    * [#3167](https://github.com/http4s/http4s/pull/3167): Add `MetricsOps.classifierFMethodWithOptionallyExcludedPath`.name.

# v0.18.26 (2020-03-24)

This release is fully backward compatible with 0.18.25.

* Security fixes
    * [GHSA-66q9-f7ff-mmx6](https://github.com/http4s/http4s/security/advisories/GHSA-66q9-f7ff-mmx6): Fixes a local file inclusion vulnerability in `FileService`, `ResourceService`, and `WebjarService`.
    * Request paths with `.`, `..`, or empty segments will now return a 400 in all three services.  Combinations of these could formerly be used to escape the configured roots and expose arbitrary local resources.
    * Request path segments are now percent-decoded to support resources with reserved characters in the name.

# v0.21.1 (2020-02-13)

This release is fully backward compatible with v0.21.0, and includes all the changes from v0.20.18.

* Bug fixes
    * [#3192](https://github.com/http4s/http4s/pull/3192): Parse `SameSite` cookie attribute and values case insensitively.

* Enhancements
    * [#3185](https://github.com/http4s/http4s/pull/3185): In blaze-server, recover `EOF` to close the connection instead of catching it. This reduces log noise in Cats Effect implementations that wrap uncaught exceptions.

* Dependency updates
    * jawn-fs2-1.0.0: We accidentally released v0.21.0 against an RC of jawn-fs2. This is fully compatible.

# v0.20.19 (2020-02-13)

This release is fully backward compatible with 0.20.18.

* Bugfixes
    * [#3199](https://github.com/http4s/http4s/pull/3199): When `Uri#withPath` is called without a slash and an authority is defined, add a slash to separate them.

* Enhancements
    * [#3199](https://github.com/http4s/http4s/pull/3199):
    * New `addSegment` alias for `Uri#/`
    * New `Uri#addPath` function, which splits the path segments and adds each, URL-encoded.

# v0.20.18 (2020-02-13)

This release is fully backward compatible with 0.20.17.

* Bugfixes
    * [#3178](https://github.com/http4s/http4s/pull/3178): In `TomcatBuilder`, use the correct values for the `clientAuth` connector attribute.
    * [#3184](https://github.com/http4s/http4s/pull/3184):
    * Parse cookie attribute names case insensitively.
    * Preserve multiple extended cookie attributes, delimited by a `';'`
    * Support cookie domains with a leading `'.'`

* Enhancements
    * [#3190](https://github.com/http4s/http4s/pull/3190): Remove reflection from initialization of `HttpHeaderParser`. This allows modeled headers to be parsed when running on Graal. The change is fully transparent on the JVM.

* Dependency updates
    * argonaut-6.2.4
    * async-http-client-2.10.5
    * tomcat-9.0.31

# v0.21.0 (2020-02-09)

This release is fully compatible with 0.21.0-RC4.  Future releases in the 0.21.x series will maintain binary compatibility with this release.  All users on the 0.20.x or earlier are strongly encouraged to upgrade.

* Dependency updates
* argonaut-6.2.4
* circe-0.13.0

# v0.21.0-RC5 (2020-02-08)

This release is binary compatible with 0.21.0-RC4.

We announced this as built on circe-0.13.0.  That was not correct, but is fixed in 0.21.0.

* Enhancements
    * [#3148](https://github.com/http4s/http4s/pull/3148): Add `HttpRoutes.strict` and `ContextRoutes.strict` for routes that require only an `Applicative`, at the cost of evaluating `combineK`ed routes strictly.

* Dependency updates
    * async-http-client-2.10.5
    * cats-effect-2.1.1
    * scalatags-0.8.5

# v0.21.0-RC4 (2020-02-04)

This release is binary incompatible with 0.21.0-RC2, but is source compatible.

* Breaking changes
    * Binary
        * [#3145](https://github.com/http4s/http4s/pull/3145): Relax constraints from `Effect` to `Sync` in `resourceService`, `fileService`, and `webjarService`.

# v0.21.0-RC3 (2020-02-03)

This release is binary incompatible with 0.21.0-RC2, but should be source compatible, with deprecations.

* Breaking changes
    * Binary
        * [#3126](https://github.com/http4s/http4s/pull/3126): Remove unnecessary `Applicative` constraints from http4s-circe
        * [#3124](https://github.com/http4s/http4s/pull/3124): Relax constraints from `Effect` to `Sync` in `FileService`.
        * [#3136](https://github.com/http4s/http4s/pull/3136): In `WebSocketBuilder`, add `filterPingPongs` parameter, default true.  When false, `send` and `receive` will see pings and pongs sent by the client.  The server still responds automatically to pings.  This change should be transparent to existing users.
        * [#3138](https://github.com/http4s/http4s/pull/3124): Remove unnecessary `Applicative` constraints on `EntityEncoder` instances in several modules.
    * Semantic
        * [#3139](https://github.com/http4s/http4s/pull/3139): Changes `Router` to find the longest matching prefix by path segments rather than character-by-character.  This is arguably a bug fix.  The old behavior could cause unexpected matches, is inconsistent with the servlet mappings that inspired `Router`, and is unlikely to have been intentionally depended on.
    * Deprecation
        * [#3134](https://github.com/http4s/http4s/pull/3132): Deprecate `JettyBuilder#withSSLContext` in favor of new methods in favor of new `withSslContext*` methods.
        * [#3132](https://github.com/http4s/http4s/pull/3132): Deprecate `BlazeServerBuilder#withSSLContext` and `BlazeServerBuilder#withSSL` in favor of new `withSslContext*` methods.
        * [#3140](https://github.com/http4s/http4s/pull/3140): Deprecate `JettyBuilder#withSSL`, to match `BlazeServerBuilder`. It's still necessary in Tomcat, which doesn't take a `ServletContext`.  Deprecate `SSLConfig`, `KeyStoreBits`, and `SSLContextBits`, which had already been removed from public API.

* Bugfixes
    * [#3140](https://github.com/http4s/http4s/pull/3140): In `TomcatBuilder`, fix mapping of `SSLClientAuthMode` to Tomcat's connector API.

* Enhancements
    * [#3134](https://github.com/http4s/http4s/pull/3132): In `JettyBuilder`, add `withSslContext` and `withSslContextAndParameters` to permit full control of `SSLParameters`.  Add `withoutSsl`.
    * [#3132](https://github.com/http4s/http4s/pull/3132): In `BlazeBuilder`, add `withSslContext` and `withSslContextAndParameters` to permit full control of `SSLParameters`.  Add `withoutSsl`.

* Dependency updates
    * cats-effect-2.1.0
    * fs2-2.2.2

# v0.21.0-RC2 (2020-01-27)

* Breaking changes
    * Binary and source
        * [#3110](https://github.com/http4s/http4s/pull/3110): Change `MessageFailure#toHttpResponse` to return a `Response[F]` instead of an `F[Response[F]]`, and relax constraints accordingly. Drops the `inHttpResponse` method.
        * [#3107](https://github.com/http4s/http4s/pull/3107): Add `covary[F[_]]` method to `Media` types.  Should not break your source unless you have your own `Media` subclass, which you shouldn't.
    * Binary only
        * [#3098](https://github.com/http4s/http4s/pull/3098): Update `MimeDB` from IANA registry.
    * Deprecation
        * [#3087](https://github.com/http4s/http4s/pull/3087): Deprecate the public http4s-testing module.  This was mostly Specs2 matchers, the majority of which block threads.  This is not to be confused with http4s-laws, which depends only on Discipline and is still maintained.

* Bugfixes
    * [#3105](https://github.com/http4s/http4s/pull/3105): Fix "cannot have more than one pending write request" error in blaze-server web sockets.
    * [#3115](https://github.com/http4s/http4s/pull/3115): Handle BOM at the head of a chunk in `decode`.

* Enhancements
    * [#3106](https://github.com/http4s/http4s/pull/3106): Interrupt response body in `DefaultHead` middleware. This optimization saves us from draining a potentially large response body that, because `HEAD` is a safe method, should not have side effects.
    * [#3095](https://github.com/http4s/http4s/pull/3095): Add `Request#asCurl` method to render a request as a curl command.  Renders the method, URI, and headers, but not yet the body.

# v0.20.17 (2020-01-25)

This release is fully compatible with 0.20.16.

* Bugfixes
    * [#3105](https://github.com/http4s/http4s/pull/3105): Fix "cannot have more than one pending write request" error in blaze-server web sockets.

* Dependency updates
    * simpleclient-0.8.1 (Prometheus)

# v0.18.25 (2020-01-21)

* Bug fixes
    * [#3093](https://github.com/http4s/http4s/pull/3093): Backport [#3086](https://github.com/http4s/http4s/pull/3086): Fix connection leak in blaze-client pool manager when the next request in the queue is expired.

# v0.21.0-RC1 (2020-01-21)

* Breaking changes
    * [#3012](https://github.com/http4s/http4s/pull/3012): Use `HttpApp` instead of `HttpRoutes` in `Http4sServlet`. The servlet builders themselves retain compatibility.
    * [#3078](https://github.com/http4s/http4s/pull/3078): Wrap Java exceptions in `ConnectionFailure` when a blaze-client fails to establish a connection. This preserves information about which host could not be connected to.
    * [#3062](https://github.com/http4s/http4s/pull/3062): http4s' JSON support is now built on jawn-1.0.0, which is a binary break from jawn-0.14.x.  This comes with a bump to circe-0.13.  Most circe-0.13 modules are binary compatible with circe-0.12, but note that circe-parser is not.
    * [#3055](https://github.com/http4s/http4s/pull/3055): Add fs2-io's TLS support to ember-client.  The `sslContext: Option[(ExecutionContext, SSLContext)]` argument is replaced by a `tlsContext: Option[TLSContext]`.`

* Enhancements
    * [#3004](https://github.com/http4s/http4s/pull/3004): Add `classloader` argument to `StaticFile.fromResource`
    * [#3007](https://github.com/http4s/http4s/pull/3007): Add `classloader` argument to `TomcatBuilder`
    * [#3008](https://github.com/http4s/http4s/pull/3008): Consistently use `collection.Seq` across Scala versions in DSL
    * [#3031](https://github.com/http4s/http4s/pull/3031): Relax `Router.apply` constraint from `Sync` to `Monad`
    * [#2821](https://github.com/http4s/http4s/pull/2821): Add `Media` supertype of `Message` and `Part`, so multipart parts can use `EntityDecoder`s
    * [#3021](https://github.com/http4s/http4s/pull/3021): Relax `Throttle.apply` constraint from `Sync` to `Monad`. Add a `mapK` operation to `TokenBucket`.
    * [#3056](https://github.com/http4s/http4s/pull/3056): Add `streamJsonArrayEncoder*` operations to circe support, to encode a `Stream` of `A` to a JSON array, given an encoder for `A`.
    * [#3053](https://github.com/http4s/http4s/pull/3053): Remove unneeded `Functor[G]` constraint on `HeaderEcho.apply`.
    * [#3054](https://github.com/http4s/http4s/pull/3054): Add `SameSite` cookie support
    * [#2518](https://github.com/http4s/http4s/pull/2518): Add `status` methods to `Client` that take a `String` or `Uri`
    * [#3069](https://github.com/http4s/http4s/pull/3069): Add `ContextMiddleware.const` function
    * [#3070](https://github.com/http4s/http4s/pull/3070): Add `NonEmptyTraverse` instance to `ContextRequest`
    * [#3060](https://github.com/http4s/http4s/pull/3060): Stop mixing context bounds and implicits in `CirceInstances`.
    * [#3024](https://github.com/http4s/http4s/pull/3024): Add `withQueryParams` and `withMultiValueQueryParams` to `QueryOps`
    * [#3092](https://github.com/http4s/http4s/pull/3092): Add TLS support to ember-server via fs2-io.

* Dependency updates
    * cats-2.1.0
    * circe-0.13.0-RC1
    * fs2-2.2.0
    * jawn-1.0.0
    * jawn-fs2-1.0.0-RC2
    * okhttp-4.3.1
    * play-json-2.8.1
    * scalacheck-1.14.3
    * scalatags-0.8.4
    * specs2-4.8.3

# v0.20.16 (2020-01-21)

* Bugfixes
    * [#3086](https://github.com/http4s/http4s/pull/3086): Fix connection leak in blaze-client pool manager when the next request in the queue is expired.

* Breaking changes
    * [#3053](https://github.com/http4s/http4s/pull/3053): Deprecate `HttpDate.now`, which is not referentially transparent. Prefer `HttpDate.current`.

* Enhancements
    * [#3049](https://github.com/http4s/http4s/pull/3049): Add new `Date` server middleware
    * [#3051](https://github.com/http4s/http4s/pull/3051): Add `HttpDate.current` convenience constructor, based on `Clock`.
    * [#3052](https://github.com/http4s/http4s/pull/3052): Add `Caching` server middleware.
    * [#3065](https://github.com/http4s/http4s/pull/3065): Add `ErrorAction` server middleware
    * [#3082](https://github.com/http4s/http4s/pull/3082): Wrap `UnresolvedAddressException` in blaze in an `UnresolvedAddressException` subtype that contains the address that could not resolve to aid diagnostics.  This is a conservative change.  See [#3078](https://github.com/http4s/http4s/pull/3078) for the wrapper forthcoming in http4s-0.21.

* Documentation
    * [#3017](https://github.com/http4s/http4s/pull/3017): Correct the documentation in `Timeout.apply`
    * [#3020](https://github.com/http4s/http4s/pull/3020): Update scaladoc to compiling example code on OptionalMultiQueryParamDecoderMatcher

* Dependency updates
    * async-http-client-2.10.4
    * jetty-9.4.26.v20200117
    * metrics-4.1.2 (Dropwizard)
    * log4s-1.8.2
    * okhttp-3.14.6
    * simpleclient-0.8.0 (Prometheus)
    * tomcat-9.0.30

# v0.20.15 (2019-11-27)

* Enhancements
    * [#2966](https://github.com/http4s/http4s/pull/2966): Add `HttpsRedirect` middleware
    * [#2965](https://github.com/http4s/http4s/pull/2965): Add `Request#addCookies` method
    * [#2887](https://github.com/http4s/http4s/pull/2887): Support realm in the `OAuth1` header

* Bug fixes
    * [#2916](https://github.com/http4s/http4s/pull/2916): Ensure that `Metrics` only decrements active requests once
    * [#2889](https://github.com/http4s/http4s/pull/2889): In `Logger`, log the prelude if `logBody` and `logHeaders` are false

# v0.20.14 (2019-11-26)

* Bug fixes
    * [#2909](https://github.com/http4s/http4s/pull/2909): Properly propagate streamed errors in jetty-client
    * The blaze upgrade fixes the "SSL Handshake WRAP produced 0 bytes" error on JDK 11.

* Enhancements
    * [#2911](https://github.com/http4s/http4s/pull/2911): Add missing bincompat syntax to `org.http4s.implicits`.

* Dependency updates
    * blaze-0.14.11
    * circe-0.11.2
    * jawn-0.14.3
    * jetty-9.4.24.v20191120
    * tomcat-9.0.29

# v0.20.13 (2019-11-05)

* Bug fixes
    * [#2946](https://github.com/http4s/http4s/pull/2946): Restore binary compatibility of private `UrlCodingUtils`. [#2930](https://github.com/http4s/http4s/pull/2930) caused a breakage in rho.
    * [#2922](https://github.com/http4s/http4s/pull/2922): Handle Content-Length longer that Int.MaxValue in chunked uploads
    * [#2941](https://github.com/http4s/http4s/pull/2941): Fix for `BlockingHttp4sServlet` with shifted IO.
    * [#2953](https://github.com/http4s/http4s/pull/2953): Fix connection info in servlet backend.  The local and remote addresses were reversed.
    * [#2942](https://github.com/http4s/http4s/pull/2942): Fix `Request.addcookie` to consolidate all `Cookie` headers into one.
    * [#2957](https://github.com/http4s/http4s/pull/2957): Shift the write to Blocker in `BlockingServletIo`

* Enhancements
    * [#2948](https://github.com/http4s/http4s/pull/2948): Add all missing `ContentCoding`s from the IANA registry.

* Dependency updates
    * blaze-0.14.9

# v0.20.12 (2019-10-31)

* Enhancements
    * [#2930](https://github.com/http4s/http4s/pull/2830): Move private `UrlCodingUtils` to the `Uri` companion object, make public

* Dependency updates
    * jawn-0.14.2
    * jetty-9.4.22
    * json4s-0.14.2
    * metrics-4.1.1
    * okhttp-3.14.4
    * play-json-2.7.4
    * tomcat-9.0.27
    * twirl-1.4.2

# v0.21.0-M5 (2019-09-19)

* Breaking changes
    * [#2815](https://github.com/http4s/http4s/pull/2815): Allow `Allow` header to specify an empty set of methods.
    * [#2832](https://github.com/http4s/http4s/pull/2836): Add natural transformation to `ResponseGenerator` to allow the `F` and `G` to work in unison. Relevant for http4s-directives.

* Enhancements
    * [#2836](https://github.com/http4s/http4s/pull/2836): Add `additionalSocketOptions` to ember configs
    * [#2869](https://github.com/http4s/http4s/pull/2869): Add JsonDebugErrorHandler middleware
    * [#2830](https://github.com/http4s/http4s/pull/2830): Add encoder and decoder helpers to `Uri` companion

* Documentation
    * [#2733](https://github.com/http4s/http4s/pull/2733): Add CSRF documentation

* Dependency updates
    * async-http-client-2.10.2
    * cats-2.0.0
    * cats-effect-2.0.0
    * circe-0.12.1
    * fs2-2.0.0
    * keypool-2.0.0
    * log4cats-core-1.0.0
    * okhttp-4.2.0
    * jawn-fs2-0.15.0
    * tomcat-9.0.24
    * vault-2.0.0

# v0.20.11 (2019-09-19)

* Breaking changes
    * [#2792](https://github.com/http4s/http4s/pull/2792): Drop support for Scala 2.13.0-M5. Users of Scala 2.13 should be on a stable release of Scala on the http4s-0.21 release series.
    * [#2800](https://github.com/http4s/http4s/pull/2800): Revert [#2785](https://github.com/http4s/http4s/pull/2785), using `F[A]` instead of `G[A]` in `EntityResponseGenerator`, which broke directives.

* Bug fixes
    * [#2807](https://github.com/http4s/http4s/pull/2807): In jetty-client, don't follow redirects with the internal client, which throws an exception in the http4s wrapper.

* Enhancements
    * [#2817](https://github.com/http4s/http4s/pull/2817): In jetty-client, disable internal client's default `Content-Type` to prevent default `application/octet-stream` for empty bodies.

* Dependency updates
    * jetty-9.4.20

# v0.21.0-M4 (2019-08-14)

* Dependency updates
    * cats-core-2.0.0-RC1
    * cats-effect-2.0.0-RC1
    * circe-0.12.0-RC1
    * discipline-1.0.0
    * keypool-0.2.0-RC1
    * log4cats-1.0.0-RC1
    * vault-2.0.0-RC1

# v0.20.10 (2019-08-14)

* Breaking changes
    * [#2785](https://github.com/http4s/http4s/pull/2785): Use `F[A]` instead of `G[A]` in the DSL's `EntityResponseGenerator`. This change is binary compatible, but not source compatible for users of `Http4sDsl2` where `F` is not `G`. This is uncommon.

* Bug fixes
    * [#2778](https://github.com/http4s/http4s/pull/2778): Don't truncate signing keys in CSRF middleware to 20 bytes, which causes a loss of entropy.

* Enhancements
    * [#2776](https://github.com/http4s/http4s/pull/2776): Add `MaxActiveRequest` middleware
    * [#2724](https://github.com/http4s/http4s/pull/2724): Add `QueryParamEncoder[Instant]` and `QueryParamDecoder[Instant]`. Introduce `QueryParamCodec` for convenience.
    * [#2777](https://github.com/http4s/http4s/pull/2777): Handle invalid `Content-Range` requests with a 416 response and `Accept-Range` header.

# v0.20.9 (2019-08-07)

* Bug fixes
    * [#2761](https://github.com/http4s/http4s/pull/2761): In blaze-client, don't add `ResponseHeaderTimeoutStage` when `responseHeaderTimeout` is infinite. This prevents an `IllegalArgumentException` when debug logging is turned on.
    * [#2762](https://github.com/http4s/http4s/pull/2762): Fix text in warnings when blaze-client timeouts are questionably ordered.

# v0.21.0-M3 (2019-08-02)

* Breaking changes
    * [#2572](https://github.com/http4s/http4s/pull/2572): Make `Http1Stage` private to `org.http4s`, which we highly doubt anybody extended directly anyway.

* Bug fixes
    * [#2727](https://github.com/http4s/http4s/pull/2727): Fix `UserInfo` with `+` sign

* Enhancements
    * [#2623](https://github.com/http4s/http4s/pull/2623): Propagate cookies in `FollowRedirect` client middleware

* Documentation
    * [#2717](https://github.com/http4s/http4s/pull/2717): Update quickstart for v0.21
    * [#2734](https://github.com/http4s/http4s/pull/2734): Add missing comma in code sample
    * [#2740](https://github.com/http4s/http4s/pull/2740): Clarify `Method` imports for client DSL

* Internals
    * [#2747](https://github.com/http4s/http4s/pull/2717): Create .mergify.yml

* Dependency upgrades
    * better-monadic-for-0.3.1
    * cats-effect-2.0.0-M5
    * log4cats-0.4.0-M2
    * okhttp-4.0.1

# v0.20.8 (2019-08-02)

* Enhancements
    * [#2550](https://github.com/http4s/http4s/pull/2550): Adjust default timeouts and add warnings about misconfiguration

* Dependency updates
    * blaze-0.14.8
    * cats-effect-1.4.0

# v0.20.7 (2019-07-30)

* Bug fixes
    * [#2728](https://github.com/http4s/http4s/pull/2728): Preserve division of `request.uri.path` into `scriptName` and `pathInfo` when calling `withPathInfo`.
    * [#2737](https://github.com/http4s/http4s/pull/2737): Fix deadlock in blaze-server web socket shutdown.

* Enhancements
    * [#2736](https://github.com/http4s/http4s/pull/2736): Implement a `connectTimeout` in blaze-client, defaulted to 10 seconds.  Prevents indefinite hangs on non-responsive hosts.

* Documentation
    * [#2741](https://github.com/http4s/http4s/pull/2741): Improve docs surrounding auth middleware and fall through.

* Dependency upgrades
    * blaze-0.14.7
    * tomcat-9.0.22

# v0.21.0-M2 (2019-07-09)

This release drops support for Scala 2.11 and adds the `http4s-ember-server` and `http4s-ember-client` backends.  Ember is new and experimental, but we intend for it to become the reference implementation.  Notably, it only requires a `Concurrent` constraint.

* Bugfixes
    * [#2691](https://github.com/http4s/http4s/pull/2691): Fix deadlock in client by releasing current connection before retrying in `Retry` client middleware.  The constraint is upgraded to `Concurrent`.
    * [#2693](https://github.com/http4s/http4s/pull/2693): Fix deadlock in client by releasing current connection before retrying in `FollowRedirect` client middleware.  The constraint is upgraded to `Concurrent`.
    * [#2671](https://github.com/http4s/http4s/pull/2671): Upgrade `Uri.UserInfo` to a case class with username and password, fixing encoding issues. This is for RFC 3986 compliance, where it's deprecated for security reasons. Please don't use this.
    * [#2704](https://github.com/http4s/http4s/pull/2704): Remove unused `Sync` constraint on `Part.formData`.

* Breaking changes
    * [#2654](https://github.com/http4s/http4s/pull/2654): Extract an http4s-laws module from http4s-testing, with no dependency on Specs2.  The arbitraries, laws, and tests are now laid out in a similar structure to cats and cats-effect.
    * [#2665](https://github.com/http4s/http4s/pull/2665): Change `withBlock` to `withBlocker` in `OkHttpBuilder`
    * [#2661](https://github.com/http4s/http4s/pull/2661): Move string contexts macros for literals from `org.http4s` to `org.http4s.implicits`
    * [#2679](https://github.com/http4s/http4s/pull/2679): Replace `Uri.IPv4` with `Uri.Ipv4Address`, including an `ipv4` interpolator and interop with `Inet4Address`.
    * [#2694](https://github.com/http4s/http4s/pull/2694): Drop Scala 2.11 support
    * [#2700](https://github.com/http4s/http4s/pull/2700): Replace `Uri.IPv6` with `Uri.Ipv6Address`, including an `ipv6` interpolator and interop with `Inet6Address`.

* Enhancements
    * [#2656](https://github.com/http4s/http4s/pull/2656): Add `emap` and `emapValidatedNel` to `QueryParamDecoder`
    * [#2696](https://github.com/http4s/http4s/pull/2696): Introduce `http4s-ember-server` and `http4s-ember-client`

* Documentation
    * [#2658](https://github.com/http4s/http4s/pull/2658): Link to http4s-jdk-http-client
    * [#2668](https://github.com/http4s/http4s/pull/2668): Clarify scaladoc for `Uri.Scheme`

* Internal
    * [#2655](https://github.com/http4s/http4s/pull/2655): Tune JVM options for throughput

* Dependency updates
    * async-http-client-2.10.1
    * circe-0.12.0-M4
    * json4s-3.6.7
    * okhttp-4.0.0
    * specs2-core-4.6.0

# v0.20.6 (2019-07-09)

* Bug fixes
    * [#2705](https://github.com/http4s/http4s/pull/2705): Upgrades blaze to close `SSLEngine` when an `SSLStage` shuts down. This is useful in certain `SSLContext` implementations.  See [blaze#305](https://github.com/http4s/blaze/pull/305) for more.

* Dependency upgrades
    * blaze-0.14.6

~~# v0.20.5 (2019-07-09)~~

Cursed release. Sonatype staging repo closed in flight.

# v0.20.4 (2019-07-06)

* Bug fixes
    * [#2687](https://github.com/http4s/http4s/pull/2687): Don't throw in `Uri.fromString` on invalid ports
    * [#2695](https://github.com/http4s/http4s/pull/2695): Handle EOF in blaze-server web socket by shutting down stage

* Enhancements
    * [#2673](https://github.com/http4s/http4s/pull/2673): Add `GZip` middleware for client

* Documentation
    * [#2668](https://github.com/http4s/http4s/pull/2668): Clarifications in `Uri.Scheme` scaladoc

* Dependency upgrades
    - blaze-0.14.5
    - jetty-9.14.19.v20190610 (for client)

# v0.21.0-M1 (2019-06-17)

* Breaking changes
    * [#2565](https://github.com/http4s/http4s/pull/2565): Change constraint on server `Metrics` from `Effect` to `Sync`
    * [#2551](https://github.com/http4s/http4s/pull/2551): Refactor `AuthMiddleware` to not require `Choice` constraint
    * [#2614](https://github.com/http4s/http4s/pull/2614): Relax various `ResponseGenerator` constraints from `Monad` to `Applicative` in http4s-dsl.
    * [#2613](https://github.com/http4s/http4s/pull/2613): Rename implicit `http4sKleisliResponseSyntax` and its parameter name.
    * [#2624](https://github.com/http4s/http4s/pull/2624): In `BlazeServerBuilder`, don't depend on laziness of `SSLContext`. `None` now disables the secure context. The default argument tries to load `Some(SSLContext.getDefault())`, but falls back to `None` in case of failure.
    * [#2493](https://github.com/http4s/http4s/pull/2493): Scala 2.13 support and related upgrades
    * Scala 2.13.0-M5 is dropped.
    * All modules are supported on 2.11, 2.12, and 2.13 again.
    * Use cats-effect-2.0's new `Blocker` in place of `ExecutionContext` where appropriate

* Enhancements
    * [#2591](https://github.com/http4s/http4s/pull/2590): Add `MediaType.unsafeParse` and `QValue.unsafeFromString`.
    * [#2548](https://github.com/http4s/http4s/pull/2548): Add `Client#translate`
    * [#2622](https://github.com/http4s/http4s/pull/2622): Add `Header#renderedLength`

* Docs
    * [#2569](https://github.com/http4s/http4s/pull/2569): Fix typo in CORS scaladoc
    * [#2608](https://github.com/http4s/http4s/pull/2608): Replace `Uri.uri` with `uri` in tuts
    * [#2626](https://github.com/http4s/http4s/pull/2626): Fix typos in root package and DSL docs
    * [#2635](https://github.com/http4s/http4s/pull/2635): Remove obsolete scaladoc from client
    * [#2645](https://github.com/http4s/http4s/pull/2645): Fix string literal in router example in static file docs

* Internal
    * [#2563](https://github.com/http4s/http4s/pull/2563): Refactor `EntityDecoder#decode`
    * [#2553](https://github.com/http4s/http4s/pull/2553): Refactor `Timeout`
    * [#2564](https://github.com/http4s/http4s/pull/2564): Refactor boopickle and circe decoders
    * [#2580](https://github.com/http4s/http4s/pull/2580): Refactor server `RequestLogger`
    * [#2581](https://github.com/http4s/http4s/pull/2581): Remove redundant braces in various types
    * [#2539](https://github.com/http4s/http4s/pull/2539): Narrow cats imports
    * [#2582](https://github.com/http4s/http4s/pull/2582): Refactor `DefaultHead`
    * [#2590](https://github.com/http4s/http4s/pull/2590): Refactor `GZip`
    * [#2591](https://github.com/http4s/http4s/pull/2590): Refactor literal macros to not use `.get`
    * [#2596](https://github.com/http4s/http4s/pull/2596): Refactor `MimeLoader`
    * [#2542](https://github.com/http4s/http4s/pull/2542): Refactor `WebjarService`
    * [#2555](https://github.com/http4s/http4s/pull/2555): Refactor `FileService`
    * [#2597](https://github.com/http4s/http4s/pull/2597): Optimize internal hex encoding
    * [#2599](https://github.com/http4s/http4s/pull/2599): Refactor `ChunkAggregator`
    * [#2574](https://github.com/http4s/http4s/pull/2574): Refactor `FollowRedirect`
    * [#2648](https://github.com/http4s/http4s/pull/2648): Move `mimedb-generator` from a project to an internal SBT plugin. Run with `core/generateMimeDb`.

* Dependency updates
    * cats-2.0.0-M4
    * cats-effect-2.0.0-M4
    * circe-0.12.0-M3
    * discipline-0.12.0-M3
    * fs2-1.1.0-M1
    * jawn-0.14.2
    * jawn-fs2-0.15.0-M1
    * json4s-3.6.6
    * log4s-1.8.2
    * parboiled-2.0.1 (internal fork)
    * play-json-2.7.4
    * sbt-doctest-0.9.5 (tests only)
    * sbt-native-packager-1.3.22 (examples only)
    * sbt-site-1.4.0 (docs only)
    * sbt-tpolecat-0.1.6 (compile time only)
    * scalacheck-1.14.0
    * scalatags-0.7.0 (2.12 and 2.13 only)
    * scalaxml-1.2.0
    * specs2-4.5.1
    * mockito-core-2.28.2 (tests only)
    * tut-0.6.12 (docs only)
    * twirl-1.4.2
    * vault-2.0.0-M2

# v0.20.3 (2019-06-12)

* Bug fixes
    * [#2638](https://github.com/http4s/http4s/pull/2638): Fix leaking sensitive headers in server RequestLogger

# v0.18.24 (2019-06-12)

* Bug fixes
    * [#2639](https://github.com/http4s/http4s/pull/2639): Fix leaking sensitive headers in server RequestLogger

* Dependency updates
    - cats-1.6.1
    - jetty-9.4.19.v20190610
    - tomcat-9.0.21

# v0.20.2 (2019-06-12)

* Bug fixes
    * [#2604](https://github.com/http4s/http4s/pull/2604): Defer creation of `SSLContext.getDefault()` in blaze-client
    * [#2611](https://github.com/http4s/http4s/pull/2611): Raise errors with `getResource()` into effect in `StaticFile`

* Enhancements
    * [#2567](https://github.com/http4s/http4s/pull/2567): Add `mapK` to `AuthedRequest`.  Deprecate `AuthedService` in favor of `AuthedRoutes`.

* Internals
    * [#2579](https://github.com/http4s/http4s/pull/2579): Skip Travis CI on tags

* Dependency updates
    * blaze-0.14.4
    * cats-core-1.6.1
    * cats-effect-1.3.1
    * fs2-1.0.5 (except Scala 2.13.0-M5)
    * okhttp-3.14.2
    * tomcat-9.0.21

# v0.20.1 (2019-05-16)

Users of blaze-client are strongly urged to upgrade.  This patch fixes a bug and passes new tests, but we still lack 100% confidence in it.  The async-http-client backend has proven stable for a large number of users.

* Bug fixes
    * [#2562](https://github.com/http4s/http4s/pull/2562): Fix issue in `PoolManager` that causes hung requests in blaze-client.
    * [#2571](https://github.com/http4s/http4s/pull/2571): Honor `If-None-Match` request header in `StaticFile`

* Enhancements
    * [#2532](https://github.com/http4s/http4s/pull/2532): Add queue limit to log message when client wait queue is full
    * [#2535](https://github.com/http4s/http4s/pull/2535): Add `translate` to `HttpRoutes` and `HttpApp`

* Documentation
    * [#2533](https://github.com/http4s/http4s/pull/2533): Fix link to Metrics middleware
    * [#2538](https://github.com/http4s/http4s/pull/2538): Add @MartinSnyder's presentation, update giter8 instructions
    * [#2559](https://github.com/http4s/http4s/pull/2559): Add @gvolpe's presentation and http4s-tracer

* Internals
    * [#2525](https://github.com/http4s/http4s/pull/2525): Pointful implementation of `AuthMiddleware.noSpider`
    * [#2534](https://github.com/http4s/http4s/pull/2534): Build with xenial and openjdk8 on Travis CI
    * [#2530](https://github.com/http4s/http4s/pull/2530): Refactoring of `authentication.challenged`
    * [#2531](https://github.com/http4s/http4s/pull/2531): Refactoring of `PushSupport`
    * [#2543](https://github.com/http4s/http4s/pull/2543): Rename maintenance branches to `series/x.y`
    * [#2549](https://github.com/http4s/http4s/pull/2549): Remove workarounds in `BlazeClient` for [typelevel/cats-effect#487](https://github.com/typelevel/cats-effect/issues/487)
    * [#2575](https://github.com/http4s/http4s/pull/2575): Fix the Travis CI release pipeline

* Dependency updates
    * blaze-0.14.2
    * cats-effect-1.3.0
    * jetty-server-9.4.18.v20190429
    * metrics-core-4.1.0
    * sbt-native-packager-1.3.21 (examples only)
    * tomcat-9.0.20

# v0.20.0 (2019-04-22)

* Announcements
    * blaze-client stability
      We are declaring this a stable release, though we acknowledge a handful of lingering issues with the blaze-client.  Users who have trouble with the blaze backend are invited to try the async-http-client, okhttp, or jetty-client backends instead.

    * Scala 2.13 compatibility
      When our dependencies are published for Scala 2.13.0-RC1, we will publish for it and drop support for Scala 2.13.0-M5.  We know it's out there, and we're as anxious as you.

    * cats-2 and http4s-0.21
      Cats 2.0 is expected soon, and a Cats Effect 2.0 is under discussion.  These will be binary compatible with their 1.x versions, with the exception of their laws modules.  We intend to publish http4s-0.21 on these when they are available in order to provide a compatible stack for our own laws.

    * EOL of 0.18
      This marks the end of active support for the 0.18 series.  Further releases in that series will require a pull request and an accompanying tale of woe.

* Breaking changes
    * [#2506](https://github.com/http4s/http4s/pull/2506): Raise `DecodeFailure` with `MonadError` in `Message#as` rather than relying on effect to catch in `fold`. Requires a new `MonadError` constraint.

* Bugfixes
    * [#2502](https://github.com/http4s/http4s/pull/2502): Stop relying on undefined behavior of `fold` to catch errors in client.

* Enhancements
    * [#2508](https://github.com/http4s/http4s/pull/2508): Add `mediaType` String context macro for validating literals.  Provide the same for `uri` and `qValue`, deprecating `Uri.uri` and `QValue.q`.
    * [#2520](https://github.com/http4s/http4s/pull/2520): Parameterize `selectorThreadFactory` for blaze server.  This allows setting the priority for selector threads.

* Documentation
    * [#2488](https://github.com/http4s/http4s/pull/2488): Fix bad link in changelog
    * [#2494](https://github.com/http4s/http4s/pull/2494): Add note on queue usage to `BlazeWebSocketExample`
    * [#2509](https://github.com/http4s/http4s/pull/2509): Add Formation as adopter
    * [#2516](https://github.com/http4s/http4s/pull/2516): Drop redundant `enableWebSockets` in blaze example.

* Internals
    * [#2521](https://github.com/http4s/http4s/pull/2521): Add utility conversion for `java.util.concurrent.CompletableFuture` to `F[_]: Concurrent`

* Dependency updates
    * blaze-0.14.0
    * jetty-9.4.16.v20190411
    * kind-projector-0.10.0 (build only)
    * okhttp-3.14.1
    * mockito-core-2.27.0 (test only)
    * sbt-jmh-0.3.6 (benchmarks only)
    * tomcat-9.0.19
    * tut-plugin-0.6.11 (docs only)

# v0.20.0-RC1 (2019-04-03)

* Breaking changes
    * [#2471](https://github.com/http4s/http4s/pull/2471): `Headers` is no longer an `Iterable[Header]`
    * [#2393](https://github.com/http4s/http4s/pull/2393): Several changes related to 2.13 support:
    * Replace `Seq` with `List` on:
    *  `` `Accept-Ranges.`.rangeUnits``
    *  ``CacheDirective.`no-cache`.fieldNames``
    *  `CacheDirective.private.fieldNames`
    *  `LanguageTag.subTags`
    *  `MediaType.fileExtensions`
    *  `` `User-Agent`.other``
    * Replace `Seq` with `immutable.Seq` on:
    *  `Query#multiParams.values`
    *  `Query#params.values`
    *  `Uri#multipParams.values`
    * `Query` is no longer a `Seq[Query.KeyValue]`
    * `RequestCookieJar` is no longer an `Iterable[RequestCookie]`.

* Enhancements
    * [#2466](https://github.com/http4s/http4s/pull/2466): Provide better message for `WaitQueueFullFailure`
    * [#2479](https://github.com/http4s/http4s/pull/2479): Refresh `MimeDb` from the IANA registry
    * [#2393](https://github.com/http4s/http4s/pull/2393): Scala 2.13.0-M5 support
    * All modules except http4s-boopickle
    * `Monoid[Headers]` instance

* Bugfixes
    * [#2470](https://github.com/http4s/http4s/pull/2470): Don't wait indefinitely if a request timeout happens while borrowing a connection in blaze-client.

* Documentation
    * [#2469](https://github.com/http4s/http4s/pull/2469): Add scala-steward to adopters
    * [#2472](https://github.com/http4s/http4s/pull/2472): Add http4s-chatserver demo
    * [#2478](https://github.com/http4s/http4s/pull/2478): Better scaladoc for `HttpApp`
    * [#2480](https://github.com/http4s/http4s/pull/2480): Enhance documentation of static rendering

* Other
    * [#2474](https://github.com/http4s/http4s/pull/2474): Skip another blaze test that fails only on CI

* Dependency upgrades
    * argonaut-6.2.3
    * blaze-0.14.0-RC1
    * sbt-jmh-0.3.5 (benchmarks only)
    * sbt-native-packager (example only)
    * scalatags-0.6.8

# v0.20.0-M7 (2019-03-20)

* Bugfixes
    * [#2450](https://github.com/http4s/http4s/pull/2450): Fix `CirceInstances.builder` initialization, which referenced unintialized eager vals.

* Enhancements
    * [#2435](https://github.com/http4s/http4s/pull/2435): Log information about canceled requests in `ResponseLogger`
    * [#2429](https://github.com/http4s/http4s/pull/2429): Add `httpRoutes` and `httpApp` convenience constructors to `ChunkAggregator`
    * [#2446](https://github.com/http4s/http4s/pull/2446): Introduce `Http4sDsl2[F[_], G[_]]` trait to support `http4s-directives` library.  `Http4sDsl` extends it as `Http4sDsl[F, F]`.  This change should be invisible to http4s-dsl users.
    * [#2444](https://github.com/http4s/http4s/pull/2444): New modeled headers for `If-Match` and `If-Unmodified-Since`
    * [#2458](https://github.com/http4s/http4s/pull/2458): Building on bugfix in [#2453](https://github.com/http4s/http4s/pull/2453), don't clean up the stage if it's going to be shut down anyway

* Documentation
    * [#2432](https://github.com/http4s/http4s/pull/2432): Fix Github URL in Scaladoc for tagged versions
    * [#2440](https://github.com/http4s/http4s/pull/2440): Fix broken links in client documentation
    * [#2447](https://github.com/http4s/http4s/pull/2447): Clarification of webjar path on static files
    * [#2448](https://github.com/http4s/http4s/pull/2448): Update copyright year
    * [#2454](https://github.com/http4s/http4s/pull/2454): Update `mountService` reference to `withHttpApp`
    * [#2455](https://github.com/http4s/http4s/pull/2455): Remove dangling reference to `G` parameter in `HttpApp` scaladoc
    * [#2460](https://github.com/http4s/http4s/pull/2460): Add `circuit-http4s` to adopters

* Other
    * [#2464](https://github.com/http4s/http4s/pull/2464): Temporarily disable blaze tests that fail only on CI while running on CI.

* Dependency upgrades
    * async-http-client-2.8.1
    * fs2-1.0.4
    * json4s-3.6.5
    * okhttp-3.14.0
    * play-json-2.7.2
    * sbt-explicit-depenendencies-0.2.9 (build only)
    * sbt-native-packager-1.3.19 (example only)

# v0.18.23 (2019-03-19)

* Bug fixes
    * [#2453](https://github.com/http4s/http4s/pull/2453): Fix bug in blaze-client that unnecessarily recycled connections.

* Dependency upgrades
    - jetty-9.4.15.v20190215
    - log4s-1.7.0
    - metrics-4.0.5
    - mockito-2.25.1 (test only)
    - scodec-bits-1.1.9
    - tomcat-9.0.17

# v0.20.0-M6 (2019-02-16)

* Breaking changes
    * [#2369](https://github.com/http4s/http4s/pull/2369): Make `log` operation on logging middlewares return an `F[Unit]` to support pure logging.
    * [#2370](https://github.com/http4s/http4s/pull/2370): `Prometheus.apply` returns in `F[_]` to represent its effect on the collector registry.
    * [#2398](https://github.com/http4s/http4s/pull/2398): Add media ranges to `jsonDecoderAdaptive` to support overriding the media type in an `EntityDecoder`
    * [#2396](https://github.com/http4s/http4s/pull/2396): Parameterize `Logger` middlewares to work with any `Http[G, F]` instead of requiring `HttpApp[F]`.
    * [#2318](https://github.com/http4s/http4s/pull/2318): Replace `AttributeMap` with `io.christopherdavenport.Vault`
    * [#2414](https://github.com/http4s/http4s/pull/2414): Default to a no-op cookie store in async-http-client for more uniform behavior with other clients
    * [#2419](https://github.com/http4s/http4s/pull/2419): Relax constraint on `Retry` middleware from `Effect` to `Sync`

* Bugfixes
    * [#2421](https://github.com/http4s/http4s/pull/2421): Fix buggy use of `toString` in async-http-client when rendering URIs.

* Enhancements
    * [#2364](https://github.com/http4s/http4s/pull/2364): Scalafix `allocate` to `allocated`
    * [#2366](https://github.com/http4s/http4s/pull/2366): Add `chunkBufferMaxSize` parameter to `BlazeClientBuilder` and `BlazeServerBuilder`. Change default to 10kB.
    * [#2316](https://github.com/http4s/http4s/pull/2316): Support custom error messages in circe, argonaut, and jawn.
    * [#2403](https://github.com/http4s/http4s/pull/2403): Add `MemoryAllocationExports` to `PrometheusExportService`
    * [#2355](https://github.com/http4s/http4s/pull/2355), [#2407](https://github.com/http4s/http4s/pull/2407): Add new `HttpMethodOverride` middleware
    * [#2391](https://github.com/http4s/http4s/pull/2391): Add `Authorization` to `*` as a default allowed header in default CORS config
    * [#2424](https://github.com/http4s/http4s/pull/2424): Include Chunked Transfer-Encoding header in Multipart Requests

* Documentation
    * [#2378](https://github.com/http4s/http4s/pull/2378): Fix typo in `EntityDecoder` scaladoc
    * [#2374](https://github.com/http4s/http4s/pull/2374): Include scheme in CORS examples
    * [#2399](https://github.com/http4s/http4s/pull/2399): Link to @kubukoz' presentation
    * [#2418](https://github.com/http4s/http4s/pull/2418): Fix typo in CORS documentation
    * [#2420](https://github.com/http4s/http4s/pull/2420): Add Raster Foundry to adopters

* Internal
    * [#2359](https://github.com/http4s/http4s/pull/2359): Remove code coverage checks
    * [#2382](https://github.com/http4s/http4s/pull/2382): Refactor the blaze-server pipeline construction
    * [#2401](https://github.com/http4s/http4s/pull/2401), [#2408](https://github.com/http4s/http4s/pull/2408), [#2409](https://github.com/http4s/http4s/pull/2409): Stop building with sbt-rig, deal with fallout
    * [#2422](https://github.com/http4s/http4s/pull/2422): Use Scala 2.12.8 and slash-syntax in SBT files

* Dependency upgrades
    * async-http-client-2.7.0
    * cats-1.6.0
    * circe-0.11.1
    * fs2-1.0.3
    * jawn-fs2-0.14.2
    * json4s-3.6.4
    * log4s-1.7.0
    * mockito-core-2.24.5 (tests only)
    * okhttp-3.13.1
    * parboiled-1.0.1 (http4s' internal fork)
    * play-json-2.7.1
    * sbt-build-info-0.9.0 (build only)
    * sbt-native-packager-1.3.18 (examples only)
    * sbt-updates-0.4.0 (build only)
    * tomcat-9.0.6
    * twirl-1.4.0

# v0.18.22 (2019-02-13)

* Enhancements
    * [#2389](https://github.com/http4s/http4s/pull/2389): Add `RequestKey` to Logging when eviction is necessary

# v0.20.0-M5 (2019-01-12)

Consider the blaze beta and all other modules RC quality. Don't forget
there is a scalafix to assist migration from 0.18!

* Breaking changes
    * [#2308](https://github.com/http4s/http4s/pull/2308): Change `allocate` to `allocated` on backend builders for consistency with `cats.effect.Resource#allocated`.
    * [#2332](https://github.com/http4s/http4s/pull/2332): Make double slashes behave more reasonably in the DSL.
    * [#2351](https://github.com/http4s/http4s/pull/2351): Change `clientAuthMode` on server builders from `Boolean` to sum type `SSLClientAuthMode`

* Enhancements
    * [#2309](https://github.com/http4s/http4s/pull/2308): Specialize `TimeoutException` to `WaitQueueTimeoutException` in client pool manager.  Do not retry this by default in `Retry` middleware.
    * [#2342](https://github.com/http4s/http4s/pull/2342): Add `expectOption` and `expectOptionOr` which behave like `expect` and `expectOr` respectively, but return `None` on `404` and `410` responses and `Some[A]` on other successful responses.  Other status codes still raise an error.
    * [#2328](https://github.com/http4s/http4s/pull/2328): Add a `SecureSession` attribute to server requests to expose the SSL session ID, the cipher suite, the key size, and a list of X509 certificates.

* Documentation
    * [#2337](https://github.com/http4s/http4s/pull/2337): Use `tut:silent` on imports in docs
    * [#2336](https://github.com/http4s/http4s/pull/2336): Add example of building a server from a `Resource`

* Internal
    * [#2310](https://github.com/http4s/http4s/pull/2310): Use max of 16 cores in `-Ybackend-parallelism`
    * [#2332](https://github.com/http4s/http4s/pull/2332): Don't make `F` evidence parameter a val in jetty-client `ResponseListener`.

* Dependency upgrades
    * blaze-0.14.0-M2
    * circe-0.11.0
    * jawn-0.14.1
    * jawn-fs2-0.14.1
    * json4s-3.6.3
    * metrics-4.0.5
    * okhttp-3.12.1
    * play-json-2.6.13
    * scalafix-0.9.1 (scalafix only)
    * tomcat-9.0.14

# v0.20.0-M4 (2018-12-05)

* Bugfixes
    * [#2283](https://github.com/http4s/http4s/pull/2283): Fix client metrics bug that decremented active requests and recorded time before the resource was released.
    * [#2288](https://github.com/http4s/http4s/pull/2288): Stop leaking `IdleTimeoutStage`s in the blaze client.  They were not always removed properly, leading to multiple timeout stages remaining in a connection's blaze pipeline.
    * [#2281](https://github.com/http4s/http4s/pull/2281): Fix `ClassCastException` on `decode` of an empty `Chunk`
    * [#2305](https://github.com/http4s/http4s/pull/2305): Correctly shut down the blaze-client

* Enhancements
    * [#2275](https://github.com/http4s/http4s/pull/2275): Set default prefix for Prometheus and Dropwizard metrics backends.
    * [#2276](https://github.com/http4s/http4s/pull/2276): Make scalafix Github based instead of binary based
    * [#2285](https://github.com/http4s/http4s/pull/2285): Finish deprecating `BlazeServer` in favor of `BlazeServerBuilder`.  The former's internals are now expressed in terms of the latter.
    * [#2286](https://github.com/http4s/http4s/pull/2286): Improvements to scalafix
    * Fix `withEntitywithEntity` bug in migration
    * Migration to `BlazeServerBuilder`
    * Fix `MessageSyntax#withBody`
    * Import `ResponseCookie` instead of an alias to the old `Cookie`

* Documentation
    * [#2297](https://github.com/http4s/http4s/pull/2297): Remove appveyor badge

* Dependency upgrades
    * cats-1.5.0
    * cats-effect-1.1.0
    * jetty-9.4.14.v20181114
    * kind-projector-0.9.9 (internal)
    * mockito-2.23.4 (tests only)
    * okhttp-3.12.0
    * play-json-2.6.11
    * simpleclient-0.6.0 (Prometheus)
    * sbt-1.2.7 (build only)
    * sbt-native-packager-1.3.15 (examples only)
    * tut-0.6.10 (docs only)

# v0.20.0-M3 (2018-11-13)

* Breaking changes
    * [#2228](https://github.com/http4s/http4s/pull/2228): Support more attributes for the response cookie in `CSRF` middleware. Configuration is now done through a builder, similar to backends.
    * [#2269](https://github.com/http4s/http4s/pull/2269): In the client DSL, move the body parameter ahead of the `Uri`. This works around an ambiguous overload that previously made it impossible to call `(Uri, Header)` on methods that take a body.
    * [#2262](https://github.com/http4s/http4s/pull/2262): Replace `Seq` with `Chain` in `UrlForm`.
    * [#2197](https://github.com/http4s/http4s/pull/2262): Require `Signal` rather than `SignallingRef` in `serveWhile`

* Bugfixes
    * [#2260](https://github.com/http4s/http4s/pull/2260): Fix leak in blaze-client on a canceled connection
    * [#2258](https://github.com/http4s/http4s/pull/2258): Fix deadlocks in the blaze-client pool manager under cancellation and certain other failures.

* Enhancements
    * [#2266](https://github.com/http4s/http4s/pull/2266): Support flag query parameters (i.e., parameters with no value) in the DSL with `FlagQueryParamMatcher`.
    * [#2240](https://github.com/http4s/http4s/pull/2240): Add `.resource`, `.stream`. and `.allocate` constructors to all server and client builders.
    * [#2242](https://github.com/http4s/http4s/pull/2242): Support setting socket channel options on blaze-server.
    * [#2270](https://github.com/http4s/http4s/pull/2270): Refresh `MimeDB` from the IANA registry.

* Internal
    * [#2250](https://github.com/http4s/http4s/pull/2250): Ignore http4s updates in scalafix-inputs
    * [#2267](https://github.com/http4s/http4s/pull/2267): Drop appveyor continuous integration
    * [#2256](https://github.com/http4s/http4s/pull/2256): Bump base version of scalafix to 0.18.21.
    * [#2271](https://github.com/http4s/http4s/pull/2271): Fix compilation error introduced between [#2228](https://github.com/http4s/http4s/pull/2228) and [#2262](https://github.com/http4s/http4s/pull/2262).

* Documentation
    * [#2255](https://github.com/http4s/http4s/pull/2255): Improve scalafix docs

* Dependency upgrades
    * blaze-0.14.0-M11
    * tomcat-9.0.13

# v0.20.0-M2 (2018-11-05)

* Bug fixes
    * [#2239](https://github.com/http4s/http4s/pull/2239): Fix hang when `.allocate` on a client builder fails

* Breaking changes
    * [#2207](https://github.com/http4s/http4s/pull/2207): Remove `PathNormalizer`. The functionality is now on `Uri.removeDotSegments`.
    * [#2210](https://github.com/http4s/http4s/pull/2210): Streamline instances:
    * `Http4s`, `Http4sInstances`, and `Http4sFunctions` are deprecated
    * Move instances `F[A]` for cats type classes `F` into companions of `A`
    * `Http4sDsl` no longer mixes in `UriFunctions`
    * `EntityEncoderInstances` and `EntityDecoderInstances` are removed. The instances moved to the companion objects.
    * [#2243](https://github.com/http4s/http4s/pull/2243): Cleanup `ServerBuilder` defaults and traits
    * Make `ServerBuilder` private.  The public server builders (e.g., `BlazeServerBuilder`) remain, but they no longer implement a public interface.
    * Remove `IdleTimeoutSupport`, `AsyncTimeout`, `SSLKeyStoreSupport`, `SSLContextSupport`, and `WebSocketSupport` traits. The properties remain on the public server builders.
    * Deprecated defaults on those support companion objects, in favor of `org.http4s.server.defaults`.
    * [#2063](https://github.com/http4s/http4s/pull/2063): Cancel request whenever a blaze server connection is shutdown.
    * [#2234](https://github.com/http4s/http4s/pull/2234): Clean up `Message` trait
    * Remove deprecated `EffectMessageSyntax`, `EffectRequestSyntax`, `EffectResponseSyntax` traits and associated objects
    * Remove `MessageOps`, `RequestOps`, and `ResponseOps` and put the removed methods, sans unneeded implicit parameters, directly in the classes
    * Deprecate `replaceAllHeaders`, pointing to `withHeaders` instead.
    * Deprecate `withType`, which takes a `MediaType` and just wraps it in a `Content-Type`
    * Add `withoutAttribute` and `withoutTrailerHeaders` to complement the with variants
    * Correct `filterHeaders`' scaladoc comment, which described the opposite of the behavior
    * Fix bug in `withoutContentType`

* Enhancements
    * [#2205](https://github.com/http4s/http4s/pull/2205): Add new `ResponseTiming` middleware, which adds a header to the Response as opposed to full `MetricsOps`.
    * [#2222](https://github.com/http4s/http4s/pull/2222): Add `shutdownTimeout` property to `JettyBuilder`.  Shutdown of the server waits for existing connections to complete for up to this duration before a hard shutdown with a `TimeoutException`.
    * [#2227](https://github.com/http4s/http4s/pull/2227): Add `withMaxHeaderLength` setter to `BlazeClientBuilder`
    * [#2230](https://github.com/http4s/http4s/pull/2230): `DefaultServerErrorHandler` only handles `NonFatal` `Throwable`s, instead of all `Throwable`s that aren't `VirtualMachineError`s
    * [#2237](https://github.com/http4s/http4s/pull/2237): Support parsing cookies with trailing semi-colons. This is invalid per spec, but seen often in the wild.
    * [#1687](https://github.com/http4s/http4s/pull/1687): Add a modeled `Link` header.
    * [#2244](https://github.com/http4s/http4s/pull/2244): Refactor blaze-server idle timeout
    * Quiet `Abnormal NIO1HeadStage termination\njava.util.concurrent.TimeoutException: Timeout of 30 seconds triggered. Killing pipeline.` error logging, even on idling persistent connections.  This is reduced to a debug log.
    * Use a `TickWheelExecutor` resource per blaze-server instead of a global that does not shut down when the server does.

* Bug fixes
    * [#2239](https://github.com/http4s/http4s/pull/2239): Fix hang when `.allocate` on a client builder fails
    * [#2214](https://github.com/http4s/http4s/pull/2214): Add a scalafix from http4s-0.18.20 to 0.20.0-M2.  See [upgrading](https://http4s.org/v0.20/upgrading/) for instructions.
    * [#2241](https://github.com/http4s/http4s/pull/2241): Restrict internal `IdleTimeoutStage` to a `FiniteDuration`.  Fixes an exception when converting to milliseconds when debug logging.

* Documentation
    * [#2223](https://github.com/http4s/http4s/pull/2223): Fix color of EOL label on v0.19
    * [#2226](https://github.com/http4s/http4s/pull/2226): Correct erroneous `Resource` in 0.19.0-M3 changelog

* Internal
    * [#2219](https://github.com/http4s/http4s/pull/2219): Allow test failures on openjdk11 until we can fix the SSL issue
    * [#2221](https://github.com/http4s/http4s/pull/2194): Don't grant MiMa exceptions for 0.19.1, which will never be

* Dependency upgrades
    * async-http-client-2.6.0
    * blaze-0.14.0-M10
    * circe-0.10.1
    * json4s-3.6.2
    * sbt-native-packager-1.3.12 (examples only)
    * tut-0.6.9 (docs only)

# v0.20.0-M1 (2018-10-27)

Due to the inadvertent release of 0.19.0, we have opened a new minor version.  The stable release with MiMa enforcement will be v0.20.0.

* Breaking changes
    * [#2159](https://github.com/http4s/http4s/pull/2159): Add a `responseHeaderTimeout` property to `BlazeServerBuilder`. Responses that timeout are completed with `Response.timeout`, which defaults to 503 Service Unavailable.  `BlazeServerBuilder` now requires a `Timer[F]`.
    * [#2177](https://github.com/http4s/http4s/pull/2177): Deprecate `org.http4s.syntax.async`, which was not directly relevant to HTTP.
    * [#2131](https://github.com/http4s/http4s/pull/2131): Refactor server metrics
    * `http4s-server-metrics` module merged into `http4s-dropwizard-metrics`
    * `http4s-prometheus-server-metrics` module merged into `http4s-prometheus-metrics`
    * The `org.http4s.server.middleware.metrics.Metrics` middleware now takes a `MetricsOps`, implemented by Dropwizard, Prometheus, or your custom interpreter.
    * [#2180](https://github.com/http4s/http4s/pull/2180): Change default response on `Timeout` middlware to `503 Service Unavailable`

* Enhancements
    * [#2159](https://github.com/http4s/http4s/pull/2159): Set default client request timeout to 1 minute
    * [#2163](https://github.com/http4s/http4s/pull/2163): Add `mapK` to `Request` and `Response`
    * [#2168](https://github.com/http4s/http4s/pull/2168): Add `allocate` to client builders
    * [#2174](https://github.com/http4s/http4s/pull/2159): Refactor the blaze-client timeout architecture.
    * A `TickWheelExecutor` is now allocated per client, instead of globally.
    * Request rendering and response parsing is now canceled more aggressively on timeout.
    * [#2184](https://github.com/http4s/http4s/pull/2184): Receive response concurrently with sending request in blaze client. This reduces waste when the server is not interested in the entire request body.
    * [#2190](https://github.com/http4s/http4s/pull/2190): Add `channelOptions` to blaze-client to customize socket options.

* Bug fixes
    * [#2166](https://github.com/http4s/http4s/pull/2166): Fix request timeout calculation in blaze-client to resolve "Client response header timeout after 0 millseconds" error.
    * [#2189](https://github.com/http4s/http4s/pull/2189): Manage the `TickWheelTimer` as a resource instead of an `F[A, F[Unit]]`. This prevents a leak in (extremely unlikely) cases of cancellation.

* Internal
    * [#2179](https://github.com/http4s/http4s/pull/2179): Method to silence expected exceptions in tests
    * [#2194](https://github.com/http4s/http4s/pull/2194): Remove ill-conceived, zero-timeout unit tests
    * [#2199](https://github.com/http4s/http4s/pull/2199): Make client test sizes proportional to the number of processors for greater Travis stability

* Dependency upgrades
    * alpn-boot-8.1.13.v20181017 (examples only)
    * blaze-0.14.0-M9
    * sbt-native-packager-1.3.11 (examples only)

# v0.18.21 (2018-11-05)

* Bug fixes
    * [#2231](https://github.com/http4s/http4s/pull/2231): Fix off-by-one error that lets blaze-client wait queue grow one past its limit

# v0.18.20 (2018-10-18)

* Bug fixes
    * [#2181](https://github.com/http4s/http4s/pull/2181): Honor `redactHeadersWhen` in client `RequestLogger` middleware

* Enhancements
    * [#2178](https://github.com/http4s/http4s/pull/2178): Redact sensitive headers by default in `Retry` middleware. Add `retryWithRedactedHeaders` function that parameterizes the headers predicate.

* Documentation
    * [#2147](https://github.com/http4s/http4s/pull/2147): Fix link to v0.19 docs

* Internal
    * [#2130](https://github.com/http4s/http4s/pull/2130): Build with scala-2.12.7 and sbt-1.2.3

# ~~v0.19.0 (2018-10-05)~~

This release is identical to v0.19.0-M4.  We mistagged it.  Please proceed to the 0.20 series.

# v0.19.0-M4 (2018-10-05)

* Breaking changes
    * [#2137](https://github.com/http4s/http4s/pull/2137): Remove `ExecutionContext` argument to jetty-client in favor of the `ContextShift[F]`.
    * [#2070](https://github.com/http4s/http4s/pull/2070): Give `AbitraryInstances` unique names with `http4sTesting` prefix.
    * [#2136](https://github.com/http4s/http4s/pull/2136): Add `stream` method to `Client` interface. Deprecate `streaming`, which is just a `flatMap` of `Stream`.
    * [#2143](https://github.com/http4s/http4s/pull/2143): WebSocket model improvements:
    * The `org.http4s.websocket` package in unified in http4s-core
    * Drop http4s-websocket module dependency
    * All frames use an immutable `scodec.bits.ByteVector` instead of an `Array[Byte]`.
    * Frames moved from `WebSocketBits` to the `WebSocketFrame` companion
    * Rename all instances of `Websocket*` to `WebSocket*` for consistency
    * [#2094](https://github.com/http4s/http4s/pull/2094): Metrics unification
    * Add a `MetricsOps` algebra to http4s-core to be implemented by any metrics backend.
    * Create new `Metrics` middleware in http4s-client based on `MetricsOps`
    * Replace http4s-dropwizard-client-metrics and http4s-proemtheus-client-metrics modules with http4s-dropwizard-metrics and http4s-prometheus-metrics to implement `MetricsOps`.

* Enhancements
    * [#2149](https://github.com/http4s/http4s/pull/2134): Refresh `MimeDB` constants from the public registry
    * [#2151](https://github.com/http4s/http4s/pull/2151): Changed default response timeout code from 500 to 503

* Documentation updates
    * [#2134](https://github.com/http4s/http4s/pull/2134): Add Cats Friendly badge to readme
    * [#2139](https://github.com/http4s/http4s/pull/2139): Reinstate example projects
    * [#2145](https://github.com/http4s/http4s/pull/2145): Fix deprecated calls to `Client#streaming`

* Internal
    * [#2126](https://github.com/http4s/http4s/pull/2126): Delete obsolete `bin` directory
    * [#2127](https://github.com/http4s/http4s/pull/2127): Remove MiMa exceptions for new modules
    * [#2128](https://github.com/http4s/http4s/pull/2128): Don't run `dependencyUpdates` on load
    * [#2129](https://github.com/http4s/http4s/pull/2129): Build with sbt-1.2.3 and scala-2.12.7
    * [#2133](https://github.com/http4s/http4s/pull/2133): Build with kind-projector-0.9.8
    * [#2146](https://github.com/http4s/http4s/pull/2146): Remove all use of `OutboundCommand` in blaze integration

* Dependency upgrades
    * async-http-client-2.5.4
    * blaze-0.14.0-M5
    * fs2-1.0.0
    * jawn-0.13.0
    * scala-xml-1.1.1

# v0.19.0-M3 (2018-09-27)

* Breaking changes
    * [#2081](https://github.com/http4s/http4s/pull/2081): Remove `OkHttp` code redundant with `OkHttpBuilder`.
    * [#2092](https://github.com/http4s/http4s/pull/2092): Remove `ExecutionContext` and `Timer` implicits from async-http-client. Threads are managed by the `ContextShift`.
    * [#2115](https://github.com/http4s/http4s/pull/2115): Refactoring of `Server` and `ServerBuilder`:
    * Removed `Server#shutdown`, `Server#shutdownNow`, `Server#onShutdown`, and `Server#awaitShutdown`.  `Server` lifecycles are managed as a `fs2.Stream` or a `cats.effect.Resource`.
    * `ServerBuilder#start` replaced by `Server#resource`, which shuts down the `Server` after use.
    * Added a `ServerBuilder#stream` to construct a `Stream` from a `Resource`.
    * [#2118](https://github.com/http4s/http4s/pull/2118): Finalize various case classes.
    * [#2102](https://github.com/http4s/http4s/pull/2102): Refactoring of `Client` and some builders:
    * `Client` is no longer a case class.  Construct a new `Client` backend or middleware with `Client.apply(run: Request[F] => Resource[F, Response[F]])` for any `F` with a `Bracket[Throwable, F]`.
    * Removed `DisposableResponse[F]` in favor of `Resource[F, Response[F]]`.
    * Removed `Client#open` in favor of `Client#run`.
    * Removed `Client#shutdown` in favor of `cats.effect.Resource` or `fs2.Stream`.
    * Removed `AsyncHttpClient.apply`. It was not referentially transparent, and no longer possible. Use `AsyncHttpClient.resource` instead.
    * Removed deprecated `blaze.Http1Client.apply`

* Enhancements
    * [#2042](https://github.com/http4s/http4s/pull/2042): New `Throttle` server middleware
    * [#2036](https://github.com/http4s/http4s/pull/2036): New `http4s-jetty-client` backend, with HTTP/2 support
    * [#2080](https://github.com/http4s/http4s/pull/2080): Make `Http4sMatchers` polymorphic on their effect type
    * [#2082](https://github.com/http4s/http4s/pull/2082): Structured parser for the `Origin` header
    * [#2061](https://github.com/http4s/http4s/pull/2061): Send `Disconnect` event on EOF in blaze-server for faster cleanup of mid stages
    * [#2093](https://github.com/http4s/http4s/pull/2093): Track redirects in the `FollowRedirect` client middleware
    * [#2109](https://github.com/http4s/http4s/pull/2109): Add `→` as a synonym for `->` in http4s-dsl
    * [#2100](https://github.com/http4s/http4s/pull/2100): Tighten up module dependencies
    * http4s-testing only depends on specs2-matchers instead of specs2-core
    * http4s-prometheus-server-metrics depends on simpleclient_common instead of simpleclient

* Bugfixes
    * [#2069](https://github.com/http4s/http4s/pull/2069): Add proper `withMaxTotalConnections` method to `BlazeClientBuilder` in place of misnamed `withIdleTimeout` overload.
    * [#2106](https://github.com/http4s/http4s/pull/2106): Add the servlet timeout listener before the response has a chance to complete the `AsyncContext`

* Documentation updates
    * [#2076](https://github.com/http4s/http4s/pull/2076): Align coloring of legend and table for milestone on versoins page
    * [#2077](https://github.com/http4s/http4s/pull/2077): Replace Typelevel Code of Conduct with Scala Code of Conduct
    * [#2083](https://github.com/http4s/http4s/pull/2083): Fix link to 0.19 on the website
    * [#2100](https://github.com/http4s/http4s/pull/2100): Correct `re-start` to `reStart` in docs

* Internal
    * [#2105](https://github.com/http4s/http4s/pull/2105): Test on OpenJDK 11
    * [#2113](https://github.com/http4s/http4s/pull/2113): Check for unused compile dependencies in build
    * [#2115](https://github.com/http4s/http4s/pull/2115): Stop testing on Oracle JDK 10
    * [#2079](https://github.com/http4s/http4s/pull/2079): Use `readRange`, as contributed to fs2
    * [#2123](https://github.com/http4s/http4s/pull/2123): Remove unmaintained `load-test` module

* Dependency upgrades
    * cats-1.4.0
    * circe-0.10.0
    * fs2-1.0.0-RC1
    * jawn-fs2-0.13.0-RC1
    * play-json-3.6.10 for Scala 2.11.x
    * tomcat-9.0.12

# v0.18.19 (2018-09-27)

* Bug fixes
    * [#2101](https://github.com/http4s/http4s/pull/2101): `haveHeaders` checks by equality, not reference
    * [#2117](https://github.com/http4s/http4s/pull/2117): Handle unsuccessful responses in `JavaNetClient`

* Internal
    * [#2116](https://github.com/http4s/http4s/pull/2116): Test against OpenJDK 11. Retire Oracle JDK 10.

# v0.18.18 (2018-09-18)

* Bug fixes
    * [#2048](https://github.com/http4s/http4s/pull/2048): Correct misleading logging in `Retry` middleware
    * [#2078](https://github.com/http4s/http4s/pull/2078): Replace generic exception on full wait queue with new `WaitQueueFullFailure`

* Enhancements
    * [#2078](https://github.com/http4s/http4s/pull/2078): Replace generic exception on full wait queue with new `WaitQueueFullFailure`
    * [#2095](https://github.com/http4s/http4s/pull/2095): Add `Monoid[UrlForm]` instance

* Dependency upgrades
    * cats-1.4.0
    * fs2-0.10.6
    * jetty-9.4.12.v20180830
    * tomcat-9.0.12

# v0.19.0-M2 (2018-09-07)

* Breaking changes
    * [#1802](https://github.com/http4s/http4s/pull/1802): Race servlet requests against the `AsyncContext.timeout`. `JettyBuilder` and `TomcatBuilder` now require a `ConcurrentEffect` instance.
    * [#1934](https://github.com/http4s/http4s/pull/1934): Refactoring of `ConnectionManager`.  Now requires a `Concurrent` instance, which ripples to a `ConcurrentEffect` in blaze-client builders
    * [#2023](https://github.com/http4s/http4s/pull/2023): Don't overwrite existing `Vary` headers from `CORS`
    * [#2030](https://github.com/http4s/http4s/pull/2023): Restrict `MethodNotAllowed` response generator in DSL
    * [#2032](https://github.com/http4s/http4s/pull/2032): Eliminate mutable `Status` registry. IANA-registered `Status`es are still cached, but `register` is no longer public.
    * [#2026](https://github.com/http4s/http4s/pull/2026): `CSRF` enhancements
    * CSRF tokens represented with a newtype
    * CSRF token signatures are encoded hexadecimal strings, making them URI-safe.
    * Added a `headerCheck: Request[F] => Boolean` parameter
    * Added an `onFailure: Response[F]` parameter, which defaults to a `403`. This was formerly a hardcoded `401`.
    * [#1993](https://github.com/http4s/http4s/pull/2026): Massive changes from cats-effect and fs2 upgrades
    * `Timer` added to `AsyncHttpClient`
    * Dropwizard `Metrics` middleware now takes a `Clock` rather than a `Timer`
    * Client builders renamed and refactored for consistency and to support binary compatible evolution after 1.0:
    *  `BlazeClientBuilder` replaces `Http1Client`, `BlazeClient`, and `BlazeClientConfig`
    *  Removed deprecated `SimpleHttp1Client`
    *  `JavaNetClient` renamed to `JavaNetClientBuilder`, which now has a `resource` and `stream`
    *  `OkHttp` renamed to `OkHttpBuilder`.  The client now created from an `OkHttpClient` instance instead of an `F[OkHttpClient.Builder]`. A default client can be created as a `Resource` through `OkHttp.default`.
    * Fallout from removal of `fs2.Segment`
    *  `EntityDecoder.collectBinary` now decodes a `Chunk`
    *  `EntityDecoder.binaryChunk` deprecated
    *  `SegmentWriter` is removed
    *  Changes to:
      *  `ChunkWriter`s in blaze rewritten
      *  `Logger` middlewares
      *  `MemoryCache`
    * Blocking I/O now requires a blocking `ExecutionContext` and a `ContextShift`:
    *  `EntityDecoder`s:
      *  `EntityDecoder.binFile`
      *  `EntityDecoder.textFile`
      *  `MultipartDecoder.mixedMultipart`
    *  `EntityEncoder`s (no longer implicit):
      *  `File`
      *  `Path`
      *  `InputStream`
      *  `Reader`
    *  Multipart:
      *  `MultipartParser.parseStreamedFile`
      *  `MultipartParser.parseToPartsStreamedFile`
      *  `Part.fileData`
    *  Static resources:
      *  `StaticFile.fromString`
      *  `StaticFile.fromResource`
      *  `StaticFile.fromURL`
      *  `StaticFile.fromFile`
      *  `FileService.Config`
      *  `ResourceService.Config`
      *  `WebjarService.Config`
    *  `OkHttpBuilder`
    *  Servlets:
      *  `BlockingHttp4sServlet`
      *  `BlockingServletIo`
  * Servlet backend changes:
    *  `Http4sServlet` no longer shift onto an `ExecutionContext` by default.  Accordingly, `ServerBuilder` no longer has a `withExecutionContext`.
    *  Jetty and Tomcat builders use their native executor types instead of shifting onto an `ExecutionContext`.  Accordingly, `ServletBuilder#withExecutionContext` is removed.
    *  `AsyncHttp4sServlet` and `ServletContextSyntax` now default to non-blocking I/O.  No startup check is made against the servlet version, which failed classloading on an older servlet container.  Neither takes an `ExeuctionContext` parameter anymore.
  * Removed deprecated `StreamApp` aliases. `fs2.StreamApp` is removed and replaced by `cats.effect.IOApp`, `monix.eval.TaskApp`, or similar.
  * Removed deprecated `ServerApp`.
  * `EntityLimiter` middleware now requires an `ApplicativeError`
  * [#2054](https://github.com/http4s/http4s/pull/2054): blaze-server builder changes
  * `BlazeBuilder` deprecated for `BlazeServerBuilder`
  * `BlazeServerBuidler` has a single `withHttpApp(HttpApp)` in place of zero-to-many calls `mountService(HttpRoutes)`.
    *  This change makes it possible to mount an `HttpApp` wrapped in a `Logger` middleware, which only supports `HttpApp`
    *  Call `.orNotFound`, from `org.http4s.implicits._`, to cap an `HttpRoutes` as `HttpApp`
    *  Use `Router` to combine multiple `HttpRoutes` into a single `HttpRoutes` by prefix
    *  This interface will see more changes before 0.19.0 to promote long-term binary compatibility

* Enhancements
    * [#1953](https://github.com/http4s/http4s/pull/1953): Add `UUIDVar` path extractor
    * [#1963](https://github.com/http4s/http4s/pull/1963): Throw `ConnectException` rather than `IOException` on blaze-client connection failures
    * [#1961](https://github.com/http4s/http4s/pull/1961): New `http4s-prometheus-client-metrics` module
    * [#1974](https://github.com/http4s/http4s/pull/1974): New `http4s-client-metrics` module for Dropwizard Metrics
    * [#1973](https://github.com/http4s/http4s/pull/1973): Add `onClose` handler to `WebSocketBuilder`
    * [#2024](https://github.com/http4s/http4s/pull/2024): Add `HeaderEcho` server middleware
    * [#2062](https://github.com/http4s/http4s/pull/2062): Eliminate "unhandled inbund command: Disconnected"` warnings in blaze-server

* Bugfixes
  * [#2027](https://github.com/http4s/http4s/pull/2024): Miscellaneous websocket fixes
  * Stop sending frames even after closed
  * Avoid deadlock on small threadpools
  * Send `Close` frame in response to `Close` frame

* Documentation updates
    * [#1935](https://github.com/http4s/http4s/pull/1953): Make `http4sVersion` lowercase
    * [#1943](https://github.com/http4s/http4s/pull/1943): Make the imports in the Client documentation silent
    * [#1944](https://github.com/http4s/http4s/pull/1944): Upgrade to cryptobits-1.2
    * [#1971](https://github.com/http4s/http4s/pull/1971): Minor corrections to DSL tut
    * [#1972](https://github.com/http4s/http4s/pull/1972): Add `UUIDVar` to DSL tut
    * [#2034](https://github.com/http4s/http4s/pull/1958): Add branch to quickstart instructions
    * [#2035](https://github.com/http4s/http4s/pull/2035): Add Christopher Davenport to community staff
    * [#2060](https://github.com/http4s/http4s/pull/2060): Guide to setting up IntelliJ for contributors

* Internal
    * [#1966](https://github.com/http4s/http4s/pull/1966): Use scalafmt directly from IntelliJ
    * [#1968](https://github.com/http4s/http4s/pull/1968): Build with sbt-1.2.1
    * [#1996](https://github.com/http4s/http4s/pull/1996): Internal refactoring of `JettyBuilder`
    * [#2041](https://github.com/http4s/http4s/pull/2041): Simplify implementations of `RetryPolicy`
    * [#2050](https://github.com/http4s/http4s/pull/2050): Replace test `ExecutionContext` in `Http4sWSStageSpec`
    * [#2052](https://github.com/http4s/http4s/pull/2050): Introduce expiring `TestScheduler` to avoid leaking threads on tests

* Dependency upgrades
    * async-http-client-2.5.2
    * blaze-0.14.0-M4
    * cats-1.3.1
    * cats-effect-1.0.0
    * circe-0.10.0-M2
    * fs2-1.0.0-M5
    * jawn-0.13.0
    * jawn-fs2-0.13.0-M4
    * json4s-3.6.0

# v0.18.17 (2018-09-04)
* Accumulate errors in `OptionalMultiQueryParamDecoderMatcher` [#2000](https://github.com/http4s/pull/2000)
* New http4s-scalatags module [#2002](https://github.com/http4s/pull/2002)
* Resubmit bodies in `Retry` middleware where allowed by policy [#2001](https://github.com/http4s/pull/2001)
* Dependency upgrades:
  * play-json-3.6.10 (for Scala 2.12)
  * tomcat-9.0.11

# v0.18.16 (2018-08-14)
* Fix regression for `AutoSlash` when nested in a `Router` [#1948](https://github.com/http4s/http4s/pull/1948)
* Respect `redactHeadersWhen` in `Logger` middleware [#1952](https://github.com/http4s/http4s/pull/1952)
* Capture `BufferPoolsExports` in prometheus server middleware [#1977](https://github.com/http4s/http4s/pull/1977)
* Make `Referer` header extractable [#1984](https://github.com/http4s/http4s/pull/1984)
* Log server startup banner in a single call to prevent interspersion [#1985](https://github.com/http4s/http4s/pull/1985)
* Add support module for play-json [#1946](https://github.com/http4s/http4s/pull/1946)
* Introduce `TranslateUri` middleware, which checks the prefix of the service it's translating against the request. Deprecated `URITranslation`, which chopped the prefix length without checking for a match. [#1964](https://github.com/http4s/http4s/pull/1964)
* Dependency upgrades:
  * cats-1.2.0
  * metrics-4.0.3
  * okhttp-3.11.0
  * prometheus-client-0.5.0
  * scodec-bits-1.1.6

# v0.18.15 (2018-07-05)
* Bugfix for `AutoSlash` Middleware in Router [#1937](https://github.com/http4s/http4s/pull/1937)
* Add `StaticHeaders` middleware that appends static headers to a service [#1939](https://github.com/http4s/http4s/pull/1939)

# v0.19.0-M1 (2018-07-04)
* Add accumulating version of circe `EntityDecoder` [#1647](https://github.com/http4/http4s/1647)
* Add ETag support to `StaticFile` [#1652](https://github.com/http4s/http4s/pull/1652)
* Reintroduce the option for fallthrough for authenticated services [#1670](https://github.com/http4s/http4s/pull/1670)
* Separate `Cookie` into `RequestCookie` and `ResponseCookie` [#1676](https://github.com/http4s/http4s/pull/1676)
* Add `Eq[Uri]` instance [#1688](https://github.com/http4s/http4s/pull/1688)
* Deprecate `Message#withBody` in favor of `Message#withEntity`.  The latter returns a `Message[F]` rather than an `F[Message[F]]`. [#1694](https://github.com/http4s/http4s/pull/1694)
* Myriad new `Arbitrary` and `Cogen` instances [#1677](https://github.com/http4s/http4s/pull/1677)
* Add non-deprecated `LocationResponseGenerator` functions [#1715](https://github.com/http4s/http4s/pull/1715)
* Relax constraint on `Router` from `Sync` to `Monad` [#1723](https://github.com/http4s/http4s/pull/1723)
* Drop scodec-bits dependency [#1732](https://github.com/http4s/http4s/pull/1732)
* Add `Show[ETag]` instance [#1749](https://github.com/http4s/http4s/pull/1749)
* Replace `fs2.Scheduler` with `cats.effect.Timer` in `Retry` [#1754](https://github.com/http4s/http4s/pull/1754)
* Remove `Sync` constraint from `EntityEncoder[Multipart]` [#1762](https://github.com/http4s/http4s/pull/1762)
* Generate `MediaType`s from [MimeDB](https://github.com/jshttp/mime-db) [#1770](https://github.com/http4s/http4s/pull/1770)
  * Continue phasing out `Renderable` with `MediaRange` and `MediaType`.
  * Media types are now namespaced by main type.  This reduces backticks.  For example, `` MediaType.`text/plain` `` is replaced by `MediaType.text.plain`.
* Remove `Registry`. [#1770](https://github.com/http4s/http4s/pull/1770)
* Deprecate `HttpService`: [#1693](https://github.com/http4s/http4s/pull/1693)
  * Introduces an `Http[F[_], G[_]]` type alias
  * `HttpService` is replaced by `HttpRoutes`, which is an `Http[OptionT[F, ?], ?]`.  `HttpRoutes.of` replaces `HttpService` constructor from `PartialFunction`s.
  * `HttpApp` is an `Http[F, F]`, representing a total HTTP function.
* Add `BlockingHttp4sServlet` for use in Google App Engine and Servlet 2.5 containers.  Rename `Http4sServlet` to `AsyncHttp4sServlet`. [#1830](https://github.com/http4s/http4s/pull/1830)
* Generalize `Logger` middleware to log with `String => Unit` instead of `logger.info(_)` [#1839](https://github.com/http4s/http4s/pull/1839)
* Generalize `AutoSlash` middleware to work on `Kleisli[F, Request[G], B]` given `MonoidK[F]` and `Functor[G]`. [#1885](https://github.com/http4s/http4s/pull/1885)
* Generalize `CORS` middleware to work on `Http[F, G]` given `Applicative[F]` and `Functor[G]`. [#1889](https://github.com/http4s/http4s/pull/1889)
* Generalize `ChunkAggegator` middleware to work on `Kleisli[F, A, Response[G]]` given `G ~> F`, `FlatMap[F]`, and `Sync[G]`. [#1886](https://github.com/http4s/http4s/pull/1886)
* Generalize `EntityLimiter` middleware to work on `Kleisli[F, Request[G], B]`. [#1892](https://github.com/http4s/http4s/pull/1892)
* Generalize `HSTS` middleware to work on `Kleisli[F, A, Response[G]]` given `Functor[F]` and `Functor[G]`. [#1893](https://github.com/http4s/http4s/pull/1893)
* Generalize `UrlFormLifter` middleware to work on `Kleisli[F, Request[G], Response[G]]` given `G ~> F`, `Sync[F]` and `Sync[G]`.  [#1894](https://github.com/http4s/http4s/pull/1894)
* Generalize `Timeout` middleware to work on `Kleisli[F, A, Response[G]]` given `Concurrent[F]` and `Timer[F]`. [#1899](https://github.com/http4s/http4s/pull/1899)
* Generalize `VirtualHost` middleware to work on `Kleisli[F, Request[G], Response[G]]` given `Applicative[F]`.  [#1902](https://github.com/http4s/http4s/pull/1902)
* Generalize `URITranslate` middleware to work on `Kleisli[F, Request[G], B]` given `Functor[G]`.  [#1895](https://github.com/http4s/http4s/pull/1895)
* Generalize `CSRF` middleware to work on `Kleisli[F, Request[G], Response[G]]` given `Sync[F]` and `Applicative[G]`.  [#1909](https://github.com/http4s/http4s/pull/1909)
* Generalize `ResponseLogger` middleware to work on `Kleisli[F, A, Response[F]]` given `Effect[F]`.  [#1916](https://github.com/http4s/http4s/pull/1916)
* Make `Logger`, `RequestLogger`, and `ResponseLogger` work on `HttpApp[F]` so a `Response` is guaranteed unless the service raises an error [#1916](https://github.com/http4s/http4s/pull/1916)
* Rename `RequestLogger.apply0` and `ResponseLogger.apply0` to `RequestLogger.apply` and `ResponseLogger.apply`.  [#1837](https://github.com/http4s/http4s/pull/1837)
* Move `org.http4s.server.ServerSoftware` to `org.http4s.ServerSoftware` [#1884](https://github.com/http4s/http4s/pull/1884)
* Fix `Uncompressible` and `NotBinary` flags in `MimeDB` generator. [#1900](https://github.com/http4s/http4s/pull/1884)
* Generalize `DefaultHead` middleware to work on `Http[F, G]` given `Functor[F]` and `MonoidK[F]` [#1903](https://github.com/http4s/http4s/pull/1903)
* Generalize `GZip` middleware to work on `Http[F, G]` given `Functor[F]` and `Functor[G]` [#1903](https://github.com/http4s/http4s/pull/1903)
* `jawnDecoder` takes a `RawFacade` instead of a `Facade`
* Change `BasicCredentials` extractor to return `(String, String)` [#1924](https://github.com/http4s/http4s/1925)
* `Effect` constraint relaxed to `Sync`:
  * `Logger.logMessage`
* `Effect` constraint relaxed to `Async`:
  * `JavaNetClient`
* `Effect` constraint changed to `Concurrent`:
  * `Logger` (client and server)
  * `RequestLogger` (client and server)
  * `ResponseLogger` (client and server)
  * `ServerBuilder#serve` (moved to abstract member of `ServerBuilder`)
* `Effect` constraint strengthened to `ConcurrentEffect`:
  * `AsyncHttpClient`
  * `BlazeBuilder`
  * `JettyBuilder`
  * `TomcatBuilder`
* Implicit `ExecutionContext` removed from:
  * `RequestLogger` (client and server)
  * `ResponseLogger` (client and server)
  * `ServerBuilder#serve`
  * `ArbitraryInstances.arbitraryEntityDecoder`
  * `ArbitraryInstances.cogenEntity`
  * `ArbitraryInstances.cogenEntityBody`
  * `ArbitraryInstances.cogenMessage`
  * `JavaNetClient`
* Implicit `Timer` added to:
  * `AsyncHttpClient`
  * `JavaNetClient.create`
* `Http4sWsStage` removed from public API
* Removed charset for argonaut instances [#1914](https://github.com/http4s/http4s/pull/1914)
* Dependency upgrades:
  * async-http-client-2.4.9
  * blaze-0.14.0-M3
  * cats-effect-1.0.0-RC2
  * circe-0.10.0-M1
  * fs2-1.0.0-M1
  * fs2-reactive-streams-0.6.0
  * jawn-0.12.1
  * jawn-fs2-0.13.0-M1
  * prometheus-0.4.0
  * scala-xml-1.1.0

# v0.18.14 (2018-07-03)
* Add `CirceEntityCodec` to provide an implicit `EntityEncoder` or `EntityDecoder` from an `Encoder` or `Decoder`, respectively. [#1917](https://github.com/http4s/http4s/pull/1917)
* Add a client backend based on `java.net.HttpURLConnection`.  Note that this client blocks and is primarily intended for use in a REPL. [#1882](https://github.com/http4s/http4s/pull/1882)
* Dependency upgrades:
  * jetty-9.4.11
  * tomcat-9.0.10

# v0.18.13 (2018-06-22)
* Downcase type in `MediaRange` generator [#1907](https://github.com/http4s/http4s/pull/1907)
* Fixed bug where `PoolManager` would try to dequeue from an empty queue [#1922](https://github.com/http4s/http4s/pull/1922)
* Dependency upgrades:
  * argonaut-6.2.2
  * fs2-0.10.5

# v0.18.12 (2018-05-28)
* Deprecated `Part.empty` [#1858](https://github.com/http4s/http4s/pull/1858)
* Log requests with an unconsumed body [#1861](https://github.com/http4s/http4s/pull/1861)
* Log requests when the service returns `None` or raises an error [#1875](https://github.com/http4s/http4s/pull/1875)
* Support streaming parsing of multipart and storing large parts as temp files [#1865](https://github.com/http4s/http4s/pull/1865)
* Add an OkHttp client, with HTTP/2 support [#1864](https://github.com/http4s/http4s/pull/1864)
* Add `Host` header to requests to `Client.fromHttpService` if the request URI is absolute [#1874](https://github.com/http4s/http4s/pull/1874)
* Log `"service returned None"` or `"service raised error"` in service `ResponseLogger` when the service does not produce a successful response [#1879](https://github.com/http4s/http4s/pull/1879)
* Dependency upgrades:
  * jetty-9.4.10.v20180503
  * json4s-3.5.4
  * tomcat-9.0.8

# v0.18.11 (2018-05-10)
* Prevent zero-padding of servlet input chunks [#1835](https://github.com/http4s/http4s/pull/1835)
* Fix deadlock in client loggers.  `RequestLogger.apply` and `ResponseLogger.apply` are each replaced by `apply0` to maintain binary compatibility. [#1837](https://github.com/http4s/http4s/pull/1837)
* New `http4s-boopickle` module supports entity codecs through `boopickle.Pickler` [#1826](https://github.com/http4s/http4s/pull/1826)
* Log as much of the response as is consumed in the client. Previously, failure to consume the entire body prevented any part of the body from being logged. [#1846](https://github.com/http4s/http4s/pull/1846)
* Dependency upgrades:
  * prometheus-client-java-0.4.0

# v0.18.10 (2018-05-03)
* Eliminate dependency on Macro Paradise and macro-compat [#1816](https://github.com/http4s/http4s/pull/1816)
* Add `Logging` middleware for client [#1820](https://github.com/http4s/http4s/pull/1820)
* Make blaze-client tick wheel executor lazy [#1822](https://github.com/http4s/http4s/pull/1822)
* Dependency upgrades:
  * cats-effect-0.10.1
  * fs2-0.10.4
  * specs2-4.1.0

# v0.18.9 (2018-04-17)
* Log any exceptions when writing the header in blaze-server for HTTP/1 [#1781](https://github.com/http4s/http4s/pull/1781)
* Drain the response body (thus running its finalizer) when there is an error writing a servlet header or body [#1782](https://github.com/http4s/http4s/pull/1782)
* Clean up logging of errors thrown by services. Prevents the possible swallowing of errors thrown during `renderResponse` in blaze-server and `Http4sServlet` [#1783](https://github.com/http4s/http4s/pull/1783)
* Fix `Uri.Scheme` parser for schemes beginning with `http` other than `https` [#1790](https://github.com/http4s/http4s/pull/1790)
* Fix blaze-client to reset the connection start time on each invocation of the `F[DisposableResponse]`. This fixes the "timeout after 0 milliseconds" error. [#1792](https://github.com/http4s/http4s/pull/1792)
* Depdency upgrades:
  * blaze-0.12.13
  * http4s-websocket-0.2.1
  * specs2-4.0.4
  * tomcat-9.0.7

# v0.18.8 (2018-04-11)
* Improved ScalaDoc for BlazeBuilder [#1775](https://github.com/http4s/http4s/pull/1775)
* Added a stream constructor for async-http-client [#1776](https://github.com/http4s/http4s/pull/1776)
* http4s-prometheus-server-metrics project created. Prometheus Metrics middleware implemented for metrics on http4s server. Exposes an HttpService ready to be scraped by Prometheus, as well pairing to a CollectorRegistry for custom metric registration. [#1778](https://github.com/http4s/http4s/pull/1778)

# v0.18.7 (2018-04-04)
* Multipart parser defaults to fields interpreted as utf-8. [#1767](https://github.com/http4s/http4s/pull/1767)

# v0.18.6 (2018-04-03)
* Fix parsing of multipart bodies across chunk boundaries. [#1764](https://github.com/http4s/http4s/pull/1764)

# v0.18.5 (2018-03-28)
* Add `&` extractor to http4s-dsl. [#1758](https://github.com/http4s/http4s/pull/1758)
* Deprecate `EntityEncoder[F, Future[A]]`.  The `EntityEncoder` is strict in its argument, which causes any side effect of the `Future` to execute immediately.  Wrap your `future` in `IO.fromFuture(IO(future))` instead. [#1759](https://github.com/http4s/http4s/pull/1759)
* Dependency upgrades:
  * circe-0.9.3

# v0.18.4 (2018-03-23)
* Deprecate old `Timeout` middleware methods in favor of new ones that use `FiniteDuration` and cancel timed out effects [#1725](https://github.com/http4s/http4s/pull/1725)
* Add `expectOr` methods to client for custom error handling on failed expects [#1726](https://github.com/http4s/http4s/pull/1726)
* Replace buffered multipart parser with a streaming version. Deprecate all uses of fs2-scodec. [#1727](https://github.com/http4s/http4s/pull/1727)
* Dependency upgrades:
  * blaze-0.12.2
  * fs2-0.10.3
  * log4s-1.6.1
  * jetty-9.4.9.v20180320

# v0.18.3 (2018-03-17)
* Remove duplicate logging in pool manager [#1683](https://github.com/http4s/http4s/pull/1683)
* Add request/response specific properties to logging [#1709](https://github.com/http4s/http4s/pull/1709)
* Dependency upgrades:
  * async-http-client-2.0.39
  * cats-1.1.0
  * cats-effect-0.10
  * circe-0.9.2
  * discipline-0.9.0
  * jawn-fs2-0.12.2
  * log4s-1.5.0
  * twirl-1.3.15

# v0.18.2 (2018-03-09)
* Qualify reference to `identity` in `uriLiteral` macro [#1697](https://github.com/http4s/http4s/pull/1697)
* Make `Retry` use the correct duration units [#1698](https://github.com/http4s/http4s/pull/1698)
* Dependency upgrades:
  * tomcat-9.0.6

# v0.18.1 (2018-02-27)
* Fix the rendering of trailer headers in blaze [#1629](https://github.com/http4s/http4s/pull/1629)
* Fix race condition between shutdown and parsing in Http1SeverStage [#1675](https://github.com/http4s/http4s/pull/1675)
* Don't use filter in `Arbitrary[``Content-Length``]` [#1678](https://github.com/http4s/http4s/pull/1678)
* Opt-in fallthrough for authenticated services [#1681](https://github.com/http4s/http4s/pull/1681)
* Dependency upgrades:
  * cats-effect-0.9
  * fs2-0.10.2
  * fs2-reactive-streams-0.5.1
  * jawn-fs2-0.12.1
  * specs2-4.0.3
  * tomcat-9.0.5
  * twirl-1.3.4

# v0.18.0 (2018-02-01)
* Add `filename` method to `Part`
* Dependency upgrades:
  * fs2-0.10.0
  * fs2-reactive-streams-0.5.0
  * jawn-fs2-0.12.0

# v0.18.0-M9 (2018-01-26)
* Emit Exit Codes On Server Shutdown [#1638](https://github.com/http4s/http4s/pull/1638) [#1637](https://github.com/http4s/http4s/pull/1637)
* Register Termination Signal and Frame in Http4sWSStage [#1631](https://github.com/http4s/http4s/pull/1631)
* Trailer Headers Are Now Being Emitted Properly [#1629](https://github.com/http4s/http4s/pull/1629)
* Dependency Upgrades:
   * alpn-boot-8.1.12.v20180117
   * circe-0.9.1
   * fs2-0.10.0-RC2
   * fs2-reactive-streams-0.3.0
   * jawn-fs2-0.12.0-M7
   * metrics-4.0.2
   * tomcat-9.0.4

# v0.18.0-M8 (2018-01-05)
* Dependency Upgrades:
   * argonaut-6.2.1
   * circe-0.9.0
   * fs2-0.10.0-M11
   * fs2-reactive-streams-0.2.8
   * jawn-fs2-0.12.0-M6
   * cats-1.0.1
   * cats-effect-0.8

# v0.18.0-M7 (2017-12-23)
* Relax various typeclass constraints from `Effect` to `Sync` or `Async`. [#1587](https://github.com/http4s/http4s/pull/1587)
* Operate on `Segment` instead of `Chunk` [#1588](https://github.com/http4s/http4s/pull/1588)
   * `EntityDecoder.collectBinary` and `EntityDecoder.binary` now
     return `Segment[Byte, Unit]` instead of `Chunk[Byte]`.
   * Add `EntityDecoder.binaryChunk`.
   * Add `EntityEncoder.segmentEncoder`.
   * `http4sMonoidForChunk` replaced by `http4sMonoidForSegment`.
* Add new generators for core RFC 2616 types. [#1593](https://github.com/http4s/http4s/pull/1593)
* Undo obsolete copying of bytes in `StaticFile.fromURL`. [#1202](https://github.com/http4s/http4s/pull/1202)
* Optimize conversion of `Chunk.Bytes` and `ByteVectorChunk` to `ByteBuffer. [#1602](https://github.com/http4s/http4s/pull/1602)
* Rename `read` to `send` and `write` to `receive` in websocket model. [#1603](https://github.com/http4s/http4s/pull/1603)
* Remove `MediaRange` mutable `Registry` and add `HttpCodec[MediaRange]` instance [#1597](https://github.com/http4s/http4s/pull/1597)
* Remove `Monoid[Segment[A, Unit]]` instance, which is now provided by fs2. [#1609](https://github.com/http4s/http4s/pull/1609)
* Introduce `WebSocketBuilder` to build `WebSocket` responses.  Allows headers (e.g., `Sec-WebSocket-Protocol`) on a successful handshake, as well as customization of the response to failed handshakes. [#1607](https://github.com/http4s/http4s/pull/1607)
* Don't catch exceptions thrown by `EntityDecoder.decodeBy`. Complain loudly in logs about exceptions thrown by `HttpService` rather than raised in `F`. [#1592](https://github.com/http4s/http4s/pull/1592)
* Make `abnormal-terminations` and `service-errors` Metrics names plural. [#1611](https://github.com/http4s/http4s/pull/1611)
* Refactor blaze client creation. [#1523](https://github.com/http4s/http4s/pull/1523)
   * `Http1Client.apply` returns `F[Client[F]]`
   * `Http1Client.stream` returns `Stream[F, Client[F]]`, bracketed to shut down the client.
   * `PooledHttp1Client` constructor is deprecated, replaced by the above.
   * `SimpleHttp1Client` is deprecated with no direct equivalent.  Use `Http1Client`.
* Improve client timeout and wait queue handling
   * `requestTimeout` and `responseHeadersTimeout` begin from the submission of the request.  This includes time spent in the wait queue of the pool. [#1570](https://github.com/http4s/http4s/pull/1570)
   * When a connection is `invalidate`d, try to unblock a waiting request under the same key.  Previously, the wait queue would only be checked on recycled connections.
   * When the connection pool is closed, allow connections in the wait queue to complete.
* Changes to Metrics middleware. [#1612](https://github.com/http4s/http4s/pull/1612)
   * Decrement the active requests gauge when no request matches
   * Don't count non-matching requests as 4xx in case they're composed with other services.
   * Don't count failed requests as 5xx in case they're recovered elsewhere.  They still get recorded as `service-error`s.
* Dependency upgrades:
   * async-http-client-2.0.38
   * cats-1.0.0.RC2
   * circe-0.9.0-M3
   * fs2-0.10.0-M10
   * fs2-jawn-0.12.0-M5
   * fs2-reactive-streams-0.2.7
   * scala-2.10.7 and scala-2.11.12

# v0.18.0-M6 (2017-12-08)
* Tested on Java 9.
* `Message.withContentType` now takes a `Content-Type` instead of an
  ``Option[`Content-Type`]``.  `withContentTypeOption` takes an `Option`,
  and `withoutContentType` clears it.
* `QValue` has an `HttpCodec` instance
* `AuthMiddleware` never falls through.  See
  [#1530](https://github.com/http4s/http4s/pull/1530) for more.
* `ContentCoding` is no longer a `Registry`, but has an `HttpCodec`
  instance.
* Render a banner on server startup.  Customize by calling
  `withBanner(List[String])` or `withoutBanner` on the
  `ServerBuilder`.
* Parameterize `isZippable` as a predicate of the `Response` in `GZip`
  middleware.
* Add constant for `application/vnd.api+json` MediaType.
* Limit memory consumption in `GZip` middleware
* Add `handleError`, `handleErrorWith`, `bimap`, `biflatMap`,
  `transform`, and `transformWith` to `EntityDecoder`.
* `org.http4s.util.StreamApp` and `org.http4s.util.ExitCode` are
  deprecated in favor of `fs2.StreamApp` and `fs2.StreamApp.ExitCode`,
  based on what was in http4s.
* Dependency upgrades:
  * fs2-0.10.0-M9
  * fs2-reactive-streams-0.2.6
  * jawn-fs2-0.12.0-M4
  * specs2-4.0.2

# v0.17.6 (2017-12-05)
* Fix `StaticFile` to serve files larger than `Int.MaxValue` bytes
* Dependency upgrades:
  * tomcat-8.5.24

# v0.16.6 (2017-12-04)
* Add a CSRF server middleware
* Fix `NullPointerException` when starting a Tomcat server related to `docBase`
* Log version info and server address on server startup
* Dependency upgrades:
  * jetty-9.4.8.v20171121
  * log4s-1.4.0
  * scalaz-7.2.17
  * twirl-1.3.13

# v0.18.0-M5 (2017-11-02)
* Introduced an `HttpCodec` type class that represents a type that can round
  trip to and from a `String`.  `Uri.Scheme` and `TransferCoding` are the first
  implementors, with more to follow.  Added an `HttpCodecLaws` to http4s-testing.
* `Uri.Scheme` is now its own type instead of a type alias.
* `TransferCoding` is no longer a case class. Its `coding` member is now a
  `String`, not a `CIString`. Its companion is no longer a
  `Registry`.
* Introduced `org.http4s.syntax.literals`, which contains a `StringContext` forAll
  safely constructing a `Uri.Scheme`.  More will follow.
* `org.http4s.util.StreamApp.ExitCode` moved to `org.http4s.util.ExitCode`
* Changed `AuthService[F[_], T]` to `AuthService[T, F[_]]` to support
  partial unification when combining services as a `SemigroupK`.
* Unseal the `MessageFailure` hierarchy. Previous versions of http4s had a
  `GenericParsingFailure`, `GenericDecodeFailure`, and
  `GenericMessageBodyFailure`. This was not compatible with the parameterized
  effect introduced in v0.18. Now, `MessageFailure` is unsealed, so users
  wanting precise control over the default `toHttpResponse` can implement their
  own failure conditions.
* `MessageFailure` now has an `Option[Throwable]` cause.
* Removed `KleisliInstances`. The `SemigroupK[Kleisli[F, A, ?]]` is now provided
  by cats.  Users should no longer need to import `org.http4s.implicits._` to
  get `<+>` composition of `HttpService`s
* `NonEmptyList` extensions moved from `org.http4s.util.nonEmptyList` to
  `org.http4s.syntax.nonEmptyList`.
* There is a classpath difference in log4s version between blaze and http4s in this
  milestone that will be remedied in M6. We believe these warnings are safe.
* Dependency upgrades:
  * cats-1.0.0-RC1
  * fs2-0.10.0-M8
  * fs2-reactive-streams-0.2.5

# v0.18.0-M4 (2017-10-12)
* Syntax for building requests moved from `org.http4s.client._` to
  `org.http4s.client.dsl.Http4sClientDsl[F]`, with concrete type `IO`
  available as `org.http4s.client.dsl.io._`.  This is consistent with
  http4s-dsl for servers.
* Change `StreamApp` to return a `Stream[F, ExitCode]`. The first exit code
  returned by the stream is the exit code of the JVM. This allows custom exit
  codes, and eases dead code warnings in certain constructions that involved
  mapping over `Nothing`.
* `AuthMiddleware.apply` now takes an `Kleisli[OptionT[F, ?], Request[F], T]`
  instead of a `Kleisli[F, Request[F], T]`.
* Set `Content-Type` header on default `NotFound` response.
* Merges from v0.16.5 and v0.17.5.
* Remove mutable map that backs `Method` registry. All methods in the IANA
  registry are available through `Method.all`. Custom methods should be memoized
  by other means.
* Adds an `EntityDecoder[F, Array[Byte]]` and `EntityDecoder[F, Array[Char]]`
  for symmetry with provided `EntityEncoder` instances.
* Adds `Arbitrary` instances for `Headers`, `EntityBody[F]` (currently just
  single chunk), `Entity[F]`, and `EntityEncoder[F, A]`.
* Adds `EntityEncoderLaws` for `EntityEncoder`.
* Adds `EntityCodecLaws`.  "EntityCodec" is not a type in http4s, but these
  laws relate an `EntityEncoder[F, A]` to an `EntityDecoder[F, A]`.
* There is a classpath difference in log4s version between blaze and http4s in this
  milestone that will be remedied in M6. We believe these warnings are safe.

# v0.17.5 (2017-10-12)
* Merges only.

# v0.16.5 (2017-10-11)
* Correctly implement sanitization of dot segments in static file paths
  according to RFC 3986 5.2.4. Most importantly, this fixes an issue where `...`
  is reinterpreted as `..` and can escape the root of the static file service.

# v0.18.0-M3 (2017-10-04)
* Merges only.
* There is a classpath difference in log4s version between blaze and http4s in this
  milestone that will be remedied in M6. We believe these warnings are safe.

# v0.17.4 (2017-10-04)
* Fix reading of request body in non-blocking servlet backend. It was previously
  only reading the first byte of each chunk.
* Dependency upgrades:
  * fs2-reactive-streams-0.1.1

# v0.16.4 (2017-10-04)
* Backport removal `java.xml.bind` dependency from `GZip` middleware,
  to play more nicely with Java 9.
* Dependency upgrades:
  * metrics-core-3.2.5
  * tomcat-8.0.23
  * twirl-1.3.12

# v0.18.0-M2 (2017-10-03)
* Use http4s-dsl with any effect type by either:
    *  extend `Http4sDsl[F]`
    *  create an object that extends `Http4sDsl[F]`, and extend that.
    *  `import org.http4s.dsl.io._` is still available for those who
      wish to specialize on `cats.effect.IO`
* Remove `Semigroup[F[MaybeResponse[F]]]` constraint from
  `BlazeBuilder`.
* Fix `AutoSlash` middleware when a service is mounted with a prefix.
* Publish internal http4s-parboiled2 as a separate module.  This does
  not add any new third-party dependencies, but unbreaks `sbt
  publishLocal`.
* Add `Request.from`, which respects `X-Fowarded-For` header.
* Make `F` in `EffectMessageSyntax` invariant
* Add `message.decodeJson[A]` syntax to replace awkward `message.as(implicitly,
  jsonOf[A])`. Brought into scope by importing one of the following, based on
  your JSON library of choice.
  * `import org.http4s.argonaut._`
  * `import org.http4s.circe._`
  * `import org.http4s.json4s.jackson._`
  * `import org.http4s.json4s.native._`
* `AsyncHttpClient.apply` no longer takes a `bufferSize`.  It is made
  irrelevant by fs2-reactive-streams.
* `MultipartParser.parse` no longer takes a `headerLimit`, which was unused.
* Add `maxWaitQueueLimit` (default 256) and `maxConnectionsPerRequestKey`
  (default 10) to `PooledHttp1Client`.
* Remove private implicit `ExecutionContext` from `StreamApp`. This had been
  known to cause diverging implicit resolution that was hard to debug.
* Shift execution of the routing of the `HttpService` to the `ExecutionContext`
  provided by the `JettyBuilder` or `TomcatBuilder`. Previously, it only shifted
  the response task and stream. This was a regression from v0.16.
* Add two utility execution contexts. These may be used to increase throughput
  as the server builder's `ExecutionContext`. Blocking calls on routing may
  decrease fairness or even deadlock your service, so use at your own risk:
  * `org.http4s.util.execution.direct`
  * `org.http4s.util.execution.trampoline`
* Deprecate `EffectRequestSyntax` and `EffectResponseSyntax`. These were
  previously used to provide methods such as `.putHeaders` and `.withBody`
  on types `F[Request]` and `F[Response]`.  As an alternative:
  * Call `.map` or `.flatMap` on `F[Request]` and `F[Response]` to get access
    to all the same methods.
  * Variadic headers have been added to all the status code generators in
    `Http4sDsl[F]` and method generators in `import org.http4s.client._`.
    For example:
    *  `POST(uri, urlForm, Header("Authorization", "Bearer s3cr3t"))`
    *  ``Ok("This will have an html content type!", `Content-Type`(`text/html`))``
* Restate `HttpService[F]` as a `Kleisli[OptionT[F, ?], Request[F], Response[F]]`.
* Similarly, `AuthedService[F]` as a `Kleisli[OptionT[F, ?], AuthedRequest[F], Response[F]]`.
* `MaybeResponse` is removed, because the optionality is now expressed through
  the `OptionT` in `HttpService`. Instead of composing `HttpService` via a
  `Semigroup`, compose via a `SemigroupK`. Import `org.http4s.implicits._` to
  get a `SemigroupK[HttpService]`, and chain services as `s1 <+> s2`. We hope to
  remove the need for `org.http4s.implicits._` in a future version of cats with
  [issue 1428](https://github.com/typelevel/cats/issues/1428).
* The `Service` type alias is deprecated in favor of `Kleisli`.  It used to represent
  a partial application of the first type parameter, but since version 0.18, it is
  identical to `Kleisli.
* `HttpService.lift`, `AuthedService.lift` are deprecated in favor of `Kleisli.apply`.
* Remove `java.xml.bind` dependency from `GZip` middleware to avoid an
  extra module dependency in Java 9.
* Upgraded dependencies:
    *  jawn-fs2-0.12.0-M2
    *  log4s-1.4.0
* There is a classpath difference in log4s version between blaze and http4s in this
  milestone that will be remedied in M6. We believe these warnings are safe.

# v0.17.3 (2017-10-02)
* Shift execution of HttpService to the `ExecutionContext` provided by the
  `BlazeBuilder` when using HTTP/2. Previously, it only shifted the response
  task and body stream.

# v0.16.3 (2017-09-29)
* Fix `java.io.IOException: An invalid argument was supplied` on blaze-client
  for Windows when writing an empty sequence of `ByteBuffer`s.
* Set encoding of `captureWriter` to UTF-8 instead of the platform default.
* Dependency upgrades:
  * blaze-0.12.9

# v0.17.2 (2017-09-25)
* Remove private implicit strategy from `StreamApp`. This had been known to
  cause diverging implicit resolution that was hard to debug.
* Shift execution of HttpService to the `ExecutionContext` provided by the
  `BlazeBuilder`. Previously, it only shifted the response stream. This was a
  regression from 0.16.
* Split off http4s-parboiled2 module as `"org.http4s" %% "parboiled"`. There are
  no externally visible changes, but this simplifies and speeds the http4s
  build.

# v0.16.2 (2017-09-25)
* Dependency patch upgrades:
  * async-http-client-2.0.37
  * blaze-0.12.8: changes default number of selector threads to
    from `2 * cores + 1` to `max(4, cores + 1)`.
  * jetty-9.4.7.v20170914
  * tomcat-8.5.21
  * twirl-1.3.7

# v0.17.1 (2017-09-17)
* Fix bug where metrics were not captured in `Metrics` middleware.
* Pass `redactHeadersWhen` argument from `Logger` to `RequestLogger`
  and `ResponseLogger`.

# v0.16.1 (2017-09-17)
* Publish our fork of parboiled2 as http4s-parboiled2 module.  It's
  the exact same internal code as was in http4s-core, with no external
  dependencies. By publishing an extra module, we enable a
  `publishLocal` workflow.
* Charset fixes:
  * Deprecate `CharsetRange.isSatisfiedBy` in favor of
    and ```Accept-Charset`.isSatisfiedBy`` in favor of
    ```Accept-Charset`.satisfiedBy``.
  * Fix definition of `satisfiedBy` to respect priority of
    ```Charset`.*``.
  * Add `CharsetRange.matches`.
* ContentCoding fixes:
  * Deprecate `ContentCoding.satisfiedBy` and
    `ContentCoding.satisfies` in favor of ```Accept-Encoding`.satisfiedBy``.
  * Deprecate ```Accept-Encoding`.preferred``, which has no reasonable
    interpretation in the presence of splats.
  * Add ```Accept-Language`.qValue``.
  * Fix definition of `satisfiedBy` to respect priority of
    `ContentCoding.*`.
  * Add `ContentCoding.matches` and `ContentCoding.registered`.
  * Add `Arbitrary[ContentCoding]` and ```Arbitrary[`Accept-Encoding`]``
    instances.
* LanguageTag fixes:
  * Deprecate `LanguageTag.satisfiedBy` and
    `LanguageTag.satisfies` in favor of ```Accept-Language`.satisfiedBy``.
  * Fix definition of `satisfiedBy` to respect priority of
    `LanguageTag.*` and matches of a partial set of subtags.
  * Add `LanguageTag.matches`.
  * Deprecate `LanguageTag.withQuality` in favor of new
    `LanguageTag.withQValue`.
  * Deprecate ```Accept-Language`.preferred``, which has no reasonable
    interpretation in the presence of splats.
  * Add ```Accept-Language`.qValue``.
  * Add `Arbitrary[LanguageTag]` and ```Arbitrary[`Accept-Language`]``
    instances.

# v0.17.0 (2017-09-01)
* Honor `Retry-After` header in `Retry` middleware.  The response will
  not be retried until the maximum of the backoff strategy and any
  time specified by the `Retry-After` header of the response.
* The `RetryPolicy.defaultRetriable` only works for methods guaranteed
  to not have a body.  In fs2, we can't introspect the stream to
  guarantee that it can be rerun.  To retry requests for idempotent
  request methods, use `RetryPolicy.unsafeRetriable`.  To retry
  requests regardless of method, use
  `RetryPolicy.recklesslyRetriable`.
* Fix `Logger` middleware to render JSON bodies as text, not as a hex
  dump.
* `MultipartParser.parse` returns a stream of `ByteVector` instead of
  a stream of `Byte`. This perserves chunking when parsing into the
  high-level `EntityDecoder[Multipart]`, and substantially improves
  performance on large files.  The high-level API is not affected.

# v0.16.0 (2017-09-01)
* `Retry` middleware takes a `RetryPolicy` instead of a backoff
  strategy.  A `RetryPolicy` is a function of the request, the
  response, and the number of attempts.  Wrap the previous `backoff`
  in `RetryPolicy {}` for compatible behavior.
* Expose a `Part.fileData` constructor that accepts an `EntityBody`.

# v0.17.0-RC3 (2017-08-29)
* In blaze-server, when doing chunked transfer encoding, flush the
  header as soon as it is available.  It previously buffered until the
  first chunk was available.

# v0.16.0-RC3 (2017-08-29)
* Add a `responseHeaderTimeout` property to `BlazeClientConfig`.  This
  measures the time between the completion of writing the request body
  to the reception of a complete response header.
* Upgraded dependencies:
    *  async-http-client-2.0.35

# v0.18.0-M1 (2017-08-24)

This release is the product of a long period of parallel development
across different foundation libraries, making a detailed changelog
difficult.  This is a living document, so if any important points are
missed here, please send a PR.

The most important change in http4s-0.18 is that the effect type is
parameterized.  Where previous versions were specialized on
`scalaz.concurrent.Task` or `fs2.Task`, this version supports anything
with a `cats.effect.Effect` instance.  The easiest way to port an
existing service is to replace your `Task` with `cats.effect.IO`,
which has a similar API and is already available on your classpath.
If you prefer to bring your own effect, such as `monix.eval.Task` or
stick to `scalaz.concurrent.Task` or put a transformer on `IO`, that's
fine, too!

The parameterization chanages many core signatures throughout http4s:
- `Request` and `Response` become `Request[F[_]]` and
  `Response[F[_]]`.  The `F` is the effect type of the body (i.e.,
  `Stream[F, Byte]`), or what the body `.run`s to.
- `HttpService` becomes `HttpService[F[_]]`, so that the service
  returns an `F[Response[F]]`.  Instead of constructing with
  `HttpService { ... }`, we now declare the effect type of the
  service, like `HttpService[IO] { ... }`.  This determines the type
  of request and response handled by the service.
- `EntityEncoder[A]` and `EntityDecoder[A]` are now
  `EntityEncoder[F[_], A]` and `EntityDecoder[F[_], A]`, respectively.
  These act as a codec for `Request[F]` and `Response[F]`.  In practice,
  this change tends to be transparent in the DSL.
- The server builders now take an `F` parameter, which needs to match
  the services mounted to them.
- The client now takes an `F` parameter, which determines the requests
  and responses it handles.

Several dependencies are upgraded:
- cats-1.0.0.MF
- circe-0.9.0-M1
- fs2-0.10.0-M6
- fs2-reactive-streams-0.2.2
- jawn-fs2-0.12.0-M1

# v0.17.0-RC2 (2017-08-24)
* Remove `ServiceSyntax.orNotFound(a: A): Task[Response]` in favor of
  `ServiceSyntax.orNotFound: Service[Request, Response]`

# v0.16.0-RC2 (2017-08-24)
* Move http4s-blaze-core from `org.http4s.blaze` to
  `org.http4s.blazecore` to avoid a conflict with the non-http4s
  blaze-core module.
* Change `ServiceOps` to operate on a `Service[?, MaybeResponse]`.
  Give it an `orNotFound` that returns a `Service`.  The
  `orNotFound(a: A)` overload is left for compatibility with Scala
  2.10.
* Build with Lightbend compiler instead of Typelevel compiler so we
  don't expose `org.typelevel` dependencies that are incompatible with
  ntheir counterparts in `org.scala-lang`.
* Upgraded dependencies:
    *  blaze-0.12.7 (fixes eviction notice in http4s-websocket)
    *  twirl-1.3.4

# v0.17.0-RC1 (2017-08-16)
* Port `ChunkAggregator` to fs2
* Add logging middleware
* Standardize on `ExecutionContext` over `Strategy` and `ExecutorService`
* Implement `Age` header
* Fix `Client#toHttpService` to not dispose until the body is consumed
* Add a buffered implementation of `EntityDecoder[Multipart]`
* In async-http-client, don't use `ReactiveStreamsBodyGenerator` unless there is
  a body to transmit. This fixes an `IllegalStateException: unexpected message
  type`
* Add `HSTS` middleware
* Add option to serve pre-gzipped resources
* Add RequestLogging and ResponseLogging middlewares
* `StaticFile` options return `OptionT[Task, ?]`
* Set `Content-Length` or `Transfer-Encoding: chunked` header when serving
  from a URL
* Explicitly close `URLConnection``s if we are not reading the contents
* Upgrade to:
    *  async-http-client-2.0.34
    *  fs2-0.9.7
    *  metrics-core-3.2.4
    *  scodec-bits-1.1.5

# v0.16.0-RC1 (2017-08-16)
* Remove laziness from `ArbitraryInstances`
* Support an arbitrary predicate for CORS allowed origins
* Support `Access-Control-Expose-Headers` header for CORS
* Fix thread safety issue in `EntityDecoder[XML]`
* Support IPV6 headers in `X-Forwarded-For`
* Add `status` and `successful` methods to client
* Overload `client.fetchAs` and `client.streaming` to accept a `Task[Request]`
* Replace `Instant` with `HttpDate` to avoid silent truncation and constrain
  to dates that are legally renderable in HTTP.
* Fix bug in hash code of `CIString`
* Update `request.pathInfo` when changing `request.withUri`. To keep these
  values in sync, `request.copy` has been deprecated, but copy constructors
  based on `with` have been added.
* Remove `name` from `AttributeKey`.
* Add `withFragment` and `withoutFragment` to `Uri`
* Construct `Content-Length` with `fromLong` to ensure validity, and
  `unsafeFromLong` when you can assert that it's positive.
* Add missing instances to `QueryParamDecoder` and `QueryParamEncoder`.
* Add `response.cookies` method to get a list of cookies from `Set-Cookie`
  header.  `Set-Cookie` is no longer a `Header.Extractable`, as it does
  not adhere to the HTTP spec of being concatenable by commas without
  changing semantics.
* Make servlet `HttpSession` available as a request attribute in servlet
  backends
* Fix `Part.name` to return the name from the `Content-Disposition` header
  instead of the name _of_ the `Content-Disposition` header. Accordingly, it is
  no longer a `CIString`
* `Request.toString` and `Response.toString` now redact sensitive headers. A
  method to redact arbitrary headers is added to `Headers`.
* `Retry-After` is now modeled as a `Either[HttpDate, Long]` to reflect either
  an http-date or delta-seconds value.
* Look for index.html in `StaticFile` when rendering a directory instead of
  returning `401 Unauthorized`.
* Limit dates to a minimum of January 1, 1900, per RFC.
* Add `serviceErrorHandler` to `ServerBuilder` to allow pluggable error handlers
  when a server backend receives a failed task or a thrown Exception when
  invoking a service. The default calls `toHttpResponse` on `MessageFailure` and
  closes the connection with a `500 InternalServerError` on other non-fatal
  errors.  Fatal errors are left to the server.
* `FollowRedirect` does not propagate sensitive headers when redirecting to a
  different authority.
* Add Content-Length header to empty response generators
* Upgraded dependencies:
    *  async-http-client-2.0.34
    *  http4s-websocket-0.2.0
    *  jetty-9.4.6.v20170531
    *  json4s-3.5.3
    *  log4s-1.3.6
    *  metrics-core-3.2.3
    *  scala-2.12.3-bin-typelevel-4
    *  scalaz-7.2.15
    *  tomcat-8.5.20

# v0.15.16 (2017-07-20)
* Backport rendering of details in `ParseFailure.getMessage`

# ~~v0.15.15 (2017-07-20)~~
* Oops. Same as v0.15.14.

# v0.15.14 (2017-07-10)
* Close parens in `Request.toString`
* Use "message" instead of "request" in message body failure messages
* Add `problem+json` media type
* Tolerate `[` and `]` in queries parsing URIs. These characters are parsed, but
  percent-encoded.

# v0.17.0-M3 (2017-05-27)
* Fix file corruption issue when serving static files from the classpath

# v0.16.0-M3 (2017-05-25)
* Fix `WebjarService` so it matches assets.
* `ServerApp` overrides `process` to leave a single abstract method
* Add gzip trailer in `GZip` middleware
* Upgraded dependencies:
    *  circe-0.8.0
    *  jetty-9.4.5.v20170502
    *  scalaz-7.2.13
    *  tomcat-8.5.15
* `ProcessApp` uses a `Process[Task, Nothing]` rather than a
  `Process[Task, Unit]`
* `Credentials` is split into `Credentials.AuthParams` for key-value pairs and
  `Credentials.Token` for legacy token-based schemes.  `OAuthBearerToken` is
  subsumed by `Credentials.Token`.  `BasicCredentials` no longer extends
  `Credentials`, but is extractable from one.  This model permits the
  definition of other arbitrary credential schemes.
* Add `fromSeq` constructor to `UrlForm`
* Allow `WebjarService` to pass on methods other than `GET`.  It previously
  threw a `MatchError`.

# v0.15.13 (2017-05-25)
* Patch-level upgrades to dependencies:
    *  async-http-client-2.0.32
    *  blaze-0.12.6 (fixes infinite loop in some SSL handshakes)
    *  jetty-9.3.19.v20170502
    *  json4s-3.5.2
    *  tomcat-8.0.44

# v0.15.12 (2017-05-11)
* Fix GZip middleware to render a correct stream

# v0.17.0-M2 (2017-04-30)
* `Timeout` middleware takes an implicit `Scheduler` and
  `ExecutionContext`
* Bring back `http4s-async-client`, based on `fs2-reactive-stream`
* Restore support for WebSockets

# v0.16.0-M2 (2017-04-30)
* Upgraded dependencies:
    *  argonaut-6.2
    *  jetty-9.4.4.v20170414
    *  tomcat-8.5.14
* Fix `ProcessApp` to terminate on process errors
* Set `secure` request attribute correctly in blaze server
* Exit with code `-1` when `ProcessApp` fails
* Make `ResourceService` respect `If-Modified-Since`
* Rename `ProcessApp.main` to `ProcessApp.process` to avoid overload confusio
* Avoid intermediate String allocation in Circe's `jsonEncoder`
* Adaptive `EntityDecoder[Json]` for circe: works directly from a ByteBuffer for
  small bodies, and incrementally through jawn for larger.
* Capture more context in detail message of parse errors

# v0.15.11 (2017-04-29)
* Upgrade to blaze-0.12.5 to pick up fix for `StackOverflowError` in
  SSL handshake

# v0.15.10 (2017-04-28)
* Patch-level upgrades to dependencies
* argonaut-6.2
* scalaz-7.2.12
* Allow preambles and epilogues in multipart bodies
* Limit multipart headers to 40 kilobytes to avoid unbounded buffering
  of long lines in a header
* Remove `' '` and `'?'` from alphabet for generated multipart
  boundaries, as these are not token characters and are known to cause
  trouble for some multipart implementations
* Fix multipart parsing for unlucky input chunk sizes

# v0.15.9 (2017-04-19)
* Terminate `ServerApp` even if the server fails to start
* Make `ResourceService` respect `If-Modified-Since`
* Patch-level upgrades to dependencies:
* async-http-client-2.0.31
* jetty-9.3.18.v20170406
* json4s-3.5.1
* log4s-1.3.4
* metrics-core-3.1.4
* scalacheck-1.13.5
* scalaz-7.1.13 or scalaz-7.2.11
* tomcat-8.0.43

# v0.17.0-M1 (2017-04-08)
* First release on cats and fs2
    *  All scalaz types and typeclasses replaced by cats equivalengts
	* `scalaz.concurrent.Task` replaced by `fs2.Task`
	* `scalaz.stream.Process` replaced by `fs2.Stream`
* Roughly at feature parity with v0.16.0-M1. Notable exceptions:
	* Multipart not yet supported
	* Web sockets not yet supported
	* Client retry middleware can't check idempotence of requests
	* Utilties in `org.http4s.util.io` not yet ported

# v0.16.0-M1 (2017-04-08)
* Fix type of `AuthedService.empty`
* Eliminate `Fallthrough` typeclass.  An `HttpService` now returns
  `MaybeResponse`, which can be a `Response` or `Pass`.  There is a
  `Semigroup[MaybeResponse]` instance that allows `HttpService`s to be
  chained as a semigroup.  `service orElse anotherService` is
  deprecated in favor of `service |+| anotherService`.
* Support configuring blaze and Jetty servers with a custom
  `SSLContext`.
* Upgraded dependencies for various modules:
    *  async-http-client-2.0.31
    *  circe-0.7.1
    *  jetty-9.4.3.v20170317
    *  json4s-3.5.1
    *  logback-1.2.1
    *  log4s-1.3.4
    *  metrics-3.2.0
    *  scalacheck-1.13.5
    *  tomcat-8.0.43
* Deprecate `EntityEncoder[ByteBuffer]` and
  `EntityEncoder[CharBuffer]`.
* Add `EntityDecoder[Unit]`.
* Move `ResponseClass`es into `Status`.
* Use `SSLContext.getDefault` by default in blaze-client.  Use
  `BlazeServerConfig.insecure` to ignore certificate validity.  But
  please don't.
* Move `CIString` syntax to `org.http4s.syntax`.
* Bundle an internal version of parboiled2.  This decouples core from
  shapeless, allowing applications to use their preferred version of
  shapeless.
* Rename `endpointAuthentication` to `checkEndpointAuthentication`.
* Add a `WebjarService` for serving files out of web jars.
* Implement `Retry-After` header.
* Stop building with `delambdafy` on Scala 2.11.
* Eliminate finalizer on `BlazeConnection`.
* Respond OK to CORS pre-flight requests even if the wrapped service
  does not return a successful response.  This is to allow `CORS`
  pre-flight checks of authenticated services.
* Deprecate `ServerApp` in favor of `org.http4s.util.ProcessApp`.  A
  `ProcessApp` is easier to compose all the resources a server needs via
  `Process.bracket`.
* Implement a `Referer` header.

# v0.15.8 (2017-04-06)
* Cache charset lookups to avoid synchronization.  Resolution of
  charsets is synchronized, with a cache size of two.  This avoids
  the synchronized call on the HTTP pool.
* Strip fragment from request target in blaze-client.  An HTTP request
  target should not include the fragment, and certain servers respond
  with a `400 Bad Request` in their presence.

# v0.15.7 (2017-03-09)
* Change default server and client executors to a minimum of four
  threads.
* Bring scofflaw async-http-client to justice for its brazen
  violations of Reactive Streams Rule 3.16, requesting of a null
  subscription.
* Destroy Tomcat instances after stopping, so they don't hold the port
* Deprecate `ArbitraryInstances.genCharsetRangeNoQuality`, which can
  cause deadlocks
* Patch-level upgrades to dependencies:
    *  async-http-client-2.0.30
    *  jetty-9.3.16.v20170120
    *  logback-1.1.11
    *  metrics-3.1.3
    *  scala-xml-1.0.6
    *  scalaz-7.2.9
    *  tomcat-8.0.41
    *  twirl-1.2.1

# v0.15.6 (2017-03-03)
* Log unhandled MessageFailures to `org.http4s.server.message-failures`

# v0.15.5 (2017-02-20)
* Allow services wrapped in CORS middleware to fall through
* Don't log message about invalid CORS headers when no `Origin` header present
* Soften log about invalid CORS headers from info to debug

# v0.15.4 (2017-02-12)
* Call `toHttpResponse` on tasks failed with `MessageFailure`s from
  `HttpService`, to get proper 4xx handling instead of an internal
  server error.

# v0.15.3 (2017-01-17)
* Dispose of redirect responses in `FollowRedirect`. Fixes client deadlock under heavy load
* Refrain from logging headers with potentially sensitive info in blaze-client
* Add `hashCode` and `equals` to `Headers`
* Make `challenge` in auth middlewares public to facilitate composing multiple auth mechanisms
* Fix blaze-client detection of stale connections

# v0.15.2 (2016-12-29)
* Add helpers to add cookies to requests

# v0.12.6 (2016-12-29)
* Backport rendering of details in `ParseFailure.getMessage`

# ~~v0.12.5 (2016-12-29)~~
* ~~Backport rendering of details in `ParseFailure.getMessage`~~ Oops.

# v0.15.1 (2016-12-20)
* Fix GZip middleware to fallthrough non-matching responses
* Fix UnsupportedOperationException in `Arbitrary[Uri]`
* Upgrade to Scala 2.12.1 and Scalaz 7.2.8

# v0.15.0 (2016-11-30)
* Add support for Scala 2.12
* Added `Client.fromHttpService` to assist with testing.
* Make all case classes final where possible, sealed where not.
* Codec for Server Sent Events (SSE)
* Added JSONP middleware
* Improve Expires header to more easily build the header and support parsing of the header
* Replce lazy `Raw.parsed` field with a simple null check
* Added support for Zipkin headers
* Eliminate response attribute for detecting fallthrough response.
  The fallthrough response must be `Response.fallthrough`.
* Encode URI path segments created with `/`
* Introduce `AuthedRequest` and `AuthedService` types.
* Replace `CharSequenceEncoder` with `CharBufferEncoder`, assuming
  that `CharBuffer` and `String` are the only `CharSequence`s one
  would want to encode.
* Remove `EnittyEncoder[Char]` and `EntityEncoder[Byte]`.  Send an
  array, buffer, or String if you want this.
* Add `DefaultHead` middleware for `HEAD` implementation.
* Decouple `http4s-server` from Dropwizard Metrics.  Metrics code is
  in the new `http4s-metrics` module.
* Allow custom scheduler for timeout middleware.
* Add parametric empty `EntityEncoder` and `EntityEncoder[Unit]`.
* Replace unlawful `Order[CharsetRange]` with `Equal[CharsetRange]`.
* Auth middlewares renamed `BasicAuth` and `DigestAuth`.
* `BasicAuth` passes client password to store instead of requesting
  password from store.
* Remove realm as an argument to the basic and digest auth stores.
* Basic and digest auth stores return a parameterized type instead of
  just a String username.
* Upgrade to argonaut-6.2-RC2, circe-0.6.1, json4s-3.5.0

# v0.14.11 (2016-10-25)
* Fix expansion of `uri` and `q` macros by qualifying with `_root_`

# v0.14.10 (2016-10-12)
* Include timeout type and duration in blaze client timeouts

# v0.14.9 (2016-10-09)
* Don't use `"null"` as query string in servlet backends for requests without a query string

# v0.14.8 (2016-10-04)
* Allow param names in UriTemplate to have encoded, reserved parameters
* Upgrade to blaze-0.12.1, to fix OutOfMemoryError with direct buffers
* Upgrade to Scalaz 7.1.10/7.2.6
* Upgrade to Jetty 9.3.12
* Upgrade to Tomcat 8.0.37

# v0.14.7 (2016-09-25)
* Retry middleware now only retries requests with idempotent methods
  and pure bodies and appropriate status codes
* Fix bug where redirects followed when an effectful chunk (i.e., `Await`) follows pure ones.
* Don't uppercase two hex digits after "%25" when percent encoding.
* Tolerate invalid percent-encodings when decoding.
* Omit scoverage dependencies from POM

# v0.14.6 (2016-09-11)
* Don't treat `Kill`ed responses (i.e., HEAD requests) as abnormal
  termination in metrics

# v0.14.5 (2016-09-02)
* Fix blaze-client handling of HEAD requests

# v0.14.4 (2016-08-29)
* Don't render trailing "/" for URIs with empty paths
* Avoid calling tail of empty list in `/:` extractor

# v0.14.3 (2016-08-24)
* Follow 301 and 302 responses to POST with a GET request.
* Follow all redirect responses to HEAD with a HEAD request.
* Fix bug where redirect response is disposed prematurely even if not followed.
* Fix bug where payload headers are sent from original request when
  following a redirect with a GET or HEAD.
* Return a failed task instead of throwing when a client callback
  throws an exception. Fixes a resource leak.
* Always render `Date` header in GMT.
* Fully support the three date formats specified by RFC 7231.
* Always specify peer information in blaze-client SSL engines
* Patch upgrades to latest async-http-client, jetty, scalaz, and scalaz-stream

# v0.14.2 (2016-08-10)
* Override `getMessage` in `UnexpectedStatus`

# v0.14.1 (2016-06-15)
* Added the possibility to specify custom responses to MessageFailures
* Address issue with Retry middleware leaking connections
* Fixed the status code for a semantically invalid request to `422 UnprocessableEntity`
* Rename `json` to `jsonDecoder` to reduce possibility of implicit shadowing
* Introduce the `ServerApp` trait
* Deprectate `onShutdown` and `awaitShutdown` in `Server`
* Support for multipart messages
* The Path extractor for Long now supports negative numbers
* Upgrade to scalaz-stream-0.8.2(a) for compatibility with scodec-bits-1.1
* Downgrade to argonaut-6.1 (latest stable release) now that it cross builds for scalaz-7.2
* Upgrade parboiled2 for compatibility with shapeless-2.3.x

# ~~v0.14.0 (2016-06-15)~~
* Recalled. Use v0.14.1 instead.

# v0.13.3 (2016-06-15)
* Address issue with Retry middleware leaking connections.
* Pass the reason string when setting the `Status` for a successful `ParseResult`.

# v0.13.2 (2016-04-13)
* Fixes the CanBuildFrom for RequestCookieJar to avoid duplicates.
* Update version of jawn-parser which contains a fix for Json decoding.

# v0.13.1 (2016-04-07)
* Remove implicit resolution of `DefaultExecutor` in blaze-client.

# v0.13.0 (2016-03-29)
* Add support for scalaz-7.2.x (use version 0.13.0a).
* Add a client backed based on async-http-client.
* Encode keys when rendering a query string.
* New entity decoder based on json4s' extract.
* Content-Length now accepts a Long.
* Upgrade to circe-0.3, json4s-3.3, and other patch releases.
* Fix deadlocks in blaze resulting from default executor on single-CPU machines.
* Refactor `DecodeFailure` into a new `RequestFailure` hierarchy.
* New methods for manipulating `UrlForm`.
* All parsed headers get a `parse` method to construct them from their value.
* Improve error message for unsupported media type decoding error.
* Introduce `BlazeClientConfig` class to simplify client construction.
* Unify client executor service semantics between blaze-client and async-http-client.
* Update default response message for UnsupportedMediaType failures.
* Add a `lenient` flag to blazee configuration to accept illegal characters in headers.
* Remove q-value from `MediaRange` and `MediaType`, replaced by `MediaRangeAndQValue`.
* Add `address` to `Server` trait.
* Lazily construct request body in Servlet NIO to support HTTP 100.
* Common operations pushed down to `MessageOps`.
* Fix loop in blaze-client when no connection can be established.
* Privatize most of the blaze internal types.
* Enable configuration of blaze server parser lengths.
* Add trailer support in blaze client.
* Provide an optional external executor to blaze clients.
* Fix Argonaut string interpolation

# v0.12.4 (2016-03-10)
* Fix bug on rejection of invalid URIs.
* Do not send `Transfer-Encoding` or `Content-Length` headers for 304 and others.
* Don't quote cookie values.

# v0.12.3 (2016-02-24)
* Upgrade to jawn-0.8.4 to fix decoding escaped characters in JSON.

# v0.12.2 (2016-02-22)
* ~~Upgrade to jawn-0.8.4 to fix decoding escaped characters in JSON.~~ Oops.

# v0.12.1 (2016-01-30)
* Encode keys as well as values when rendering a query.
* Don't encode '?' or '/' when encoding a query.

# v0.12.0 (2016-01-15)
* Refactor the client API for resource safety when not reading the entire body.
* Rewrite client connection pool to support maximum concurrent
  connections instead of maximum idle connections.
* Optimize body collection for better connection keep-alive rate.
* Move `Service` and `HttpService`, because a `Client` can be viewed as a `Service`.
* Remove custom `DateTime` in favor of `java.time.Instant`.
* Support status 451 Unavailable For Legal Reasons.
* Various blaze-client optimizations.
* Don't let Blaze `IdentityWriter` write more than Content-Length bytes.
* Remove `identity` `Transfer-Encoding`, which was removed in HTTP RFC errata.
* In blaze, `requireClose` is now the return value of `writeEnd`.
* Remove body from `Request.toString` and `Response.toString`.
* Move blaze parser into its own class.
* Trigger a disconnect if an ignored body is too long.
* Configurable thread factories for happier profiling.
* Fix possible deadlock in default client execution context.

# v0.11.3 (2015-12-28)
* Blaze upgrade to fix parsing HTTP responses without a reason phrase.
* Don't write more than Content-Length bytes in blaze.
* Fix infinite loop in non-blocking Servlet I/O.
* Never write a response body on HEAD requests to blaze.
* Add missing `'&'` between multivalued k/v pairs in `UrlFormCodec.encode`

# v0.11.2 (2015-12-04)
* Fix stack safety issue in async servlet I/O.
* Reduce noise from timeout exceptions in `ClientTimeoutStage`.
* Address file descriptor leaks in blaze-client.
* Fix `FollowRedirect` middleware for 303 responses.
* Support keep-alives for client requests with bodies.

# v0.11.1 (2015-11-29)
* Honor `connectorPoolSize` and `bufferSize` parameters in `BlazeBuilder`.
* Add convenient `ETag` header constructor.
* Wait for final chunk to be written before closing the async context in non-blocking servlet I/O.
* Upgrade to jawn-streamz-0.7.0 to use scalaz-stream-0.8 across the board.

# v0.11.0 (2015-11-20)
* Upgrade to scalaz-stream 0.8
* Add Circe JSON support module.
* Add ability to require content-type matching with EntityDecoders.
* Cleanup blaze-client internals.
* Handle empty static files.
* Add ability to disable endpoint authentication for the blaze client.
* Add charset encoding for Argonaut JSON EntityEncoder.

# v0.10.1 (2015-10-07)
* Processes render data in chunked encoding by default.
* Incorporate type name into error message of QueryParam.
* Comma separate Access-Control-Allow-Methods header values.
* Default FallThrough behavior inspects for the FallThrough.fallthroughKey.

# v0.10.0 (2015-09-03)
* Replace `PartialService` with the `Fallthrough` typeclass and `orElse` syntax.
* Rename `withHeaders` to `replaceAllHeaders`
* Set https endpoint identification algorithm when possible.
* Stack-safe `ProcessWriter` in blaze.
* Configureable number of connector threads and buffer size in blaze-server.

# v0.9.3 (2015-08-27)
* Trampoline recursive calls in blaze ProcessWriter.
* Handle server hangup and body termination correctly in blaze client.

# v0.9.2 (2015-08-26)
* Bump http4s-websockets to 1.0.3 to properly decode continuation opcode.
* Fix metrics incompatibility when using Jetty 9.3 backend.
* Preserve original headers when appending as opposed to quoting.

# v0.8.5 (2015-08-26)
* Preserve original headers when appending as opposed to quoting.
* Upgrade to jawn-0.8.3 to avoid transitive dependency on GPL2 jmh

# v0.9.1 (2015-08-19)
* Fix bug in servlet nio handler.

# v0.9.0 (2015-08-15)
* Require Java8.
* `StaticFile` uses the filename extension exclusively to determine media-type.
* Add `/` method to `Uri`.
* Add `UrlFormLifter` middleware to aggregate url-form parameters with the query parameters.
* Add local address information to the `Request` type.
* Add a Http method 'or' (`|`) extractor.
* Add `VirtualHost` middleware for serving multiple sites from one server.
* Add websocket configuration to the blaze server builder.
* Redefine default timeout status code to 500.
* Redefine the `Service` arrow result from `Task[Option[_]]` to `Task[_]`.
* Don't extend `AllInstances` with `Http4s` omnibus import object.
* Use UTF-8 as the default encoding for text bodies.
* Numerous bug fixes by numerous contributors!

# v0.8.4 (2015-07-13)
* Honor the buffer size parameter in gzip middleware.
* Handle service exceptions in servlet backends.
* Respect asyncTimeout in servlet backends.
* Fix prefix mounting bug in blaze-server.
* Do not apply CORS headers to unsuccessful OPTIONS requests.

# v0.8.3 (2015-07-02)
* Fix bug parsing IPv4 addresses found in URI construction.

# v0.8.2 (2015-06-22)
* Patch instrumented handler for Jetty to time async contexts correctly.
* Fix race condition with timeout registration and route execution in blaze client
* Replace `ConcurrentHashMap` with synchronized `HashMap` in `staticcontent` package.
* Fix static content from jars by avoiding `"//"` in path uris when serving static content.
* Quote MediaRange extensions.
* Upgrade to jawn-streamz-0.5.0 and blaze-0.8.2.
* Improve error handling in blaze-client.
* Respect the explicit default encoding passed to `decodeString`.

# v0.8.1 (2015-06-16)
* Authentication middleware integrated into the server package.
* Static content tools integrated into the server package.
* Rename HttpParser to HttpHeaderParser and allow registration and removal of header parsers.
* Make UrlForm EntityDecoder implicitly resolvable.
* Relax UrlForm parser strictness.
* Add 'follow redirect' support as a client middleware.
* Add server middleware for auto retrying uris of form '/foo/' as '/foo'.
* Numerous bug fixes.
* Numerous version bumps.

# ~~v0.8.0 (2015-06-16)~~
* Mistake.  Go straight to v0.8.1.

# v0.7.0 (2015-05-05)
* Add QueryParamMatcher to the dsl which returns a ValidationNel.
* Dsl can differentiate between '/foo/' and '/foo'.
* Added http2 support for blaze backend.
* Added a metrics middleware usable on all server backends.
* Websockets are now modeled by an scalaz.stream.Exchange.
* Add `User-Agent` and `Allow` header types and parsers.
* Allow providing a Host header to the blaze client.
* Upgrade to scalaz-stream-7.0a.
* Added a CORS middleware.
* Numerous bug fixes.
* Numerous version bumps.

# v0.6.5 (2015-03-29)
* Fix bug in Request URI on servlet backend with non-empty context or servlet paths.
* Allow provided Host header for Blaze requests.

# v0.6.4 (2015-03-15)
* Avoid loading javax.servlet.WriteListener when deploying to a servlet 3.0 container.

# ~~v0.6.3 (2015-03-15)~~
* Forgot to pull origin before releasing.  Use v0.6.4 instead.

# v0.6.2 (2015-02-27)
* Use the thread pool provided to the Jetty servlet builder.
* Avoid throwing exceptions when parsing headers.
* Make trailing slash insignificant in service prefixes on servlet containers.
* Fix mapping of servlet query and mount prefix.

# v0.6.1 (2015-02-04)
* Update to blaze-0.5.1
* Remove unneeded error message (90b2f76097215)
* GZip middleware will not throw an exception if the AcceptEncoding header is not gzip (ed1b2a0d68a8)

# v0.6.0 (2015-01-27)

* http4s-core
* Remove ResponseBuilder in favor of Response companion.
* Allow '';'' separators for query pairs.
* Make charset on Message an Option.
* Add a `flatMapR` method to EntityDecoder.
* Various enhancements to QueryParamEncoder and QueryParamDecoder.
* Make Query an IndexedSeq.
* Add parsers for Location and Proxy-Authenticate headers.
* Move EntityDecoder.apply to `Request.decode` and `Request.decodeWith`
* Move headers into `org.http4s.headers` package.
* Make UriTranslation respect scriptName/pathInfo split.
* New method to resolve relative Uris.
* Encode query and fragment of Uri.
* Codec and wrapper type for URL-form-encoded bodies.

* http4s-server
* Add SSL support to all server builders.

* http4s-blaze-server
* Add Date header to blaze-server responses.
* Close connection when error happens during body write in blaze-server.

* http4s-servlet
* Use asynchronous servlet I/O on Servlet 3.1 containers.
* ServletContext syntax for easy mounting in a WAR deployment.
* Support Dropwizard Metrics collection for servlet containers.

* http4s-jawn
* Empty strings are a JSON decoding error.

* http4s-argonaut
* Add codec instances for Argonaut's CodecJson.

* http4s-json4s
* Add codec instances for Json4s' Reader/Writer.

* http4s-twirl
* New module to support Twirl templates

* http4s-scala-xml
* Split scala-xml support into http4s-scala-xml module.
* Change inferred type of `scala.xml.Elem` to `application/xml`.

* http4s-client
* Support for signing oauth-1 requests in client.

* http4s-blaze-client
* Fix blaze-client when receiving HTTP1 response without Content-Length header.
* Change default blaze-client executor to variable size.
* Fix problem with blaze-client timeouts.

# v0.5.4 (2015-01-08)
* Upgrade to blaze 0.4.1 to fix header parsing issue in blaze http/1.x client and server.

# v0.5.3 (2015-01-05)
* Upgrade to argonaut-6.1-M5 to match jawn. [#157](https://github.com/http4s/http4s/issues/157)

# v0.5.2 (2015-01-02)
* Upgrade to jawn-0.7.2.  Old version of jawn was incompatible with argonaut. [#157](https://github.com/http4s/http4s/issues/157)

# v0.5.1 (2014-12-23)
* Include context path in calculation of scriptName/pathInfo. [#140](https://github.com/http4s/http4s/issues/140)
* Fix bug in UriTemplate for query params with multiple keys.
* Fix StackOverflowError in query parser. [#147](https://github.com/http4s/http4s/issues/147)
* Allow ';' separators for query pairs.

# v0.5.0 (2014-12-11)
* Client syntax has evloved and now will include Accept headers when used with EntityDecoder
* Parse JSON with jawn-streamz.
* EntityDecoder now returns an EitherT to make decoding failure explicit.
* Renamed Writable to EntityEncoder
* New query param typeclasses for encoding and decoding query strings.
* Status equality now discards the reason phrase.
* Match AttributeKeys as singletons.
* Added async timeout listener to servlet backends.
* Start blaze server asynchronously.
* Support specifying timeout and executor in blaze-client.
* Use NIO for encoding files.

# v0.4.2 (2014-12-01)
* Fix whitespace parsing in Authorization header [#87](https://github.com/http4s/http4s/issues/87)

# v0.4.1 (2014-11-20)
* `Uri.query` and `Uri.fragment` are no longer decoded. [#75](https://github.com/http4s/http4s/issues/75)

# v0.4.0 (2014-11-18)

* Change HttpService form a `PartialFunction[Request,Task[Response]]`
  to `Service[Request, Response]`, a type that encapsulates a `Request => Task[Option[Response]]`
* Upgrade to scalaz-stream-0.6a
* Upgrade to blaze-0.3.0
* Drop scala-logging for log4s
* Refactor ServerBuilders into an immutable builder pattern.
* Add a way to control the thread pool used for execution of a Service
* Modernize the Renderable/Renderer framework
* Change Renderable append operator from ~ to <<
* Split out the websocket codec and types into a seperate package
* Added ReplyException, an experimental way to allow an Exception to encode
  a default Response on for EntityDecoder etc.
* Many bug fixes and slight enhancements

# v0.3.0 (2014-08-29)

* New client API with Blaze implementation
* Upgrade to scalaz-7.1.0 and scalaz-stream-0.5a
* JSON Writable support through Argonaut and json4s.
* Add EntityDecoders for parsing bodies.
* Moved request and response generators to http4s-dsl to be more flexible to
  other frameworks'' syntax needs.
* Phased out exception-throwing methods for the construction of various
  model objects in favor of disjunctions and macro-enforced literals.
* Refactored imports to match the structure followed by [scalaz](https://github.com/scalaz/scalaz).

# v0.2.0 (2014-07-15)

* Scala 2.11 support
* Spun off http4s-server module. http4s-core is neutral between server and
the future client.
* New builder for running Blaze, Jetty, and Tomcat servers.
* Configurable timeouts in each server backend.
* Replace Chunk with scodec.bits.ByteVector.
* Many enhancements and bugfixes to URI type.
* Drop joda-time dependency for slimmer date-time class.
* Capitalized method names in http4s-dsl.

# v0.1.0 (2014-04-15)

* Initial public release.
