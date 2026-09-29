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
import cats.data.WriterT
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.applicativeError._
import cats.syntax.functor._

import fs2.Stream
import fs2.kafka.CommittableConsumerRecord
import fs2.kafka.KafkaCommitter
import fs2.kafka.LiftedCommittableOffset
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
      .evalMapAccumulate(Map.empty[KafkaCommitter[F], String]) { case (groups, record) =>
        val committer = record.offset.committer

        groups.get(committer) match {
          case Some(group) => MonadCancelThrow[F].pure((groups, (record, group)))
          case None        =>
            committer.metadata.map(_.groupId).handleError(_ => "").map { group =>
              (groups.updated(committer, group), (record, group))
            }
        }
      }
      .map(_._2)
      .traceContinue(k, "kafka.receive", SpanKind.Consumer) { case (record, _) =>
        KafkaHeaders.converter.from(record.record.headers)
      }
      .evalMapTrace { case (record, group) =>
        Trace[G]
          .putAll(
            "topic"           -> record.record.topic,
            "consumer.group"  -> AttributeValue.StringValue(group),
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

  def injectK[F[_]: MonadCancelThrow, G[_]: MonadCancelThrow: Trace, K, V](
      stream: Stream[F, CommittableConsumerRecord[F, K, V]]
  )(
      k: ResourceKleisli[F, SpanParams, Span[F]]
  )(implicit P: Provide[F, G, Span[F]]): TracedStream[G, CommittableConsumerRecord[G, K, V]] = {
    val liftK = P.liftK

    WriterT(
      inject[F, G, K, V](stream)(k)
        .liftTrace[G]
        .run
        .mapAccumulate(Map.empty[KafkaCommitter[F], KafkaCommitter[G]]) { case (committers, (span, record)) =>
          val committer = committers.getOrElse(record.offset.committer, record.offset.committer.mapK(liftK))
          val offset    = LiftedCommittableOffset(record.offset, committer)

          (
            committers.updated(record.offset.committer, committer),
            (span, CommittableConsumerRecord(record.record, offset))
          )
        }
        .map(_._2)
    )
  }

}
