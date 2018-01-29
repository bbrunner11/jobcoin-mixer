package bb.mixer


import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import bb.mixer.HttpTransactions._
import bb.mixer.MixerMain.{mixerInToAddressIn, addressInToMixerIn, addressInToMixerOut, primaryToLastActivity}
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

  def receive: Receive = {
    case "poll" if (addressInToMixerOut.nonEmpty) => processTxs
  }

  def processTxs = {


    //TODO need to do proper string to number casting and error handling at some point!!!
    //mixerInToAddressIn.update("test1", "test2")

    /**
      * Get all transactions for known mixers that do not have a zero balance. If we know there's a balance at a mixer address when this runs,
      * the timeStamp is irrelevant, the balance should be mixed and distributed to whomever is assigned to that mixer address in mixerInToAddressIn
      *
      * Partition by the fromAddress, since if it's a mixer address with no fromAddress, the house gets to keep it :)
      * Filter the transactions where a fromAddress is defined but is also a mixer address since we don't care about mixer outgoing transactions
      */
    val allMixerTransactions = mixerInToAddressIn.map { case (mixAddress, fromAddress) => getJobCoinTransactionsFor(mixAddress) }.toList.map(r => parseResponse(r)).filter(_.balance != "0")
        .map(x => x.transactions.partition(_.fromAddress.isDefined))

    val z = mixerInToAddressIn.map { case (mixAddress, fromAddress) => getJobCoinTransactionsFor(mixAddress) }.toList.map(r => parseResponse(r)).filter(_.balance != "0")
      .map(x => (x.balance, x.transactions.partition(_.fromAddress.isDefined)))

    val knownAddressTxs = allMixerTransactions.flatMap(x => x._1.filter(n => mixerInToAddressIn.keySet.contains(n.toAddress)
      && mixerInToAddressIn.values.toList.contains(n.fromAddress.get))) //known address sends to a known mixer

    val zz = z.map(x => (x._1, x._2._1.filter(n => mixerInToAddressIn.keySet.contains(n.toAddress)
      && mixerInToAddressIn.values.toList.contains(n.fromAddress.get)))) //known address sends to a known mixer

    val unknownAddressTxs = allMixerTransactions.flatMap(x => x._1.filter(n => mixerInToAddressIn.keySet.contains(n.toAddress)
      && !mixerInToAddressIn.values.toList.contains(n.fromAddress.get)))  // unknown address sends to known mixer

    val zzz = z.map(x => (x._1, x._2._1.filter(n => mixerInToAddressIn.keySet.contains(n.toAddress)
      && !mixerInToAddressIn.values.toList.contains(n.fromAddress.get))))  // unknown address sends to known mixer

    val anonAddressTxs = allMixerTransactions.flatMap(_._2) //anonymous address (no fromAddress, UI initiated) sends to known mixer

    val zzzz = z.map(a => (a._1, a._2._2)) //anonymous address (no fromAddress, UI initiated) sends to known mixer

    val knownAddressTotals = knownAddressTxs.groupBy(_.fromAddress.get)
      .map{case (k,v) => MixFundsOut(k, v.head.toAddress, addressInToMixerOut.get(k).get, v.map(_.amount.toDouble).sum, false)} //the get here is safe since we know the fromAddress

    val anonAddressTotals = anonAddressTxs.map(x => MixFundsOut("anon", x.toAddress, Seq("house1"), anonAddressTxs.map(_.amount.toDouble).sum, true))
    println("known: "+zz.mkString("\n"))
    //println("fund: "+ knownAddressTotals)
    println("unknown: "+zzz.mkString("\n"))
    println("anonymous: "+ zzzz)
    //val mixerTransactions = allTransactionsPerUser.flatMap(f => f.transactions.filter(ts => parseTimestamp(ts.timestamp).compareTo(now) > 0)) //only get txs since the last poll


    val x = (anonAddressTotals ++ knownAddressTotals).sortBy(_.houseKeeps == false)

    //context.actorOf(MixerService.props()) ! knownAddressTotals //anonAddressTotals ++ knownAddressTotals

  }


  def parseResponse(response: HttpResponse[String]): Transactions = {
    response.body.parseJson.convertTo[Transactions]

  }

  def parseTimestamp(dateStr: String): Date = {
    formatDate.parse(dateStr)
  }
}
