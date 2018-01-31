package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpUtils.sendJobCoinTransaction
import bb.mixer.MixerMain.{addressInToMixerOut, mixerInToAddressIn}

object MixerService {
  def props(): Props = {
    Props(classOf[MixerService])
  }

}

case class MixerOutAddresses(fromAddress: String, addresses: Seq[String])

case class MixerResponse(payload: String)

case class MixFundsIn(fromAddress: String, mixerAddress: String, amount: String)

case class MixFundsOut(initatingAddress: String, mixerAddress: String, outAddresses: Seq[String], amount: Int, houseKeeps: Boolean) {
  override def toString = s" MixFundsOut(initAddres: $initatingAddress mixerAddress: $mixerAddress outAccounts: $outAddresses txAmount: $amount houseKeeps: $houseKeeps)"
}

class MixerService extends Actor with ActorLogging {

  val config = Configs
  val rand1 = new scala.util.Random

  def receive: Receive = {
//    case mfi: MixFundsIn => {
//      log.debug(s"\nPrimary Account: ${mfi.fromAddress}\n\tMixerAddress: ${mfi.mixerAddress}\n\tAmount: ${mfi.amount}\n$mixerInToAddressIn")
//    }
    case mfo: MixFundsOut => {
      doTheMix(mfo)
    }
    case _ => log.info("Akka sender not sending a valid message.")
  }

  /**
    *
    * @param mtf MixFundsOut(initatingAddress: String, mixerAddress: String, outAddresses: Seq[String], amount: Int, houseKeeps: Boolean)
    */
  private def doTheMix(mtf: MixFundsOut): Unit = {

    val txBuckets = 5 + rand1.nextInt(10) //split the amount into this many buckets(base + some random Int to 10
    val addressesOut = mtf.outAddresses
    val amount = mtf.amount

    /**
      * Split the transaction amount into increments
      * For each incremental amount, take an arbitrary .05 for the house, send the rest to the out address(es)
      * Only if the transaction to the out account is successful does the house take the vig
      * Otherwise the failed balance with vig intact stays in the mixer account and will get picked up by the next poller
      */
    val splitAmount = randSum(txBuckets, amount).filter(_ != 0)

    splitAmount.foreach(randAmt => {
      val out = rand1.nextInt(addressesOut.size)
      val vig = if (randAmt < 10) 0 else Math.round(randAmt * .05) //since the mixer can only handle integer amounts, calc vig and round for values > 10

      println(s"Mixing: ${mtf.mixerAddress} -> ${randAmt - vig} to ${addressesOut(out)} (Tx fee: $vig sent to ${config.houseVigAddress}")
      //log.info(s"${mtf.mixerAddress} sent ${randAmt - vig} to ${addressesOut(out)} by ${mtf.initatingAddress}") //send random amount to out accts
      //log.info(s"${mtf.mixerAddress} sent transaction fee of $vig to ${config.houseVigAddress} by ${mtf.initatingAddress}")

      Thread.sleep(((rand1.nextDouble * config.mixerSleep.toDouble) + config.mixerSleep.toDouble).toLong) // randomize transaction timing

      if (sendJobCoinTransaction(mtf.mixerAddress, addressesOut(out), (randAmt - vig).toString).isSuccess && vig != 0) //can't send 0 amount
        sendJobCoinTransaction(mtf.mixerAddress, config.houseVigAddress, vig.toString)

    })
println(s"***** Finished mixing funds for mixer ${mtf.mixerAddress} *****")
  }

  /** Reused from random internet java, this works but can be refactored to be more functional (I ran out if time)
    *
    * @param n the number of buckets
    * @param m upper boud of the random distribution.  Should be the transfer amount in this case
    * @return List of n integers that add up to m
    */
  private def randSum(n: Int, m: Int): List[Int] = { //TODO refactor this crap
    val rand = scala.util.Random

    val nums = new scala.collection.mutable.ListBuffer[Int]

    nums ++= nums.padTo(n, 0)

    if (m <= 0) throw new IllegalArgumentException

    for (i <- 1 until nums.length) {
      nums(i) = rand.nextInt(m)
    }

    val sorted = nums.sorted

    for (i <- 1 until sorted.length) {
      sorted(i - 1) = sorted(i) - sorted(i - 1)
    }

    sorted(n - 1) = m - sorted(n - 1)

    sorted.toList
  }

}
