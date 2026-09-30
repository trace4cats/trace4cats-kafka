# Trace4Cats Kafka integration layer

Integration for Trace4Cats and FS2 Kafka

---

- [Installation](#installation)
- [Usage](#usage)
- [Contributing](#contributing)

## Installation

Add the following line to your `build.sbt` file:

```sbt
libraryDependencies += "io.janstenpickle" %% "trace4cats-kafka-client" % "0.15.0-RC2"
```

The library is published for Scala versions: `2.13` and `3`.

## Usage

Tracing a consumer stream continues the trace from the headers of each record and adds a
`kafka.receive` span:

```scala
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
