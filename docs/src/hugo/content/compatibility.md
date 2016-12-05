---
title: Compatibility
menu: "main"
weight: 2
---

* <span class="label label-primary">Stable</span> releases are
  recommended for production use and are maintained with backward,
  binary compatible bugfixes from the http4s team.

* <span class="label label-warning">Development</span> releases are
  published as snapshots to Sonatype by Travis CI.  API breakage may
  occur at any time.

* <span class="label label-default">EOL</span> releases are no longer
  actively maintained, but with a tale of woe and a pull request,
  someone may take pity on your soul.

<table class="table table-hover">
    <thead>
	<tr>
	    <th>http4s</th>
	    <th>Status</th>
	    <th>Scala 2.10</th>
	    <th>Scala 2.11</th>
	    <th>Scala 2.12</th>
	    <th>FP</th>
	    <th>Streaming</th>
	    <th>JDK</th>
	</tr>
    </thead>
    <tbody>
	<tr>
	    <td>topic/cats branch</td>
	    <td><span class="label label-warning">Development</span></td>
	    <td><i class="fa fa-ban"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td>cats-0.8</td>
	    <td>fs2-0.9</td>
	    <td>1.8+</td>
	</tr>
	<tr>
	    <td>0.15.0a</td>
	    <td><span class="label label-primary">Stable</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td>scalaz-7.2</td>
	    <td>scalaz-stream-0.8a</td>
	    <td>1.8+</td>
	</tr>
	<tr>
	    <td>0.15.0</td>
	    <td><span class="label label-primary">Stable</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	</tr>
    </tbody>
    <tbody>
	<tr>
	    <td>0.14.11a</td>
	    <td><span class="label label-primary">Stable</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8a</td>
	    <td>1.8+</td>
	</tr>
    </tbody>
    <tbody>
	<tr>
	    <td>0.14.11</td>
	    <td><span class="label label-primary">Stable</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.2</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	</tr>
    </tbody>
    <tbody>
	<tr>
	    <td>0.13.3a</td>
	    <td><span class="label label-default">EOL</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.2</td>
	    <td>scalaz-stream-0.8a</td>
	    <td>1.8+</td>
	</tr>
    </tbody>
    <tbody>
	<tr>
	    <td>0.13.3</td>
	    <td><span class="label label-default">EOL</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	</tr>
    </tbody>
    <tbody>
	<tr>
	    <td>0.12.4</td>
	    <td><span class="label label-default">EOL</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	</tr>
    </tbody>
    <tbody>
	<tr>
	    <td>0.11.3</td>
	    <td><span class="label label-default">EOL</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	</tr>
    </tbody>
    <tbody>
	<tr>
	    <td>0.10.1</td>
	    <td><span class="label label-default">EOL</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.7a</td>
	    <td>1.8+</td>
	</tr>
    </tbody>
    <tbody>
	<tr>
	    <td>0.9.3</td>
	    <td><span class="label label-default">EOL</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.7a</td>
	    <td>1.8+</td>
	</tr>
    </tbody>
    <tbody>
	<tr>
	    <td>0.8.6</td>
	    <td><span class="label label-default">EOL</span></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-check"></i></td>
	    <td><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.7a</td>
	    <td>1.7+</td>
	</tr>
    </tbody>
</table>
