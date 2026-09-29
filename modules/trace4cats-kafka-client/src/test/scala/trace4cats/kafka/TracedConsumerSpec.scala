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

import scala.annotation.nowarn

import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.unsafe.implicits.global

import fs2.Stream
import fs2.kafka.CommittableConsumerRecord
import fs2.kafka.CommittableOffsetBatch
import fs2.kafka.ConsumerRecord
import fs2.kafka.TestCommittables
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.scalatest.flatspec.AnyFlatSpec
import trace4cats.EntryPoint
import trace4cats.Span
import trace4cats.kernel.ErrorHandler
import trace4cats.model.AttributeValue
import trace4cats.model.Link
import trace4cats.model.SpanKind
import trace4cats.model.SpanStatus

class TracedConsumerSpec extends AnyFlatSpec {

  type Traced[A] = Kleisli[IO, Span[IO], A]

  @nowarn("cat=deprecation")
  val metadata = new ConsumerGroupMetadata("my-group")

  behavior.of("TracedConsumer.inject")

  it should "set consumer.group from the committer metadata, fetched once per committer" in {
    val partition = new TopicPartition("topic", 0)

    val test = for {
      groups  <- Ref.of[IO, List[Option[Any]]](Nil)
      fetches <- Ref.of[IO, Int](0)
      span    <- IO.pure[Span[IO]](new Span[IO] {
                private val noop = Span.noopInstance[IO]

                override def context                                                         = noop.context
                override def put(key: String, value: AttributeValue)                         = noop.put(key, value)
                override def putAll(fields: Map[String, AttributeValue])                     = noop.putAll(fields)
                override def setStatus(spanStatus: SpanStatus)                               = noop.setStatus(spanStatus)
                override def addLink(link: Link)                                             = noop.addLink(link)
                override def addLinks(links: NonEmptyList[Link])                             = noop.addLinks(links)
                override def child(name: String, kind: SpanKind)                             = noop.child(name, kind)
                override def child(name: String, kind: SpanKind, errorHandler: ErrorHandler) =
                  noop.child(name, kind, errorHandler)

                override def putAll(fields: (String, AttributeValue)*) =
                  groups.update(fields.toMap.get("consumer.group").map(_.value.value) :: _)
              })
      committer = TestCommittables.committer[IO](_ => IO.unit, fetches.update(_ + 1).as(metadata))
      records   = List(1L, 2L, 3L).map { offset =>
                  CommittableConsumerRecord(
                    ConsumerRecord(partition.topic, partition.partition, offset, "key", "value"),
                    TestCommittables.offset(partition, offset, committer)
                  )
                }
      _ <- TracedConsumer
             .inject[IO, Traced, String, String](Stream.emits(records))(Kleisli(_ => Resource.pure[IO, Span[IO]](span)))
             .run
             .compile
             .drain
      recorded <- groups.get
      count    <- fetches.get
    } yield assertResult((List.fill(3)(Some("my-group")), 1)) {
      (recorded, count)
    }

    test.unsafeRunSync()
  }

  behavior.of("TracedConsumer.injectK")

  it should "keep one lifted committer per consumer so batched commits stay merged" in {
    val partition0 = new TopicPartition("topic", 0)
    val partition1 = new TopicPartition("topic", 1)

    val test = for {
      commits  <- Ref.of[IO, List[Map[TopicPartition, OffsetAndMetadata]]](Nil)
      committer = TestCommittables.committer[IO](offsets => commits.update(offsets :: _), IO.pure(metadata))
      records   = List((partition0, 1L), (partition1, 5L), (partition0, 2L)).map { case (partition, offset) =>
                  CommittableConsumerRecord(
                    ConsumerRecord(partition.topic, partition.partition, offset, "key", "value"),
                    TestCommittables.offset(partition, offset, committer)
                  )
                }
      lifted <- TracedConsumer
                  .injectK[IO, Traced, String, String](Stream.emits(records))(EntryPoint.noop[IO].toKleisli)
                  .run
                  .map(_._2)
                  .compile
                  .toList
                  .run(Span.noopInstance[IO])
      batch      = CommittableOffsetBatch.fromFoldable(lifted.map(_.offset))
      _         <- batch.commit.run(Span.noopInstance[IO])
      committed <- commits.get
    } yield {
      assertResult((1, List(Map(partition0 -> new OffsetAndMetadata(2L), partition1 -> new OffsetAndMetadata(5L))))) {
        (batch.offsets.size, committed)
      }
    }

    test.unsafeRunSync()
  }

}
