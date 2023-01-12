package com.scheffsblend.unarueda

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleReadCallback
import com.clj.fastble.callback.BleScanAndConnectCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.ramotion.fluidslider.FluidSlider
import java.util.*


class MainActivity : AppCompatActivity() {
    private val TAG = "Una Rueda"
    private val PREF_AUTO_CONNECT = "auto_connect"
    private val resultCode = 42
    private lateinit var brightnessSlider: FluidSlider
    private lateinit var animationDelaySlider: FluidSlider
    private lateinit var startColor: View
    private lateinit var endColor: View
    private lateinit var enabled: CheckBox
    private lateinit var moreColors: CheckBox
    private lateinit var rgbBlending: CheckBox
    private lateinit var connectButton: Button
    private lateinit var autoConnect: CheckBox
    private var connectedDevice: BleDevice? = null

    private val serviceUUID: UUID = UUID.fromString("eb680eeb-8aa7-d34d-ad9c-d48ca1153ca1")
    private val enabledUUID: UUID = UUID.fromString("b728debd-6c42-4a31-d34d-1e2640351b08")
    private val brightnessUUID: UUID = UUID.fromString("4982823c-fad4-4c8d-d34d-a01ce869f90f")
    private val moreColorsUUID: UUID = UUID.fromString("602ac2c0-c675-4767-d34d-bd53de2f988d")
    private val rgbBlendingUUID: UUID = UUID.fromString("67c64040-c8c9-4704-d34d-d6e020c967af")
    private val startColorUUID: UUID = UUID.fromString("39954ec6-a452-4874-d34d-76b549c9a83d")
    private val endColorUUID: UUID = UUID.fromString("9dc5f598-f9be-482f-d34d-a13f1780349d")
    private val animationSpeedUUID: UUID = UUID.fromString("083e41d7-be33-4935-d34d-222797d964b2")

    private val minBrightness: Int = 4
    private val maxBrightness: Int = 255
    private val totalBrightness: Int = maxBrightness - minBrightness

