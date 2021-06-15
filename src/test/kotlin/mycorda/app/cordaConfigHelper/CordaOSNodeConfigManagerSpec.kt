package mycorda.app.cordaConfigHelper

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnitPlatform::class)
object CordaOSNodeConfigManagerSpec : Spek({

    lateinit var testDir: File

    describe("Extracting information from a Corda node file") {

        beforeGroup {
            testDir = File(".testing/${String.random(6)}")
            testDir.mkdirs()
            println("CordaOSNodeConfigManagerSpec output in $testDir")
        }

        it("should extract endpoints") {
            val config = File("src/test/resources/configs/complex.conf")
            val manager = CordaOSNodeConfigManager(config)

            val endpoints = manager.extractEndpoints()

            assertThat(endpoints.size, equalTo(3))
            assertThat(endpoints[CordaOSNodeEndPoint.RPC],
                    equalTo(EndPoint("123.123.123.123", EndPoint.Protocol.RPC, 10003, "localhost")))
            assertThat(endpoints[CordaOSNodeEndPoint.P2P],
                    equalTo(EndPoint("123.123.123.123", EndPoint.Protocol.P2P, 10002, "localhost")))
            assertThat(endpoints[CordaOSNodeEndPoint.SSH],
                    equalTo(EndPoint("123.123.123.123", EndPoint.Protocol.SSH, 10005, "localhost")))

        }

    }
})