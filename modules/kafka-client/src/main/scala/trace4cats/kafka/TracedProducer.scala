package trace4cats.kafka

import cats.data.NonEmptyList
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Monad}
import fs2.kafka.{KafkaProducer, ProducerRecords, ProducerResult}
import trace4cats.context.Lift
import trace4cats.model.{AttributeValue, SpanKind}
import trace4cats.{ToHeaders, Trace, TraceHeaders}

object TracedProducer {
  def create[F[_], G[_]: Monad: Trace, K, V](
    producer: KafkaProducer[F, K, V],
    toHeaders: ToHeaders = ToHeaders.standard
  )(implicit L: Lift[F, G]): KafkaProducer[G, K, V] =
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
    }

  private[kafka] def addHeaders[P, K, V](
    traceHeaders: TraceHeaders
  )(records: ProducerRecords[K, V]): ProducerRecords[K, V] = {
    val msgHeaders = KafkaHeaders.converter.to(traceHeaders)
    ProducerRecords(records.map(r => r.withHeaders(r.headers.concat(msgHeaders))))
  }
}