    private val minAnimationDelay: Int = 10
    private val maxAnimationDelay: Int = 200
    private val totalAnimationDelay: Int = maxAnimationDelay - minAnimationDelay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enabled = findViewById(R.id.enable)
        enabled.setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
            val bleDevice = getBleDevice()
            if (bleDevice != null) {
                setEnabled(bleDevice, b)
            }
        }
        moreColors = findViewById(R.id.more_colors)
        moreColors.setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
            val bleDevice = getBleDevice()
            if (bleDevice != null) {
                setMoreColors(bleDevice, b)
            }
        }
        rgbBlending = findViewById(R.id.rgb_blending)
        rgbBlending.setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
            val bleDevice = getBleDevice()
            if (bleDevice != null) {
                setRgbBlending(bleDevice, b)
                moreColors.isEnabled = !b
            }
        }
        startColor = findViewById(R.id.start_color)
        endColor = findViewById(R.id.end_color)

        val sbg: ColorDrawable = startColor.background as ColorDrawable
        startColor.setOnClickListener {
            ColorPickerDialogBuilder
                .with(this)
                .setTitle(R.string.title_choose_start_color)
                .initialColor(sbg.color)
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .lightnessSliderOnly()
                .setPositiveButton(
                    R.string.button_ok
                ) { _, selectedColor, _ ->
                    // Handle Color Selection
                    startColor.setBackgroundColor(selectedColor)
                    val bleDevice = getBleDevice()
                    if (bleDevice != null) {
                        setStartColor(bleDevice, selectedColor)
                    }
                }
                .setNegativeButton(
                    R.string.button_cancel
                ) { _, _ -> }
                .build()
                .show()
        }

        val ebg: ColorDrawable = endColor.background as ColorDrawable
        endColor.setOnClickListener {
            ColorPickerDialogBuilder
                .with(this)
                .setTitle(R.string.title_choose_end_color)
                .initialColor(ebg.color)
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .lightnessSliderOnly()
                .setPositiveButton(
                    R.string.button_ok
                ) { _, selectedColor, _ ->
                    // Handle Color Selection
                    endColor.setBackgroundColor(selectedColor)
                    val bleDevice = getBleDevice()
                    if (bleDevice != null) {
                        setEndColor(bleDevice, selectedColor)
                    }
                }
                .setNegativeButton(
                    R.string.button_cancel
                ) { _, _ -> }
                .build()
                .show()
        }

        setupBrightnessSlider()
        setupAnimationDelaySlider()
        setupConnectUi()
    }

    private fun setupBrightnessSlider() {
        brightnessSlider = findViewById(R.id.brightness_level)
        brightnessSlider.positionListener = { pos -> brightnessSlider.bubbleText = "${minBrightness + (totalBrightness  * pos).toInt()}" }
        brightnessSlider.position = 0.0f
        brightnessSlider.startText ="$minBrightness"
        brightnessSlider.endText = "$maxBrightness"
        brightnessSlider.endTrackingListener = {
            val level: Int = minBrightness + (brightnessSlider.position * totalBrightness).toInt()
            val bleDevice = getBleDevice()
            if (bleDevice != null) {
                setBrightness(bleDevice, level)
            }
        }
    }

    private fun setupAnimationDelaySlider() {
        animationDelaySlider = findViewById(R.id.animation_speed)
        animationDelaySlider.positionListener = { pos -> animationDelaySlider.bubbleText = "${minAnimationDelay + (totalAnimationDelay  * pos).toInt()}" }
        animationDelaySlider.position = 0.0f
        animationDelaySlider.startText ="$minAnimationDelay"
        animationDelaySlider.endText = "$maxAnimationDelay"
        animationDelaySlider.endTrackingListener = {
            val level: Int = minAnimationDelay + (animationDelaySlider.position * totalAnimationDelay).toInt()
            val bleDevice = getBleDevice()
            if (bleDevice != null) {
                setAnimationSpeed(bleDevice, level)
            }
        }
    }

    private fun setupConnectUi() {
        connectButton = findViewById(R.id.connect_button)
        connectButton.setOnClickListener {
            val bleDevice = getBleDevice()
            if (bleDevice == null) {
                scanAndConnect()
            } else {
                BleManager.getInstance().disconnect(bleDevice)
            }
        }
        autoConnect = findViewById(R.id.auto_connect)
        val prefs = getSharedPreferences(null, 0)
        autoConnect.isChecked = prefs.getBoolean("auto_connect", false)
        autoConnect.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            prefs.edit().putBoolean(PREF_AUTO_CONNECT, checked).apply()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        disableControls()
        if (BleManager.getInstance().isBlueEnable) {
            checkPermissions()
        }
        initBle()
        val prefs = getSharedPreferences(null, 0)
        if (prefs.getBoolean(PREF_AUTO_CONNECT, false)) {
            scanAndConnect()
        }
    }

    override fun onPause() {
        super.onPause()
        BleManager.getInstance().disconnectAllDevice()
        connectedDevice = null
        disableControls()
    }

    private fun disableControls() {
        enabled.isEnabled = false
        brightnessSlider.isEnabled = false
        animationDelaySlider.isEnabled = false
        rgbBlending.isEnabled = false
        moreColors.isEnabled = false
        startColor.isEnabled = false
        endColor.isEnabled = false
    }

    private fun checkPermissions()  {
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
        val notGranted: ArrayList<String> = ArrayList(permissions.size)
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notGranted.add(permission)
            }
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), resultCode)
        }
    }

    private fun getBleDevice(): BleDevice? {
        return connectedDevice
    }

    private fun initBle() {
        val instance = BleManager.getInstance()
        instance.init(application)
        instance.enableLog(false).setSplitWriteNum(20).setReConnectCount(3, 2000L)
            .setConnectOverTime(10000L).operateTimeout = 5000
        if (!instance.isBlueEnable) {
            instance.enableBluetooth()
        }
    }

    private fun getEnabled(bleDevice: BleDevice) {
        BleManager.getInstance().read(
            bleDevice,
            serviceUUID.toString(),
            enabledUUID.toString(),
            object : BleReadCallback() {
                override fun onReadSuccess(data: ByteArray) {
                    runOnUiThread {
                        enabled.isChecked = data[0].toInt() != 0
                        enabled.isEnabled = true
                    }
                    Log.d(TAG, "getEnabled - onReadSuccess(${enabled.isChecked})");
                }
                override fun onReadFailure(exception: BleException) {
                    getEnabled(bleDevice)
                }
            })
    }

    private fun setEnabled(bleDevice: BleDevice, enabled: Boolean) {
        val e: Byte = if (enabled) 1 else 0
        val byteArray = byteArrayOf(e)
        BleManager.getInstance().write(bleDevice, serviceUUID.toString(), enabledUUID.toString(), byteArray, object:
            BleWriteCallback() {
            override fun onWriteSuccess(
                current: Int,
                total: Int,
                justWrite: ByteArray?
            ) {
                Log.d(TAG, "setEnabled - onWriteSuccess")
            }

            override fun onWriteFailure(exception: BleException?) {
                Log.d(TAG, "setEnabled - onWriteFailure(${exception}")
            }
        })
    }

    private fun getBrightnessLevel(bleDevice: BleDevice) {
        BleManager.getInstance().read(
            bleDevice,
            serviceUUID.toString(),
            brightnessUUID.toString(),
            object : BleReadCallback() {
                override fun onReadSuccess(data: ByteArray) {
                    var level: Int = data[0].toInt();
                    if (level < 0) {
                        level += 256
                    }
                    runOnUiThread {
                        brightnessSlider.position = level.toFloat() / totalBrightness.toFloat()
                        brightnessSlider.isEnabled = true
                    }
                    Log.d(TAG, "getBrightnessLevel - onReadSuccess(${level})");
                }
                override fun onReadFailure(exception: BleException) {
                    getBrightnessLevel(bleDevice)
                }
            })
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun setBrightness(bleDevice: BleDevice, level: Int) {
        val uByteArray = ubyteArrayOf(level.toUByte())
        BleManager.getInstance().write(bleDevice, serviceUUID.toString(), brightnessUUID.toString(), uByteArray.asByteArray(), object:
            BleWriteCallback() {
            override fun onWriteSuccess(
                current: Int,
                total: Int,
                justWrite: ByteArray?
            ) {
                Log.d(TAG, "setBrightness - onWriteSuccess")
            }

            override fun onWriteFailure(exception: BleException?) {
                Log.d(TAG, "setBrightness - onWriteFailure(${exception}")
            }
        })
    }

    private fun getMoreColors(bleDevice: BleDevice) {
        BleManager.getInstance().read(
            bleDevice,
            serviceUUID.toString(),
            moreColorsUUID.toString(),
            object : BleReadCallback() {
                override fun onReadSuccess(data: ByteArray) {
                    runOnUiThread {
                        moreColors.isChecked = data[0].toInt() != 0
                        moreColors.isEnabled = true
                    }
                    Log.d(TAG, "getMoreColors - onReadSuccess(${data[0]})");
                }
                override fun onReadFailure(exception: BleException) {
                    getMoreColors(bleDevice)
                }
            })
    }

    private fun setMoreColors(bleDevice: BleDevice, enabled: Boolean) {
        val e: Byte = if (enabled) 1 else 0
        val byteArray = byteArrayOf(e)
        BleManager.getInstance().write(bleDevice, serviceUUID.toString(), moreColorsUUID.toString(), byteArray, object:
            BleWriteCallback() {
            override fun onWriteSuccess(
                current: Int,
                total: Int,
                justWrite: ByteArray?
            ) {
                Log.d(TAG, "setMoreColors - onWriteSuccess")
            }

            override fun onWriteFailure(exception: BleException?) {
                Log.d(TAG, "setMoreColors - onWriteFailure(${exception}")
            }
        })
    }

    private fun getRgbBlending(bleDevice: BleDevice) {
        BleManager.getInstance().read(
            bleDevice,
            serviceUUID.toString(),
            rgbBlendingUUID.toString(),
            object : BleReadCallback() {
                override fun onReadSuccess(data: ByteArray) {
                    runOnUiThread {
                        rgbBlending.isChecked = data[0].toInt() != 0
                        rgbBlending.isEnabled = true
                        moreColors.isEnabled = !rgbBlending.isChecked
                    }
                    Log.d(TAG, "getMoreColors - onReadSuccess(${data[0]})");
                }
                override fun onReadFailure(exception: BleException) {
                    getRgbBlending(bleDevice)
                }
            })
    }

    private fun setRgbBlending(bleDevice: BleDevice, enabled: Boolean) {
        val e: Byte = if (enabled) 1 else 0
        val byteArray = byteArrayOf(e)
        BleManager.getInstance().write(bleDevice, serviceUUID.toString(), rgbBlendingUUID.toString(), byteArray, object:
            BleWriteCallback() {
            override fun onWriteSuccess(
                current: Int,
                total: Int,
                justWrite: ByteArray?
            ) {
                Log.d(TAG, "setRgbBlending - onWriteSuccess")
            }

            override fun onWriteFailure(exception: BleException?) {
                Log.d(TAG, "setRgbBlending - onWriteFailure(${exception}")
            }
        })
    }

    private fun getStartColor(bleDevice: BleDevice) {
        BleManager.getInstance().read(
            bleDevice,
            serviceUUID.toString(),
            startColorUUID.toString(),
            object : BleReadCallback() {
                override fun onReadSuccess(data: ByteArray) {
                    if (data.size != 3) {
                        Log.e(TAG, "getStartColor - Expected 3 bytes but received ${data.size}??")
                    } else {
                        val r = data[0].toUByte()
                        val g = data[1].toUByte()
                        val b = data[2].toUByte()
                        val color = (0xff shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
                        Log.d(TAG, "getStartColor - color=${color}   r=${r} g=${g} b=${b}")
                        runOnUiThread {
                            startColor.setBackgroundColor(color)
                            startColor.isEnabled = true
                        }
                    }

                }
                override fun onReadFailure(exception: BleException) {
                    getStartColor(bleDevice)
                }
            })
    }

    private fun setStartColor(bleDevice: BleDevice, color: Int) {
        val byteArray: Array<Byte> = arrayOf(((color shr 16) and 0xff).toByte(), ((color shr 8) and 0xff).toByte(), (color and 0xff).toByte())
        BleManager.getInstance().write(connectedDevice, serviceUUID.toString(), startColorUUID.toString(), byteArray.toByteArray(), object:
            BleWriteCallback() {
            override fun onWriteSuccess(
                current: Int,
                total: Int,
                justWrite: ByteArray?
            ) {
                Log.d(TAG, "setStartColor - onWriteSuccess")
            }

            override fun onWriteFailure(exception: BleException?) {
                Log.d(TAG, "setStartColor - onWriteFailure(${exception}")
            }
        })
    }

    private fun getEndColor(bleDevice: BleDevice) {
        BleManager.getInstance().read(
            bleDevice,
            serviceUUID.toString(),
            endColorUUID.toString(),
            object : BleReadCallback() {
                override fun onReadSuccess(data: ByteArray) {
                    if (data.size != 3) {
                        Log.e(TAG, "getEndColor - Expected 3 bytes but received ${data.size}??")
                    } else {
                        val r = data[0].toUByte()
                        val g = data[1].toUByte()
                        val b = data[2].toUByte()
                        val color = (0xff shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
                        Log.d(TAG, "getEndColor - color=${color}   r=${r} g=${g} b=${b}")
                        runOnUiThread {
                            endColor.setBackgroundColor(color)
                            endColor.isEnabled = true
                        }
                    }

                }
                override fun onReadFailure(exception: BleException) {
                    getEndColor(bleDevice)
                }
            })
    }

    private fun setEndColor(bleDevice: BleDevice, color: Int) {
        val byteArray: Array<Byte> = arrayOf(((color shr 16) and 0xff).toByte(), ((color shr 8) and 0xff).toByte(), (color and 0xff).toByte())
        BleManager.getInstance().write(bleDevice, serviceUUID.toString(), endColorUUID.toString(), byteArray.toByteArray(), object:
            BleWriteCallback() {
            override fun onWriteSuccess(
                current: Int,
                total: Int,
                justWrite: ByteArray?
            ) {
                Log.d(TAG, "setEndColor - onWriteSuccess")
            }

            override fun onWriteFailure(exception: BleException?) {
                Log.d(TAG, "setEndColor - onWriteFailure(${exception}")
            }
        })
    }

    private fun getAnimationSpeed(bleDevice: BleDevice) {
        BleManager.getInstance().read(
            bleDevice,
            serviceUUID.toString(),
            animationSpeedUUID.toString(),
            object : BleReadCallback() {
                override fun onReadSuccess(data: ByteArray) {
                    val b1 = data[0].toUByte()
                    val b2 = data[1].toUByte()
                    val speed = (b2.toInt() shl 8) and 0xff or b1.toInt()
                    runOnUiThread {
                        animationDelaySlider.position = speed.toFloat() / totalAnimationDelay.toFloat()
                        animationDelaySlider.isEnabled = true
                    }
                    Log.d(TAG, "getAnimationSpeed - onReadSuccess(${speed})");
                }
                override fun onReadFailure(exception: BleException) {
                    getAnimationSpeed(bleDevice)
                }
            })
    }

    private fun setAnimationSpeed(bleDevice: BleDevice, speed: Int) {
        val byteArray: Array<Byte> = arrayOf(((speed shr 8) and 0xff).toByte(), (speed and 0xff).toByte())
        BleManager.getInstance().write(bleDevice, serviceUUID.toString(), animationSpeedUUID.toString(), byteArray.toByteArray(), object:
            BleWriteCallback() {
            override fun onWriteSuccess(
                current: Int,
                total: Int,
                justWrite: ByteArray?
            ) {
                Log.d(TAG, "setAnimationSpeed - onWriteSuccess")
            }

            override fun onWriteFailure(exception: BleException?) {
                Log.d(TAG, "setAnimationSpeed - onWriteFailure(${exception}")
            }
        })
    }

    private fun scanAndConnect() {
        connectButton.isEnabled = false
        val bleManager = BleManager.getInstance()
        if (bleManager.isBlueEnable) {
            val bleScanRuleConfig = BleScanRuleConfig.Builder()
                .setServiceUuids(arrayOf(serviceUUID))
                .setScanTimeOut(5000L)
                .build()
            bleManager.initScanRule(bleScanRuleConfig)
            bleManager.scanAndConnect(object: BleScanAndConnectCallback() {
                override fun onStartConnect() {
                    Log.d(TAG, "onStartConnect()")
                }

                override fun onConnectFail(bleDevice: BleDevice?, exception: BleException?) {
                    Log.d(TAG, "onConnectFail(${bleDevice}, ${exception})")
                }

                override fun onConnectSuccess(
                    bleDevice: BleDevice?,
                    gatt: BluetoothGatt?,
                    status: Int
                ) {
                    connectedDevice = bleDevice
                    connectButton.text = getString(R.string.disconnect_label)
                    connectButton.isEnabled = true
                    if (bleDevice != null) {
                        Thread {
                            getEnabled(bleDevice)
                            getBrightnessLevel(bleDevice)
                            getMoreColors(bleDevice)
                            getRgbBlending(bleDevice)
                            getStartColor(bleDevice)
                            getEndColor(bleDevice)
                            getAnimationSpeed(bleDevice)
                        }.start()
                    };
                }

                override fun onDisConnected(
                    isActiveDisConnected: Boolean,
                    device: BleDevice?,
                    gatt: BluetoothGatt?,
                    status: Int
                ) {
                    Log.d(TAG, "onDisConnected(${isActiveDisConnected}, ${device}, ${gatt}, ${status})")
                    connectedDevice = null
                    connectButton.text = getString(R.string.connect_label)
                    connectButton.isEnabled = true
                    disableControls()
                }

                override fun onScanStarted(success: Boolean) {
                    Log.d(TAG, "onScanStarted(${success})")
                }

                @SuppressLint("MissingPermission")
                override fun onScanning(bleDevice: BleDevice?) {
                    Log.d(TAG, "onScanning(${bleDevice!!.device.name})")
                }

                override fun onScanFinished(scanResult: BleDevice?) {
                    Log.d(TAG, "onScanFinished");
                }
            })
        }
    }
}