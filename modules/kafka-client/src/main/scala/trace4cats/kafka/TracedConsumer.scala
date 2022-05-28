package trace4cats.kafka

import cats.Functor
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.functor._
import fs2.Stream
import fs2.kafka.{CommittableConsumerRecord, CommittableOffset}
import trace4cats.context.Provide
import trace4cats.fs2.TracedStream
import trace4cats.fs2.syntax.Fs2StreamSyntax
import trace4cats.model.{AttributeValue, SpanKind}
import trace4cats.{ResourceKleisli, Span, SpanParams, Trace}

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
            "topic" -> record.record.topic,
            "consumer.group" -> AttributeValue.StringValue(record.offset.consumerGroupId.getOrElse("")),
            "create.time" -> AttributeValue.LongValue(record.record.timestamp.createTime.getOrElse(0L)),
            "log.append.time" -> AttributeValue.LongValue(record.record.timestamp.logAppendTime.getOrElse(0L)),
          )
          .as(record)
      }

  def injectK[F[_]: MonadCancelThrow, G[_]: MonadCancelThrow: Trace, K, V](
    stream: Stream[F, CommittableConsumerRecord[F, K, V]]
  )(
    k: ResourceKleisli[F, SpanParams, Span[F]]
  )(implicit P: Provide[F, G, Span[F]]): TracedStream[G, CommittableConsumerRecord[G, K, V]] = {
    def liftConsumerRecord(record: CommittableConsumerRecord[F, K, V]): CommittableConsumerRecord[G, K, V] =
      CommittableConsumerRecord[G, K, V](
        record.record,
        CommittableOffset(
          record.offset.topicPartition,
          record.offset.offsetAndMetadata,
          record.offset.consumerGroupId,
          _ => P.lift(record.offset.commit)
        )
      )

    inject[F, G, K, V](stream)(k).liftTrace[G].map(liftConsumerRecord)
  }

}
