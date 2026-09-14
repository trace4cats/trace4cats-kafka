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

import cats.Eq

import fs2.kafka.Header
import fs2.kafka.Headers
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
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
    val headers  = Headers(Header("header1", "value1"), Header("header2", "value2"))
    val expected = TraceHeaders.of("header1" -> "value1", "header2" -> "value2")

    assert(Eq.eqv(converter.from(headers), expected))
  }

  it should "append tracing headers" in {
    val headers      = Headers(Header("header1", "value1"), Header("header2", "value2"))
    val traceHeaders = TraceHeaders.of("header2" -> "value2new", "header3" -> "value3")

    val prOrig = ProducerRecords(List(ProducerRecord("topic", "key", "vale").withHeaders(headers)))
    val pr     = TracedProducer.addHeaders(traceHeaders)(prOrig)

    val headersActual   = pr.toList.flatMap(_.headers.toChain.toList)
    val headersExpected = List(
      Header("header1", "value1"),
      Header("header2", "value2"),
      Header("header2", "value2new"),
      Header("header3", "value3")
    )

    assert(Eq.eqv(headersExpected, headersActual))
  }

}
