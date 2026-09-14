package trace4cats.kafka.syntax

import cats.Functor
import cats.effect.kernel.MonadCancelThrow

import fs2.Stream
import fs2.kafka.CommittableConsumerRecord
import trace4cats._
import trace4cats.context.Provide
import trace4cats.fs2.TracedStream
import trace4cats.kafka.TracedConsumer

trait Fs2KafkaSyntax {

  // Disabled along with `TracedProducer.create`, which fs2-kafka 4.x cannot express. Uncomment and
  // release as soon as https://github.com/typelevel/fs2-kafka/pull/1522 is merged and released.
  //
  // implicit class ProducerSyntax[F[_], K, V](producer: KafkaProducer[F, K, V]) {
  //   def liftTrace[G[_]](
  //     toHeaders: ToHeaders = ToHeaders.standard
  //   )(implicit
  //     L: Lift[F, G],
  //     gk: G ~> F,
  //     F: MonadCancelThrow[F],
  //     G: MonadCancelThrow[G],
  //     T: Trace[G]
  //   ): KafkaProducer[G, K, V] =
  //     TracedProducer.create[F, G, K, V](producer, toHeaders)
  // }

  implicit class ConsumerSyntax[F[_], K, V](consumerStream: Stream[F, CommittableConsumerRecord[F, K, V]]) {

    def inject[G[_]](ep: EntryPoint[F])(implicit
        P: Provide[F, G, Span[F]],
        F: MonadCancelThrow[F],
        G: Functor[G],
        T: Trace[G]
    ): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
      TracedConsumer.inject[F, G, K, V](consumerStream)(ep.toKleisli)

    def trace[G[_]](k: ResourceKleisli[F, SpanParams, Span[F]])(implicit
        P: Provide[F, G, Span[F]],
        F: MonadCancelThrow[F],
        G: Functor[G],
        T: Trace[G]
    ): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
      TracedConsumer.inject[F, G, K, V](consumerStream)(k)

    // Disabled along with `TracedConsumer.injectK`. Uncomment and release as soon as
    // https://github.com/typelevel/fs2-kafka/pull/1522 is merged and released.
    //
    // def injectK[G[_]](ep: EntryPoint[F])(implicit
    //   P: Provide[F, G, Span[F]],
    //   F: MonadCancelThrow[F],
    //   G: MonadCancelThrow[G],
    //   trace: Trace[G]
    // ): TracedStream[G, CommittableConsumerRecord[G, K, V]] =
    //   TracedConsumer.injectK[F, G, K, V](consumerStream)(ep.toKleisli)
    //
    // def traceK[G[_]](k: ResourceKleisli[F, SpanParams, Span[F]])(implicit
    //   P: Provide[F, G, Span[F]],
    //   F: MonadCancelThrow[F],
    //   G: MonadCancelThrow[G],
    //   trace: Trace[G]
    // ): TracedStream[G, CommittableConsumerRecord[G, K, V]] =
    //   TracedConsumer.injectK[F, G, K, V](consumerStream)(k)
  }

}
