package se.apothictech.eutherping.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun inboxConversationAndScrolling() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = false,
    ) {
        val device = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("cmd role add-role-holder android.app.role.SMS $PACKAGE_NAME")
        repeat(35) { index ->
            device.executeShellCommand(
                "content insert --uri content://sms/inbox " +
                    "--bind address:s:$PROFILE_ADDRESS --bind body:s:ProfileMessage$index " +
                    "--bind date:l:${System.currentTimeMillis() - index * 1000L} --bind read:i:1",
            )
        }
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("ACTIVE SIGNALS")), 5_000)

        device.findObject(By.text(PROFILE_ADDRESS))?.click()
        device.wait(Until.hasObject(By.desc("Back")), 3_000)
        device.findObject(By.scrollable(true))?.scroll(Direction.UP, 0.8f)
        device.pressBack()

        device.findObject(By.text("SYSTEM"))?.click()
        device.findObject(By.scrollable(true))?.scroll(Direction.UP, 0.8f)
        device.pressBack()
        device.executeShellCommand(
            "content delete --uri content://sms --where \"address='$PROFILE_ADDRESS'\"",
        )
    }

    private companion object {
        const val PACKAGE_NAME = "se.apothictech.eutherping"
        const val PROFILE_ADDRESS = "15550001111"
    }
}
