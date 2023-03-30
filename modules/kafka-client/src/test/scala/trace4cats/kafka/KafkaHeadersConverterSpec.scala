package trace4cats.kafka

import cats.Eq
import fs2.kafka.{Header, Headers, ProducerRecord, ProducerRecords}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import trace4cats.kafka.KafkaHeaders.converter
import trace4cats.model.TraceHeaders
import trace4cats.test.ArbitraryInstances

class KafkaHeadersConverterSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  behavior.of("KafkaHeaders.converter")

  it should "convert headers isomorphically" in forAll { (traceHeaders: TraceHeaders) =>
    assert(Eq.eqv(traceHeaders, converter.from(converter.to(traceHeaders))))
  }

  it should "convert example headers" in {
    val headers = Headers(Header("header1", "value1"), Header("header2", "value2"))
    val expected = TraceHeaders.of("header1" -> "value1", "header2" -> "value2")

    assert(Eq.eqv(converter.from(headers), expected))
  }

  it should "append tracing headers" in {
    val headers = Headers(Header("header1", "value1"), Header("header2", "value2"))
    val traceHeaders = TraceHeaders.of("header2" -> "value2new", "header3" -> "value3")

    val prOrig = ProducerRecords(List(ProducerRecord("topic", "key", "vale").withHeaders(headers)))
    val pr = TracedProducer.addHeaders(traceHeaders)(prOrig)

    val headersActual = pr.records.toList.flatMap(_.headers.toChain.toList)
    val headersExpected = List(
      Header("header1", "value1"),
      Header("header2", "value2"),
      Header("header2", "value2new"),
      Header("header3", "value3")
    )

    assert(Eq.eqv(headersExpected, headersActual))
  }
}
