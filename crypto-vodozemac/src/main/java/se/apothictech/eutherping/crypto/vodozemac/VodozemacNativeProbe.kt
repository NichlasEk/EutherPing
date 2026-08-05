// SPDX-License-Identifier: Apache-2.0

package se.apothictech.eutherping.crypto.vodozemac

object VodozemacNativeProbe {
    init {
        System.loadLibrary("eutherping_vodozemac")
    }

    external fun runProbe(): String
}
