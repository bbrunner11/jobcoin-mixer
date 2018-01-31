# jobcoin-mixer
Proof of concept cryptocoin mixer using akka-http for a fake cryptocurrency called "jobcoin"


## How to get started

```bash
  git clone https://github.com/bbrunner11/jobcoin-mixer.git
  cd jobcoin-mixer
  sbt run
```
The above will start the jobcoin mixer service.  However, before you do anything, **you need to fund your dummy address with funds**.  You can do that by going here https://jobcoin.gemini.com/puppy and using the "Create 50 New Jobcoins" option, pointing it to your dummy address.  Once that is done, you can proceed.

Once set up a get request to: http://$host:$port/api will return usage instructions:
```All mixers are operational.

How to use:
 First you need to fund your address:  Use https://jobcoin.gemini.com/puppy

 To be assigned a mixer use this URL: http://$host:$port/api/assignmixer
 To start a mixing session use this URL: http://$host:$port/api/mixfunds
 To see the status of your mixer use this URL: http://$host:$port/api/mixstatus/<mixer address>

Example mixing using curl:

curl -H "Content-Type: application/json" -d '{ "fromAddress" : "MyAddress", "addresses" : ["alt1", "alt2", "alt3"] }' http://$host:$port/api/assignmixer
   * the above will response w/ the mixer address you should use for this fromAddress going forward.
curl -H "Content-Type: application/json" -d '{ "fromAddress" : "MyAddress", "mixerAddress" : "mixerIn1", "amount" : "75" }' http://$host:$port/api/mixfunds
   * the above will send your funds from fromAddress to the mixer address you specify (above, mixerIn1).
                **BE CERTAIN THE MIXER ADDRESS IS CORRECT OR YOU FORFEIT YOUR FUNDS!**

You can refresh this URL http://$host:$port/api/mixstatus/mixerIn1 to see the status of your mixer (ie, balance and all transactions)
```
Just to emphasize... **BE CERTAIN THE MIXER ADDRESS IS CORRECT OR YOU FORFEIT YOUR FUNDS!**
Similar to the real world, if you send your real coins to the wrong address, they're lost!
The service polls known mixer addresses that roll up to a known user address.  If you own it, you get the funds.  If not :(

How the mixer works:
1. You either fund your address via https://jobcoin.gemini.com/puppy or know your address and balance.
2. You tell the mixer service who you are via address, as well which alternate address you own that you want mixed out.  **NOTE: You only get to do this once per primary address.  The mixers do not support adding additional alternate addresses at this time.**
   * The mixer will return a valid mixer address that is tied to the address you specified in step 
   * Any funds from now on that go to that address will end up in your alternate address.
3.  You specify your address, your mixer address, and how much of your balance you want to mix.
   * The service will transfer your funds from your address to the mixer address you specified, and mix them to the out addresses you specified.  The mixer is both random in its mix timing, which addresses of yours it sends to, as well as the timing in which it sends to the mix addresses,
   * The mixer charges a transaction fee per mix which will be deducted at the time of a successfl mix.
   * If the mix is unsuccessful for whatever reason, there is no transaction fee and the mixer will pick up the remaining balance the next time it runs.
4.  Once you've sent some funds to the mixer, you can check http://$host:$port/api/mixstatus/<mixer address> to see the status of the mix.  You can continue to refresh the page to see your mixer balance decrease to 0.  Once done, you can return to https://jobcoin.gemini.com/puppy and click on your mixer address to see the transactions to your other address(es).
5.  Any funds at a known mixer address that do not have a from address will be transfered to the house address.
     * eg, If you know your mixer address is 'mixerIn1' and you use the UI to deposit funds to that address w/o first doing step 2), the house keeps those funds.  
## Implementation
An akka-http based REST service utilizing strongly typed messaging between the main service, request handler, and mixer.  Each mixer is tied to the current user and will work on that mix address until the balance is 0.

* MixerMain.scala - the main REST endpoint where the api lives.  It serves out requests to the request handler as well as initializes the scheduler which controls the transaction log poller.
* RequestHandler.scala - Actor that handles the api requests and error handling of requests.  Responsible for checking for previous address activity, mapping addresses to out addresses, mixer address assignment, mixer instantiation, and status requests.   
* MixerService.scala - Actor responsible for splitting up funds, calculating transaction costs, and distributing funds to proper mixer out addresses.
* TxLogPoller.scala - Actor responsible for monitoring known mixer address, determining who owns the address, and sending transactions to the MixerService.
* HttpUtils - helper methods for contacting the main JobCoin API and parsing the Json response.
* Configs.scala - wrapper around application.conf
* application.conf - app specific configuration such as host, port, pollInterval, etc.


## Design limitations
* The amount you transfer into the mixer must be a valid, positive Integer.  It shouldn't be like this, I just simply ran out of time and wanted something that worked.
* The transaction fee only applies to transactions > $10 as determined by the random mix.  One could potentially game the mixer by transferring incremental amounts < $10 right after the poller wakes up.  This arbitrary % gets worse for the house the smaller the transfers become.  However, due to the nature of the randomness in timings of both the mixer service as well as the distribution of funds, it would take a pretty dedicated person to game it.  tl;dr It's a temporary hack to poc a transfer fee.
* Address -> Mixer mappings as well as Address -> Outaddresses are stored as in-memory TrieMaps and as such, a server restart will lose those mappings.  However, if a known mixer address has a balance after a restart, a call to http://$host:$port/api/assignmixer with the mixer address specified will initiate the rest of the mix.  (yes, this has serious security vulnerabilities, but I have no way to keep state)
