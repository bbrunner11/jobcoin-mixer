package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpTransactions.sendJobCoinTransaction
import bb.mixer.MixerMain.{addressInToMixerIn, addressInToMixerOut, mixerInToAddressIn}
import scala.util.control.Breaks._

object MixerService {
  def props(): Props = {
    Props(classOf[MixerService])
  }
}

case class MixFundsIn(fromAddress: String, mixerAddress: String, amount: String)

case class MixFundsOut(initatingAddress: String, mixerAddress: String, outAccounts: Seq[String], amount: Double, houseKeeps: Boolean)

case class MixerOutAddresses(fromAddress: String, addresses: Seq[String])

case class MixerResponse(payload: String)

class MixerService extends Actor with ActorLogging {

  val rand1 = new scala.util.Random

  def receive: Receive = {
    case mfi: MixFundsIn => {
      log.info(s"\nPrimary Account: ${mfi.fromAddress}\n\tMixerAddress: ${mfi.mixerAddress}\n\tAmount: ${mfi.amount}\n$mixerInToAddressIn")
    }
    case mfo: List[MixFundsOut] => {  //TODO fix the type erasure here, probably wrap it in an outer case class
      mfo.foreach(tx => doTheMix(tx))
    }
    case moa: MixerOutAddresses => {
      storeOutAccounts(moa)
      log.info(s"\nPrimary Account: ${moa.fromAddress}\n\tMixerOutAccounts: ${moa.addresses.toString}")
      log.info(s"MixerInToAdressIn: $mixerInToAddressIn")
    }
    case _ => log.info("whatever")
  }

  def storeOutAccounts(moa: MixerOutAddresses): Unit = {
    addressInToMixerOut.getOrElseUpdate(moa.fromAddress, moa.addresses) //right now, 1 unique address is assigned to a predefined set of out addresses
    log.info(addressInToMixerOut.toString)
  }

  def doTheMix(mtf: MixFundsOut): Unit = {


    //distribute a total amount into sub accounts
    Thread.sleep(1000L) // TODO figure out a way to randomize the sleep

    val distIncrement = 1 //TODO make this configurable
    val addressesOut = mtf.outAccounts
    var amount = mtf.amount

   breakable {

      for (iter <- 1 to distIncrement) {

        val randDouble= rand1.nextDouble

        val combined = amount * randDouble//  (amount / (distIncrement - iter)) * randDouble//somewhat normalize distribution based on stage of iteration

        val out = rand1.nextInt(addressesOut.size) //random index to pick out accounts from Seq


        if (amount == 0 || iter == distIncrement) {
          log.info(s"${mtf.mixerAddress} sent balance $amount to ${addressesOut(out)} for ${mtf.initatingAddress}") //send balance to out accts
          sendJobCoinTransaction(mtf.mixerAddress, addressesOut(out), amount.longValue().toString)
          break
        }
        else {
          log.info(s"${mtf.mixerAddress} sent random $combined to ${addressesOut(out)} for ${mtf.initatingAddress}") //send random amount to out accts
          sendJobCoinTransaction(mtf.mixerAddress, addressesOut(out), combined.longValue()toString)

        }
        amount -= combined

      }
    }
  }

}
