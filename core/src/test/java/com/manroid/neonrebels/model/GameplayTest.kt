package com.manroid.neonrebels.model

import org.junit.Test

class GameplayTest {
    @Test
    fun gameplayRegressionSuite() {
        ModelChecks.runAll()
    }
}
