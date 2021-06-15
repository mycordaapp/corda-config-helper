package mycorda.app.cordaConfigHelper


import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.io.File
import java.lang.StringBuilder
import com.natpryce.hamkrest.isA
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe


@RunWith(JUnitPlatform::class)
object ConfigFileEditorSpec : Spek({

    lateinit var testDir: File

    describe("Edit an appConfig file") {

        beforeGroup {
            testDir = File(".testing/${String.random()}")
            testDir.mkdirs()
            println("ConfigFileEditorSpec output in $testDir")
        }

        it("should update config file") {
            val original = File("src/test/resources/configs/complex.conf")
            val editor = ConfigFileEditor(original)

            // update keys
            editor.updateKey("myLegalName", "\"O=Alice,L=Melbourne,C=AU\"")
            editor.updateKey("foo", "bar", false)
            editor.updateKey("wizz", "bang", true)


            // update given keys in a section
            editor.updateKeyValueSection("rpcSettings", mapOf("address" to "\"10.0.0.1:10003\""))

            // update a single key in a section
            editor.updateSectionKey("networkServices", "doormanURL", "\"http://doorman:10001\"")
            editor.updateSectionKey("networkServices", "wizz", "\"bang\"", true)


            editor.updateSectionKey("notary", "validating", "false", true)

            val updated = StringBuilder()

            editor.save(updated)

            val expected = """myLegalName="O=Alice,L=Melbourne,C=AU"
p2pAddress="123.123.123.123:10002"
rpcSettings {
    address="10.0.0.1:10003"
    useSsl=true

    ssl {
       keyStorePath = "rpckeystore.jks"
       keyStorePassword = "password"
    }

    adminAddress="localhost:10004"
}
keyStorePassword : "cordacadevpass"
trustStorePassword : "password"
devMode : true
rpcUsers=[
{
    password=test
    permissions=[
        ALL
    ]
    user=user1
}]
 sshd {
    port = 10005
}
networkServices {
    doormanURL="http://doorman:10001"
    networkMapURL="http://localhost:10001"
  wizz = "bang"
}

wizz = bang
notary {
  validating = false
}"""

            assertThat(updated.toString(), equalTo(expected))
        }

        it("should add new entry if not in file") {
            val original = File("src/test/resources/configs/example.conf")
            val updated = File("$testDir/updated2.conf")


            val editor = ConfigFileEditor(original)

            editor.updateKeyValueSection("rpcSettings", mapOf("useSsl" to "true"))

            editor.updateKey("jmxReporterType", "JOLOKIA")

            editor.save(updated)
            println("Read updated2.conf and check its OK !")

        }

        it("should update deeply nested section") {
            val original = File("src/test/resources/configs/complex.conf")

            val editor = ConfigFileEditor(original)


            editor.updateSectionKey(listOf("rpcSettings", "ssl"), "keyStorePassword", "\"corda123\"")

            val updated = StringBuilder()
            editor.save(updated)

            val expected = """myLegalName="???"
p2pAddress="123.123.123.123:10002"
rpcSettings {
    address="localhost:10003"
    useSsl=true

    ssl {
       keyStorePath = "rpckeystore.jks"
       keyStorePassword ="corda123"
    }

    adminAddress="localhost:10004"
}
keyStorePassword : "cordacadevpass"
trustStorePassword : "password"
devMode : true
rpcUsers=[
{
    password=test
    permissions=[
        ALL
    ]
    user=user1
}]
 sshd {
    port = 10005
}
networkServices {
    doormanURL="http://localhost:10001"
    networkMapURL="http://localhost:10001"
}
"""

            assertThat(updated.toString(), equalTo(expected))

        }


        it("should update signer.conf ") {
            val original = File("src/test/resources/configs/signer.conf")

            val editor = ConfigFileEditor(original)


            editor.updateSectionKey(listOf("serviceLocations", "identity-manager"), "host", "10.1.2.3")

            val updated = StringBuilder()
            editor.save(updated)

            val expected = """// snippet from real signer.conf
serviceLocations = {
    "identity-manager" = {
        host =10.1.2.3
        port = 5051
    }
}"""

            assertThat(updated.toString(), equalTo(expected))
        }

    }

    describe("regular expression detection") {
        it("should find the start of a section") {
            val regex = Tokenizer.Regexp.REGEXP_SECTION_START

            assert(regex.matches("serviceLocations = {"))
            assert(regex.matches("service-locations = {"))
            assert(regex.matches("service_locations = {"))
            assert(regex.matches("serviceLocations={"))
            assert(regex.matches("serviceLocations {"))
            assert(regex.matches("  serviceLocations {"))
            assert(regex.matches("\"serviceLocations\" {"))
            assert(regex.matches("\"serviceLocations\" = {"))
            assert(regex.matches("  \"serviceLocations\" = {"))

        }

        it("should find the end of a section") {
            val regex = Tokenizer.Regexp.REGEXP_SECTION_END

            assert(regex.matches("}"))
            assert(regex.matches(" } "))
            assert(regex.matches("},"))

        }
    }

    describe("tokeniser") {
        it("should tokenise simple nested section") {
            val config = """
sectionA = {
    "sectionB" = {
        value1 = one
    }
}
            """.trimIndent()

            val tokenizer = Tokenizer(config.lines().listIterator())
            val tokens = tokenizer.asSequence().toList() as List<Token>

            assertThat(tokens.size, equalTo(5))
            assertThat(tokens[0], isA<StartSectionToken>())
            assertThat(tokens[1], isA<StartSectionToken>())
            assertThat(tokens[2], isA<KeyValueToken>())
            assertThat(tokens[3], isA<EndSectionToken>())
            assertThat(tokens[4], isA<EndSectionToken>())

            // check in more detail
            assertThat((tokens[0] as StartSectionToken).sectionName(), equalTo("sectionA"))
            assertThat((tokens[1] as StartSectionToken).sectionName(), equalTo("sectionB"))
            assertThat((tokens[2] as KeyValueToken).key(), equalTo("value1"))
        }


        it("should tokenise repeated nested sections") {
            val config = """
sectionA = {
    "sectionB" = {
        value1 = 1
    },
     "sectionC" = {
        value2 = 2
    }
}
            """.trimIndent()

            val tokenizer = Tokenizer(config.lines().listIterator())
            val tokens = tokenizer.asSequence().toList() as List<Token>

            assertThat(tokens.size, equalTo(8))
            assertThat(tokens[0], isA<StartSectionToken>())
            assertThat(tokens[1], isA<StartSectionToken>())
            assertThat(tokens[2], isA<KeyValueToken>())
            assertThat(tokens[3], isA<EndSectionToken>())
            assertThat(tokens[4], isA<StartSectionToken>())
            assertThat(tokens[5], isA<KeyValueToken>())
            assertThat(tokens[6], isA<EndSectionToken>())
            assertThat(tokens[7], isA<EndSectionToken>())

            // check in more detail
            assertThat((tokens[2] as KeyValueToken).key(), equalTo("value1"))
            assertThat((tokens[5] as KeyValueToken).key(), equalTo("value2"))

        }


        it("should tokenise deeply nested sections") {
            val config = """
sectionA = {
    "sectionB" = {
        "sectionC" = {
        value1 = 1
       }    
   }
}
            """.trimIndent()

            val tokenizer = Tokenizer(config.lines().listIterator())
            val tokens = tokenizer.asSequence().toList() as List<Token>

            assertThat(tokens.size, equalTo(7))
            assertThat(tokens[0], isA<StartSectionToken>())
            assertThat(tokens[1], isA<StartSectionToken>())
            assertThat(tokens[2], isA<StartSectionToken>())
            assertThat(tokens[3], isA<KeyValueToken>())
            assertThat(tokens[4], isA<EndSectionToken>())
            assertThat(tokens[5], isA<EndSectionToken>())
            assertThat(tokens[6], isA<EndSectionToken>())

            // check in more detail
            assertThat((tokens[0] as StartSectionToken).sectionName(), equalTo("sectionA"))
            assertThat((tokens[1] as StartSectionToken).sectionName(), equalTo("sectionB"))
            assertThat((tokens[2] as StartSectionToken).sectionName(), equalTo("sectionC"))
            assertThat((tokens[3] as KeyValueToken).key(), equalTo("value1"))

        }



    }
})


