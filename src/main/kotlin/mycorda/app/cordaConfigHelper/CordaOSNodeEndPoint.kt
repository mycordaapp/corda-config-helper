package mycorda.app.cordaConfigHelper

// TODO - should be in some more basic "corda models" jar


// possible endpoints for a CordaOSNode
enum class CordaOSNodeEndPoint {
    RPC,
    P2P,
    SSH,
    JMX
}
