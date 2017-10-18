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
                    println("New Block $newBlock on " + statefulChain.chain.name)
                    processBlockNumber(newBlock, statefulChain)
                }
            } catch (e: Exception) {
                println("problem at block ${statefulChain.lastBlock} " + e.message)
            }
        }


    }

}

fun processBlockNumber(newBlock: String, statefulChain: StatefulChain) {
    statefulChain.ethereumRPC.getBlockByNumber(newBlock)?.transactions?.forEach { tx ->


        tx.to?.let { to ->
            val kethereumTransaction = tx.toKethereumTransaction()
            if (kethereumTransaction.isTokenTransfer()) {
                activeIssues.filter { kethereumTransaction.getTokenTransferTo().cleanHex == it.address.clean0xPrefix() }.forEach {
                    githubInteractor.addIssueComment(it.project, it.issue,
                            "new token-transfer [transaction on " + statefulChain.chain.name + "](" + statefulChain.chain.tx_base_url.replace("TXHASH", tx.hash.prepend0xPrefix()) + ")" +
                                    " with value " + kethereumTransaction.getTokenTransferValue(),
                            it.installation)
                }

            } else {
                activeIssues.filter { it.address.clean0xPrefix() == to.clean0xPrefix() }.forEach {
                    val value = BigDecimal(BigInteger(tx.value.clean0xPrefix(), 16)).divide(BigDecimal(ETH_IN_WEI))
                    githubInteractor.addIssueComment(it.project, it.issue,
                            "new [transaction on " + statefulChain.chain.name + "](" + statefulChain.chain.tx_base_url.replace("TXHASH", tx.hash.prepend0xPrefix()) + ")" +
                                    " with value " + value +"ETH",
                            it.installation)
                }
            }
        }
    }
}
