issuETH is intended to become a [GitHub App](https://developer.github.com/apps) to be able to collect value on Ethereum to support fixing issues.
This is inspired [by commiteth](https://developer.github.com/apps) but I was not willing to grant such [invasive permissions](https://github.com/status-im/commiteth/issues/56) to my github account to actually use it.
Also being a GitHub App does not only solve this problem - we can also get a way nicer WorkFlow which would be the following:

 - Add this app to your account or Organisation
 - Add the bounty label to an issue you want
 - transfer value to it (e.g. with the BarCode that is now as an answer to the issue)
 - Close the issue with a PR and the "closes #<issue>" in the message
 - Profit

Still thinking about how to transfer the value after closing. My current Idea for the final transfer is: the private key gets encrypted for the PGP key that is used to sign the commit message that closes the issue and posted as an answer to the issue. Why so complicated you ask:

 * this way we can also cover transfer of tokens (e.g. project tokens) easily
 * no ether is needed for transfereing the value - so there are no gas-costs to operate issuETH (YAY)
