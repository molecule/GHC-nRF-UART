#GHC-nRF-UART

This app is based on the [Android-nRF-UART](https://github.com/NordicSemiconductor/Android-nRF-UART) app created and maintained by Nordic Semiconductor. It is based on version 2.0.

This app pairs with up to 7 BLE devices, sending ASCII and UTF-8 text strings to the paired BLE device running a custom Nordic Semiconductor UART service.

This source code can be compiled with Android Studio and Gradle. 

### Note
- Android 4.3 or later is required.
- Android Studio supported 


### Add these lines to /system/etc/init.qcom.post_boot.sh to auto-launch the app:
# Launch GHC app on start-up
am start -a android.intent.action.MAIN -n com.ghc.shindig/.MainActivity
# turn on bt
