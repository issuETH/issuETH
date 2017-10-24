package kontinuum


import kontinuum.model.config.Chain
import org.kethereum.ETH_IN_WEI
import org.kethereum.extensions.clean0xPrefix
import org.kethereum.extensions.prepend0xPrefix
import org.kethereum.functions.getTokenTransferTo
import org.kethereum.functions.getTokenTransferValue
import org.kethereum.functions.isTokenTransfer
import org.kethereum.rpc.EthereumRPC
import org.kethereum.rpc.toKethereumTransaction
import java.math.BigDecimal
import java.math.BigInteger

class StatefulChain(val chain: Chain, val ethereumRPC: EthereumRPC, var lastBlock: String)


val processedBlocks = mutableMapOf<StatefulChain, BigInteger>()

fun watchChain() {

    val statefulChains = ConfigProvider.config.chains.map {
        StatefulChain(it, EthereumRPC(it.eth_rpc_url, okhttp = okhttp), "0x0")
    }

    while (true) {

        Thread.sleep(1000)

        for (statefulChain in statefulChains) {
            try {
                val newBlock = statefulChain.ethereumRPC.getBlockNumberString()

                if (newBlock != null && newBlock != statefulChain.lastBlock) {
                    statefulChain.lastBlock = newBlock
                    processBlockNumber(newBlock, statefulChain)
                }
            } catch (e: Exception) {
                println("problem at block ${statefulChain.lastBlock} " + e.message)
                e.printStackTrace()
            }
        }


    }

}

fun processBlockNumber(newBlock: String, statefulChain: StatefulChain) {
    var lastBlock = processedBlocks.getOrElse(statefulChain, { BigInteger.ZERO })

    val newBlockBigInteger = BigInteger(newBlock.clean0xPrefix(), 16)
    while (lastBlock <= newBlockBigInteger) {
        lastBlock += BigInteger.ONE
        processedBlocks.put(statefulChain, lastBlock)

        val transactions = statefulChain.ethereumRPC.getBlockByNumber("0x" + lastBlock.toString(16))?.transactions

        println("New Block $lastBlock on " + statefulChain.chain.name + " " + transactions?.size + " txs")

        transactions?.forEach { tx ->

            tx.to?.let { to ->
                if (processedTransactions.contains(tx.hash.toLowerCase())) {
                    println("skipping already processed " + tx.hash)
                }else {
                    val kethereumTransaction = tx.toKethereumTransaction()
                    if (kethereumTransaction.isTokenTransfer()) {
                        activeIssues.filter { kethereumTransaction.getTokenTransferTo().cleanHex.toLowerCase() == it.address.clean0xPrefix().toLowerCase() }.forEach {
                            val value = kethereumTransaction.getTokenTransferValue()
                            val token = tokenMap[statefulChain.chain.networkId.toString()]!![to.clean0xPrefix().toLowerCase()]
                            if (token != null) {
                                val scaledValue = BigDecimal(value).divide(BigDecimal(BigInteger("10").pow(BigInteger(token.decimals).toInt())))

                                githubInteractor.addIssueComment(it.project, it.issue,
                                        "Token-transfer [transaction on " + statefulChain.chain.name + "](" + statefulChain.chain.tx_base_url.replace("TXHASH", tx.hash.prepend0xPrefix()) + ")" +
                                                " with value " + scaledValue + token.symbol,
                                        it.installation)
                                saveProcessedTX(tx.hash)
                            } else {
                                githubInteractor.addIssueComment(it.project, it.issue,
                                        "Unknown token-transfer [transaction on " + statefulChain.chain.name + "](" + statefulChain.chain.tx_base_url.replace("TXHASH", tx.hash.prepend0xPrefix()) + ")" +
                                                " with value " + value + "<br/>The token must be registered here: [https://github.com/MyEtherWallet/ethereum-lists](https://github.com/MyEtherWallet/ethereum-lists)",
                                        it.installation)
                                saveProcessedTX(tx.hash)
                            }
                        }

                    } else {
                        activeIssues.filter { it.address.toLowerCase().clean0xPrefix() == to.toLowerCase().clean0xPrefix() }.forEach {
                            val value = BigDecimal(BigInteger(tx.value.clean0xPrefix(), 16)).divide(BigDecimal(ETH_IN_WEI))
                            githubInteractor.addIssueComment(it.project, it.issue,
                                    "[Transaction on " + statefulChain.chain.name + "](" + statefulChain.chain.tx_base_url.replace("TXHASH", tx.hash.prepend0xPrefix()) + ")" +
                                            " with value " + value + "ETH",
                                    it.installation)
                            saveProcessedTX(tx.hash)
                        }
                    }
                }
            }
        }
    }
}
