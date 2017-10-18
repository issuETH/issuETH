package kontinuum.model.config


data class Chain(
        val name: String,
        val eth_rpc_url: String,
        val tx_base_url: String,
        val networkId: Long
)
