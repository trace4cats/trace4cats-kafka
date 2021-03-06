package trace4cats.kafka

import cats.Eq
import fs2.kafka.{Header, Headers}
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
}
