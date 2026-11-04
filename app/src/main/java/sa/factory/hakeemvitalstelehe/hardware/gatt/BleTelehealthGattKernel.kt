package sa.factory.hakeemvitalstelehe.hardware.gatt

import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.Locale

/**
 * IEEE-11073 16-bit SFLOAT (Short Float) Codec
 * 4-bit signed exponent (base 10) + 12-bit signed mantissa
 */
object Ieee11073Sfloat {
    const val SFLOAT_NAN = 0x07FF
    const val SFLOAT_NRES = 0x0800
    const val SFLOAT_POSITIVE_INFINITY = 0x07FE
    const val SFLOAT_NEGATIVE_INFINITY = 0x0802

    fun decode(sfloat: Int): Double {
        val u16 = sfloat and 0xFFFF
        if (u16 == SFLOAT_NAN) return Double.NaN
        if (u16 == SFLOAT_NRES) return Double.NaN
        if (u16 == SFLOAT_POSITIVE_INFINITY) return Double.POSITIVE_INFINITY
        if (u16 == SFLOAT_NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY

        // 4-bit signed exponent
        var rawExp = (u16 ushr 12) and 0x0F
        if (rawExp >= 0x08) {
            rawExp -= 0x10
        }

        // 12-bit signed mantissa
        var rawMant = u16 and 0x0FFF
        if (rawMant >= 0x0800) {
            rawMant -= 0x1000
        }

        val value = rawMant.toDouble() * Math.pow(10.0, rawExp.toDouble())
        return Math.round(value * 100.0) / 100.0
    }

    fun encode(value: Double, precision: Int = 1): Int {
        if (value.isNaN()) return SFLOAT_NAN
        if (value.isInfinite()) {
            return if (value > 0) SFLOAT_POSITIVE_INFINITY else SFLOAT_NEGATIVE_INFINITY
        }

        val exponent = -precision
        val mantissa = Math.round(value * Math.pow(10.0, precision.toDouble())).toInt()

        val expBits = (exponent and 0x0F) shl 12
        val mantBits = mantissa and 0x0FFF
        return expBits or mantBits
    }
}

/**
 * Pulse Oximeter Continuous Telemetry Model (GATT 0x1822 / 0x2A5F)
 */
data class PlxContinuousReading(
    val spo2Pct: Double,
    val pulseRateBpm: Double,
    val perfusionIndexPct: Double? = null,
    val isProbeConnected: Boolean = true,
    val isPulseSearching: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * Blood Pressure Measurement Telemetry Model (GATT 0x1810 / 0x2A35)
 */
data class BloodPressureReading(
    val systolicMmHg: Double,
    val diastolicMmHg: Double,
    val meanArterialPressureMmHg: Double,
    val pulseRateBpm: Double? = null,
    val isCuffTooLoose: Boolean = false,
    val isIrregularPulseDetected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * Bluetooth SIG GATT Telehealth Protocol Kernel
 */
class BleTelehealthGattKernel {

    companion object {
        // Bluetooth SIG Assigned Numbers
        const val GATT_SERVICE_BLOOD_PRESSURE = 0x1810
        const val GATT_SERVICE_PULSE_OXIMETER = 0x1822

        const val GATT_CHAR_BP_MEASUREMENT = 0x2A35
        const val GATT_CHAR_PLX_CONTINUOUS = 0x2A5F
        const val GATT_CHAR_PLX_SPOT_CHECK = 0x2A5E
    }

    /**
     * Encodes PLX Continuous Measurement packet (GATT 0x2A5F)
     * [0] Flags: Bit0=FastSpO2, Bit1=SlowSpO2, Bit2=Status, Bit3=DeviceStatus, Bit4=PulseAmpIndex
     * [1-2] SpO2 Normal (SFLOAT)
     * [3-4] Pulse Rate Normal (SFLOAT)
     * [Optional 5-6] Pulse Amplitude Index / Perfusion Index (SFLOAT)
     */
    fun encodePlxContinuous(reading: PlxContinuousReading): ByteArray {
        val out = ByteArrayOutputStream()

        var flags = 0
        if (reading.perfusionIndexPct != null) {
            flags = flags or 0x10 // Bit 4: Pulse Amplitude Index present
        }
        out.write(flags)

        // SpO2 SFLOAT (precision 1 decimal place, e.g. 98.0%)
        val spo2Sfloat = Ieee11073Sfloat.encode(reading.spo2Pct, 1)
        out.write(spo2Sfloat and 0xFF)
        out.write((spo2Sfloat ushr 8) and 0xFF)

        // Pulse Rate SFLOAT (precision 0 or 1, e.g. 74.0 BPM)
        val prSfloat = Ieee11073Sfloat.encode(reading.pulseRateBpm, 1)
        out.write(prSfloat and 0xFF)
        out.write((prSfloat ushr 8) and 0xFF)

        // Optional Perfusion Index SFLOAT (precision 2 decimal places, e.g. 2.45%)
        if (reading.perfusionIndexPct != null) {
            val piSfloat = Ieee11073Sfloat.encode(reading.perfusionIndexPct, 2)
            out.write(piSfloat and 0xFF)
            out.write((piSfloat ushr 8) and 0xFF)
        }

        return out.toByteArray()
    }

    /**
     * Decodes PLX Continuous Measurement packet (GATT 0x2A5F)
     */
    fun decodePlxContinuous(bytes: ByteArray): PlxContinuousReading {
        require(bytes.size >= 5) { "PLX packet requires at least 5 bytes" }

        val flags = bytes[0].toInt() and 0xFF
        val hasPerfusionIndex = (flags and 0x10) != 0

        val spo2Raw = (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        val spo2 = Ieee11073Sfloat.decode(spo2Raw)

        val prRaw = (bytes[3].toInt() and 0xFF) or ((bytes[4].toInt() and 0xFF) shl 8)
        val pr = Ieee11073Sfloat.decode(prRaw)

        var perfusionIndex: Double? = null
        if (hasPerfusionIndex && bytes.size >= 7) {
            val piRaw = (bytes[5].toInt() and 0xFF) or ((bytes[6].toInt() and 0xFF) shl 8)
            perfusionIndex = Ieee11073Sfloat.decode(piRaw)
        }

        return PlxContinuousReading(
            spo2Pct = Math.round(spo2 * 10.0) / 10.0,
            pulseRateBpm = Math.round(pr * 10.0) / 10.0,
            perfusionIndexPct = if (perfusionIndex != null) Math.round(perfusionIndex * 100.0) / 100.0 else null,
            isProbeConnected = true,
            isPulseSearching = false,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Encodes Blood Pressure Measurement packet (GATT 0x2A35)
     * [0] Flags: Bit0=Units (0=mmHg), Bit2=PulseRatePresent, Bit4=StatusPresent
     * [1-2] Systolic (SFLOAT)
     * [3-4] Diastolic (SFLOAT)
     * [5-6] Mean Arterial Pressure (SFLOAT)
     * [Optional 7-8] Pulse Rate (SFLOAT)
     */
    fun encodeBloodPressure(reading: BloodPressureReading): ByteArray {
        val out = ByteArrayOutputStream()

        var flags = 0 // Bit 0 = 0 (mmHg)
        if (reading.pulseRateBpm != null) {
            flags = flags or 0x04 // Bit 2: Pulse Rate Present
        }
        if (reading.isCuffTooLoose || reading.isIrregularPulseDetected) {
            flags = flags or 0x10 // Bit 4: Measurement Status Present
        }
        out.write(flags)

        // Systolic
        val sysSfloat = Ieee11073Sfloat.encode(reading.systolicMmHg, 1)
        out.write(sysSfloat and 0xFF)
        out.write((sysSfloat ushr 8) and 0xFF)

        // Diastolic
        val diaSfloat = Ieee11073Sfloat.encode(reading.diastolicMmHg, 1)
        out.write(diaSfloat and 0xFF)
        out.write((diaSfloat ushr 8) and 0xFF)

        // Mean Arterial Pressure
        val mapSfloat = Ieee11073Sfloat.encode(reading.meanArterialPressureMmHg, 1)
        out.write(mapSfloat and 0xFF)
        out.write((mapSfloat ushr 8) and 0xFF)

        // Optional Pulse Rate
        if (reading.pulseRateBpm != null) {
            val prSfloat = Ieee11073Sfloat.encode(reading.pulseRateBpm, 1)
            out.write(prSfloat and 0xFF)
            out.write((prSfloat ushr 8) and 0xFF)
        }

        // Optional Measurement Status (2 bytes)
        if (reading.isCuffTooLoose || reading.isIrregularPulseDetected) {
            var statusFlags = 0
            if (reading.isCuffTooLoose) statusFlags = statusFlags or 0x02 // Bit 1: Cuff Too Loose
            if (reading.isIrregularPulseDetected) statusFlags = statusFlags or 0x04 // Bit 2: Irregular Pulse
            out.write(statusFlags and 0xFF)
            out.write((statusFlags ushr 8) and 0xFF)
        }

        return out.toByteArray()
    }

    /**
     * Decodes Blood Pressure Measurement packet (GATT 0x2A35)
     */
    fun decodeBloodPressure(bytes: ByteArray): BloodPressureReading {
        require(bytes.size >= 7) { "Blood Pressure packet requires at least 7 bytes" }

        val flags = bytes[0].toInt() and 0xFF
        val hasPulseRate = (flags and 0x04) != 0
        val hasStatus = (flags and 0x10) != 0

        val sysRaw = (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        val sys = Ieee11073Sfloat.decode(sysRaw)

        val diaRaw = (bytes[3].toInt() and 0xFF) or ((bytes[4].toInt() and 0xFF) shl 8)
        val dia = Ieee11073Sfloat.decode(diaRaw)

        val mapRaw = (bytes[5].toInt() and 0xFF) or ((bytes[6].toInt() and 0xFF) shl 8)
        val map = Ieee11073Sfloat.decode(mapRaw)

        var pulseRate: Double? = null
        var offset = 7
        if (hasPulseRate && bytes.size >= offset + 2) {
            val prRaw = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            pulseRate = Ieee11073Sfloat.decode(prRaw)
            offset += 2
        }

        var cuffLoose = false
        var irregularPulse = false
        if (hasStatus && bytes.size >= offset + 2) {
            val statusVal = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            cuffLoose = (statusVal and 0x02) != 0
            irregularPulse = (statusVal and 0x04) != 0
        }

        return BloodPressureReading(
            systolicMmHg = Math.round(sys * 10.0) / 10.0,
            diastolicMmHg = Math.round(dia * 10.0) / 10.0,
            meanArterialPressureMmHg = Math.round(map * 10.0) / 10.0,
            pulseRateBpm = if (pulseRate != null) Math.round(pulseRate * 10.0) / 10.0 else null,
            isCuffTooLoose = cuffLoose,
            isIrregularPulseDetected = irregularPulse,
            timestamp = System.currentTimeMillis()
        )
    }
}

// Utility Byte Extensions
fun ByteArray.toHexString(): String {
    val hexChars = "0123456789ABCDEF"
    val result = StringBuilder(size * 2)
    for (b in this) {
        val octet = b.toInt() and 0xFF
        result.append(hexChars[octet ushr 4])
        result.append(hexChars[octet and 0x0F])
    }
    return result.toString()
}

fun String.hexToByteArray(): ByteArray {
    val clean = this.replace(" ", "").replace("-", "").replace(":", "")
    val len = clean.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        val high = Character.digit(clean[i], 16)
        val low = Character.digit(clean[i + 1], 16)
        data[i / 2] = ((high shl 4) + low).toByte()
        i += 2
    }
    return data
}
