# Trace4Cats Kafka integration layer

@DESCRIPTION@

```scala mdoc:toc
```

## Installation

Add the following line to your `build.sbt` file:

```sbt
libraryDependencies += "@ORGANIZATION@" %% "trace4cats-kafka-client" % "@VERSION@"
```

The library is published for Scala versions: @SUPPORTED_SCALA_VERSIONS@.

## Usage

Tracing a consumer stream continues the trace from the headers of each record and adds a
`kafka.receive` span:

```scala mdoc:silent
import cats.data.Kleisli
import cats.effect.IO
import fs2.Stream
import fs2.kafka.CommittableConsumerRecord
import trace4cats.EntryPoint
import trace4cats.Span
import trace4cats.kafka.syntax._

type Traced[A] = Kleisli[IO, Span[IO], A]

def consume(
    stream: Stream[IO, CommittableConsumerRecord[IO, String, String]],
    entryPoint: EntryPoint[IO]
) = stream.inject[Traced](entryPoint)
```

## Contributing

This project supports the [Scala Code of Conduct](https://typelevel.org/code-of-conduct.html) and aims that its channels
(mailing list, Gitter, github, etc.) to be welcoming environments for everyone.
