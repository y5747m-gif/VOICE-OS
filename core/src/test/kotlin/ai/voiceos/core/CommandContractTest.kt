package ai.voiceos.core

import org.junit.Test

class CommandContractTest {
    @Test fun localGrammarAndTypedExecutorHonorContracts() = verifyCommandContracts()
    @Test fun androidReleaseAndOriginHonorContracts() = verifyReleaseContracts()
}
