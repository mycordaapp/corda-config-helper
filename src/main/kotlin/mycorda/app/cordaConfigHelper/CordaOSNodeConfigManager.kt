package mycorda.app.cordaConfigHelper

import com.typesafe.config.ConfigFactory
import java.io.File

class CordaOSNodeConfigManager(nodeConfig: File) {
    private val config = ConfigFactory.parseFile(nodeConfig)

    fun extractEndpoints(): Map<CordaOSNodeEndPoint, EndPoint> {
        val results = HashMap<CordaOSNodeEndPoint, EndPoint>()
        safeExtractRPCEndpoint(results)
        safeExtractP2PEndpoint(results)
        safeExtractSSHEndpoint(results)
        return results
    }

    private fun safeExtractRPCEndpoint(endpoints: HashMap<CordaOSNodeEndPoint, EndPoint>) {
        try {
            val p2pParts = config.getString("p2pAddress").split(":") // assume p2p address is the public address
            val rpcParts = config.getString("rpcSettings.address").split(":")
            val rpc = EndPoint(ipOrHostName = p2pParts[0].trim(),
                    internalIp = rpcParts[0].trim(),
                    port = rpcParts[1].toInt(),
                    protocol = EndPoint.Protocol.RPC)
            endpoints[CordaOSNodeEndPoint.RPC] = rpc
        } catch (ignored: Exception) {
        }
    }

    private fun safeExtractP2PEndpoint(endpoints: HashMap<CordaOSNodeEndPoint, EndPoint>) {
        try {
            val p2pParts = config.getString("p2pAddress").split(":") // assume p2p address is the public address
            val p2p = EndPoint(ipOrHostName = p2pParts[0].trim(),
                    internalIp = "localhost",
                    port = p2pParts[1].toInt(),
                    protocol = EndPoint.Protocol.P2P)
            endpoints[CordaOSNodeEndPoint.P2P] = p2p
        } catch (ignored: Exception) {
        }
    }


    private fun safeExtractSSHEndpoint(endpoints: HashMap<CordaOSNodeEndPoint, EndPoint>) {
        try {
            val p2pParts = config.getString("p2pAddress").split(":")
            val sshPort = config.getInt("sshd.port")
            val ssh = EndPoint(ipOrHostName = p2pParts[0].trim(),   // assume
                    internalIp = "localhost", // how do know what the internal address is
                    port = sshPort,
                    protocol = EndPoint.Protocol.SSH)
            endpoints[CordaOSNodeEndPoint.SSH] = ssh
        } catch (ignored: Exception) {
        }
    }

}