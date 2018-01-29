package bb.mixer



import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import bb.mixer.HttpTransactions._
import bb.mixer.MixerMain.{primaryToMixerIn, primaryToMixerOut}
import spray.json._
import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat

import scalaj.http.HttpResponse



trait JsonSupport2 extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val transaction = jsonFormat4(Transaction)
  implicit val transactions = jsonFormat2(Transactions)
}

object TxLogPoller {
  def props(): Props = {
    Props(classOf[TxLogPoller])
  }
}

case class Transaction(timestamp: String, fromAddress: Option[String], toAddress: String, amount: String)
case class Transactions(balance: String, transactions: List[Transaction])

class TxLogPoller extends Actor with ActorLogging with JsonSupport2 {

  val now = Calendar.getInstance.getTime

  val formatDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

  //primaryToMixerIn.update("test1", "in1")

  def receive: Receive = {
    case "poll" if(primaryToMixerOut.nonEmpty)  => processTxs
  }

  def processTxs = {
    val allTransactionsPerUser = primaryToMixerOut.keys.map(x => getJobCoinTransactionsFor(x)).toList.map(r => parseResponse(r)) //only look for transactions for addresses wanting to mix
 println(allTransactionsPerUser.mkString)

    //val mixerTransactions = allTransactionsPerUser.flatMap(f => f.transactions.filter(ts => parseTimestamp(ts.timestamp).compareTo(now) > 0)) //only get txs since the last poll

    primaryToMixerIn.update("Alice", "Bob")

    val mixerTransactions = allTransactionsPerUser.map(f => f.transactions.filter(z => z.fromAddress.isDefined)) //no from address, can't roll up to a mix out account TODO derive out account?

    val txSinceLastPoll = mixerTransactions.flatMap(x => x.filter(z => parseTimestamp(z.timestamp).compareTo(now) < 0)) //all users that have a mixer address and have added funds to that address since last poll

println("SSSSS   " + txSinceLastPoll.mkString("\n"))

   // log.info(parseResponse(response).toString)
   // else log.info("blah")
    //context.actorOf(MixerService.props()) ! MixThis()

  }


    def parseResponse(response: HttpResponse[String]): Transactions = {
      response.body.parseJson.convertTo[Transactions]
    }

    def parseTimestamp(dateStr: String): Date = {
      formatDate.parse(dateStr)
    }
}
