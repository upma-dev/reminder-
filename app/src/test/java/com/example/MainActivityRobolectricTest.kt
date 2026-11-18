package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityRobolectricTest {

    @Test
    fun verifyActivityLaunchesWithoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().start().resume().get()
        assertNotNull(activity)
    }
}
