package bb.mixer


import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpUtils._
import bb.mixer.MixerMain.{addressInToMixerOut, mixerInToAddressIn}

object TxLogPoller {
  def props(): Props = {
    Props(classOf[TxLogPoller])
  }
}

case class Transaction(timestamp: String, fromAddress: Option[String], toAddress: String, amount: String)

case class Transactions(balance: String, transactions: List[Transaction])

class TxLogPoller extends Actor with ActorLogging with JsonSupport {

   val config = Configs

   def receive: Receive = {
    case "poll" if (addressInToMixerOut.nonEmpty) => processTxs
  }

  def processTxs = {

    val knownAddressTxs = mixerInToAddressIn.map { case (mixAddress, fromAddress) => getJobCoinTransactionsFor(mixAddress) }.toList.map(r => parseResponse(r)).filter(_.balance.trim.toDouble > 0d)
      .map(x => (x.balance.toDouble, x.transactions.filter(d => {
        d.fromAddress.isDefined || mixerInToAddressIn.keySet.contains(d.toAddress)
      }))) //either there's a fromAddress or the mixer exists

    val knownMixerHasBalance = knownAddressTxs.map { case (balance, transactions) => (balance, transactions)

    }
      .map(tx => {
        val balance = tx._1.toInt

        val (fromAddress, toAddress, outAddresses, houseKeeps) = tx._2.headOption match {
                case Some(fa) => {
            val deriveFromAddress = fa.fromAddress match {
              case Some(f) => f
              case None => config.houseGlobalInAddress
            }
              (deriveFromAddress, fa.toAddress, addressInToMixerOut(mixerInToAddressIn(fa.toAddress)), false)
          }
          case None => (config.houseGlobalInAddress, config.houseMixAddress, Seq(config.houseMainAddress), true)
        }
        MixFundsOut(fromAddress, toAddress, outAddresses, balance, houseKeeps)
      }) //known address sends to a known mixer

    knownMixerHasBalance.foreach { knownMixer =>
      context.actorOf(MixerService.props()) ! knownMixer
    }


  }

}
