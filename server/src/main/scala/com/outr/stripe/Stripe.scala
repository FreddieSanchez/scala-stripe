package com.outr.stripe

import com.outr.scribe.Logging
import com.outr.stripe.balance.{Balance, BalanceTransaction}
import gigahorse.{Gigahorse, Realm, Response}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Stripe(apiKey: String) extends Logging {
  private val baseURL = "https://api.stripe.com/v1"
  private def url(endPoint: String): String = s"$baseURL/$endPoint"

  object balance {
    def apply(): Future[Balance] = get("balance", QueryConfig.default).map { response =>
      Pickler.read[Balance](response.body)
    }

    def historyById(id: String, config: QueryConfig = QueryConfig.default): Future[BalanceTransaction] = {
      get(s"balance/history/$id", QueryConfig.default).map { response =>
        Pickler.read[BalanceTransaction](response.body)
      }
    }

    def history(config: QueryConfig = QueryConfig.default): Future[StripeList[BalanceTransaction]] = {
      get("balance/history", config).map { response =>
        Pickler.read[StripeList[BalanceTransaction]](response.body)
      }
    }
  }

  private def get(endPoint: String,
                  config: QueryConfig,
                  data: (String, String)*): Future[Response] = {
    val client = Gigahorse.http(Gigahorse.config)
    try {
      val headers = ListBuffer.empty[(String, String)]
      headers += "Stripe-Version" -> Stripe.Version
      config.idempotencyKey.foreach(headers += "Idempotency-Key" -> _)

      val args = ListBuffer(data: _*)
      if (config.limit != QueryConfig.default.limit) args += "limit" -> config.limit.toString
      config.startingAfter.foreach(args += "starting_after" -> _)
      config.endingBefore.foreach(args += "ending_before" -> _)

      val request = Gigahorse.url(url(endPoint))
        .get
        .withAuth(Realm(apiKey, ""))
        .addQueryString(args: _*)
        .addHeaders(headers: _*)

      val future = client.run(request)
      future.onComplete { t =>
        client.close()
      }
      future
    } catch {
      case t: Throwable => {
        client.close()
        throw t
      }
    }
  }
}

object Stripe extends Logging {
  val Version = "2016-07-06"

  def main(args: Array[String]): Unit = {
    val stripe = new Stripe("sk_test_BQokikJOvBiI2HlWgH4olfQ2")
    val future = stripe.balance()
    val balance = Await.result(future, 120.seconds)
    logger.info(s"Balance: $balance")
  }
}