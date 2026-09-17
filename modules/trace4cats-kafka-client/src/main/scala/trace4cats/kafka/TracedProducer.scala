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

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.Resource
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.~>

import fs2.kafka.KafkaProducer
import fs2.kafka.KeySerializer
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerResult
import fs2.kafka.TransactionalProducerRecords
import fs2.kafka.ValueSerializer
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.Metric
import org.apache.kafka.common.MetricName
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import trace4cats.ToHeaders
import trace4cats.Trace
import trace4cats.TraceHeaders
import trace4cats.context.Lift
import trace4cats.model.AttributeValue
import trace4cats.model.SpanKind

object TracedProducer {

  // Note the extra `G ~> F`: `imapK` needs both directions, and `Lift` only provides `F ~> G`.
  def create[F[_], G[_]: Trace, K, V](
      producer: KafkaProducer[F, K, V],
      toHeaders: ToHeaders = ToHeaders.standard
  )(implicit
      L: Lift[F, G],
      gk: G ~> F,
      F: MonadCancelThrow[F],
      G: MonadCancelThrow[G]
  ): KafkaProducer[G, K, V] = {
    val lifted = producer.imapK(L.liftK, gk)

    new KafkaProducer[G, K, V] {
      override def produce(records: ProducerRecords[K, V]): G[G[ProducerResult[K, V]]] =
        Trace[G].span("kafka.send", SpanKind.Producer) {
          Trace[G].headers(toHeaders).flatMap { traceHeaders =>
            NonEmptyList
              .fromList(records.map(_.topic).toList)
              .fold(Applicative[G].unit)(topics => Trace[G].put("topics", AttributeValue.StringList(topics))) >>
              L.lift(producer.produce(addHeaders(traceHeaders)(records))).map(L.lift)
          }
        }

      override def initTransactions: G[Unit] = lifted.initTransactions

      override def transaction: Resource[G, Unit] = lifted.transaction

      override def sendOffsetsToTransaction(
          offsets: Map[TopicPartition, OffsetAndMetadata],
          groupMetadata: ConsumerGroupMetadata
      ): G[Unit] = lifted.sendOffsetsToTransaction(offsets, groupMetadata)

      override def produceAndCommitTransactionally(
          records: TransactionalProducerRecords[G, K, V]
      ): G[ProducerResult[K, V]] = lifted.produceAndCommitTransactionally(records)

      override def produceTransactionally(records: ProducerRecords[K, V]): G[ProducerResult[K, V]] =
        lifted.produceTransactionally(records)

      override def metrics: G[Map[MetricName, Metric]] = lifted.metrics

      override def partitionsFor(topic: String): G[List[PartitionInfo]] = lifted.partitionsFor(topic)

      override def withSerializers[K2, V2](
          keySerializer: KeySerializer[G, K2],
          valueSerializer: ValueSerializer[G, V2]
      ): KafkaProducer[G, K2, V2] = lifted.withSerializers(keySerializer, valueSerializer)
    }
  }

  private[kafka] def addHeaders[P, K, V](
      traceHeaders: TraceHeaders
  )(records: ProducerRecords[K, V]): ProducerRecords[K, V] = {
    val msgHeaders = KafkaHeaders.converter.to(traceHeaders)
    ProducerRecords(records.map(r => r.withHeaders(r.headers.concat(msgHeaders))))
  }

}
