package mycorda.app.cordaConfigHelper


// TODO - should be in some more basic "corda models" jar

/**
 * A common model for passing around EndPoint connection information.
 *
 */
data class EndPoint(
        /**
         * The exposed (public) ip address of host name. Can be any IPv4 address or valid DNS name
         */
        val ipOrHostName: String,

        /**
         * The protocol
         */
        val protocol: Protocol = Protocol.TCP,

        /**
         * The port if known, if not assume the default for the specified protocol
         */
        val port: Int? = null,

        /**
         * The internal ip address. Can be any IP4 address or localhost
         */
        val internalIp: String? = null,

        /**
         * The platform Id
         */
        val platformId: String? = null) {

    init {
        assert(ipAddressPattern.matches(ipOrHostName) || hostNamePattern.matches(ipOrHostName))
        if (port != null) assert(port in 0..65535)
        if (internalIp != null) assert(internalIp == "localhost" || ipAddressPattern.matches(internalIp))
        if (platformId != null) assert(platformId.length <= 255)
    }

    enum class Protocol {
        TCP, // unspecified TCP
        P2P, // Corda P2P
        RPC, // Corda RPC
        SSH,
        HTTP,
        HTTPS
    }

    fun hasPort(): Boolean = port != null
    fun hasInternalIp(): Boolean = internalIp != null
    fun hasPlatformId(): Boolean = platformId != null
    fun isIPAddress(): Boolean = ipOrHostName.matches(ipAddressPattern)

    fun ipAddress(): String {
        if (isIPAddress()) {
            return ipOrHostName
        } else {
            throw RuntimeException("$ipOrHostName is not a valid IP address")
        }
    }


    companion object {
        val ipAddressPattern = "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$".toRegex()

        // https://stackoverflow.com/questions/1418423/the-hostname-regex
        val hostNamePattern = "^(?=.{1,255}\$)[0-9A-Za-z](?:(?:[0-9A-Za-z]|-){0,61}[0-9A-Za-z])?(?:\\.[0-9A-Za-z](?:(?:[0-9A-Za-z]|-){0,61}[0-9A-Za-z])?)*\\.?\$".toRegex()
    }
}