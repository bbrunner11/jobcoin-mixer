package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.MixerMain.primaryToMixerIn

object MixerService {
  def props(): Props = {
    Props(classOf[MixerService])
  }
}
case class MixThis(fromAddress: String, mixerAddress: String, amount: String)
case class MixerOutAccounts(primaryAccount: String, accounts: List[String])
case class MixerResponse(payload: String)




class MixerService extends Actor with ActorLogging {

  def receive: Receive = {
    case mt: MixThis => log.info(s"\nPrimary Account: ${mt.fromAddress}\n\tMixerAddress: ${mt.mixerAddress}\n\tAmount: ${mt.amount}\n$primaryToMixerIn")
    case moa: MixerOutAccounts => log.info(s"\nPrimary Account: ${moa.primaryAccount}\n\tMixerOutAccounts: ${moa.accounts.toString}")
    case _ => log.info("whatever")
  }

}
