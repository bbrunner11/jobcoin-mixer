package bb.mixer

import scalaj.http._
import spray.json._

object HttpUtils extends JsonSupport   {

  def getAllJobCoinTransactions: HttpResponse[String] = Http("http://jobcoin.gemini.com/puppy/api/transactions").asString

  def getJobCoinTransactionsFor(address: String): HttpResponse[String] = Http(s"http://jobcoin.gemini.com/puppy/api/addresses/$address").asString

  def sendJobCoinTransaction(fromAddress: String, toAddress: String, amount: String): HttpResponse[String] = {
    val response = Http("http://jobcoin.gemini.com/puppy/api/transactions")
      .postForm(Seq("fromAddress" -> fromAddress, "toAddress" -> toAddress, "amount" -> amount)).asString

    response.code match {
      case 200 => response
      case _ => throw new Exception(response.statusLine)
    }

  }
  def parseResponse(response: HttpResponse[String]): Transactions = {
    response.body.parseJson.convertTo[Transactions]

  }
}
