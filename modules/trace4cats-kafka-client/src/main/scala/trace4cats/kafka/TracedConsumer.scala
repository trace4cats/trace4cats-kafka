/*
 * Copyright (c) 2021-2026 Trace4Cats <https://github.com/trace4cats>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package trace4cats.kafka

import cats.Functor
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.functor._

import fs2.Stream
import fs2.kafka.CommittableConsumerRecord
import fs2.kafka.Timestamp
import trace4cats.ResourceKleisli
import trace4cats.Span
import trace4cats.SpanParams
import trace4cats.Trace
import trace4cats.context.Provide
import trace4cats.fs2.TracedStream
import trace4cats.fs2.syntax.Fs2StreamSyntax
import trace4cats.model.AttributeValue
import trace4cats.model.SpanKind

object TracedConsumer extends Fs2StreamSyntax {

  def inject[F[_]: MonadCancelThrow, G[_]: Functor: Trace, K, V](stream: Stream[F, CommittableConsumerRecord[F, K, V]])(
      k: ResourceKleisli[F, SpanParams, Span[F]]
  )(implicit P: Provide[F, G, Span[F]]): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
    stream
      .traceContinue(k, "kafka.receive", SpanKind.Consumer) { record =>
        KafkaHeaders.converter.from(record.record.headers)
      }
      .evalMapTrace { record =>
        Trace[G]
          .putAll(
            "topic"           -> record.record.topic,
            "create.time"     -> AttributeValue.LongValue(createTime(record.record.timestamp)),
            "log.append.time" -> AttributeValue.LongValue(logAppendTime(record.record.timestamp))
          )
          .as(record)
      }

  private def createTime(timestamp: Timestamp): Long = timestamp match {
    case Timestamp.CreateTime(value) => value
    case _                           => 0L
  }

  private def logAppendTime(timestamp: Timestamp): Long = timestamp match {
    case Timestamp.LogAppendTime(value) => value
    case _                              => 0L
  }

  // Lifting the stream into `G` means rebuilding each record's `CommittableOffset` in `G`, and
  // fs2-kafka 4.0.0 made that constructor package-private. Uncomment and release as soon as
  // https://github.com/typelevel/fs2-kafka/pull/1522, which adds `mapK`, is merged and released.
  //
  // def injectK[F[_]: MonadCancelThrow, G[_]: MonadCancelThrow: Trace, K, V](
  //   stream: Stream[F, CommittableConsumerRecord[F, K, V]]
  // )(
  //   k: ResourceKleisli[F, SpanParams, Span[F]]
  // )(implicit P: Provide[F, G, Span[F]]): TracedStream[G, CommittableConsumerRecord[G, K, V]] =
  //   inject[F, G, K, V](stream)(k).liftTrace[G].map(_.mapK(P.liftK))

}
