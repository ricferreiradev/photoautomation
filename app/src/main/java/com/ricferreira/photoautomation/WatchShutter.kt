package com.ricferreira.photoautomation

object WatchShutter {
    fun matches(registeredDevice: String?, sourceDevice: String?, keyCode: Int): Boolean =
        !registeredDevice.isNullOrBlank() && registeredDevice == sourceDevice && keyCode == 24
}
