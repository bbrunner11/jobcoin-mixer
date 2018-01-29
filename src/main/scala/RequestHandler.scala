
package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpTransactions._
import bb.mixer.MixerMain.{mixerInToAddressIn, addressInToMixerIn, addressInToMixerOut, primaryToLastActivity}

object RequestHandler {
  def props(): Props = {
    Props(classOf[RequestHandler])
  }
}

case class Request(request: String)
case class Response(payload: String)
case class Error(error: String)

class RequestHandler extends Actor with ActorLogging {

  var incMixer = 0 //increment int for mixer addresses

  def receive: Receive = {

    case moa: MixerOutAddresses => {
      addressInToMixerOut.get(moa.fromAddress) match {
        case Some(_) => {
          sender ! Error("You already have alternate addresses associated with this address.  Try again.") //limit one set of alternate addresses per primary address
        }
        case None => {
          context.actorOf(MixerService.props()) ! MixerOutAddresses(moa.fromAddress, moa.addresses) //TODO get rid of this and set the values here
          primaryToLastActivity.update(moa.fromAddress, java.util.Calendar.getInstance.getTime) //init first time user has used the mixer
          incMixer += 1
          addressInToMixerIn.update(moa.fromAddress, s"mixerIn$incMixer") //increment mixer address by 1 per valid mix request TODO do we even need this anymore?
          mixerInToAddressIn.update(s"mixerIn$incMixer", moa.fromAddress)
          sender ! Response(s"Sent your info to the Mixer.  Your mixer address is 'mixerIn$incMixer'.  Please send funds to be mixed to that address. Thanks")
        }
       }
    }
    case mt: MixFundsIn => {
      addressInToMixerOut.get(mt.fromAddress) match { //check that the user has notified mixer of out addresses
        case Some(_) => {
          sendJobCoinTransaction(mt.fromAddress, mt.mixerAddress, mt.amount) match { //send to Gemini API @ mixer address
            case r if(r.code) == 200 => {
              context.actorOf(MixerService.props()) ! MixFundsIn(mt.fromAddress, mt.mixerAddress, mt.amount) //TODO get rid of this, the poller will pick up the transaction
              sender ! Response(s"Sent your info to the Mixer. Your mix will be done momentarily. Thanks")
            }
            case r if(r.code) == 422 => {
              sender ! Error(s"Error. Insufficient funds.")
            }
            case _ => sender ! Error("Something went wrong.")
          }
        }
        case None => {
          sender ! Error(s"You have not notified the mixer of the alternate address(es) to use in the mix.")
        }
      }


    }
    case Request => {
      println("got here")
      sender() ! Response("ok")
    }
    case _ => println("error");Error

  }
}
