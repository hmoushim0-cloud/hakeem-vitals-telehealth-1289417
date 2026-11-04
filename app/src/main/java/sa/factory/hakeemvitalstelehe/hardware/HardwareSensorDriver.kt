package sa.factory.hakeemvitalstelehe.hardware

import sa.factory.hakeemvitalstelehe.hardware.gatt.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    private val peripheral = "Bluetooth BLE Pulse Oximeter & SpO2 GATT 0x1822 + BP 0x1810"
    private val gattKernel = BleTelehealthGattKernel()

    fun getGattKernel(): BleTelehealthGattKernel = gattKernel

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "BLE Central: Scanning for Medical GATT Services 0x1822 & 0x1810..."))
        delay(300)

        // Generate baseline sample packets
        val basePlx = PlxContinuousReading(spo2Pct = 98.2, pulseRateBpm = 74.0, perfusionIndexPct = 2.45)
        val plxBytes = gattKernel.encodePlxContinuous(basePlx)

        val baseBp = BloodPressureReading(systolicMmHg = 118.0, diastolicMmHg = 78.0, meanArterialPressureMmHg = 91.3, pulseRateBpm = 74.0)
        val bpBytes = gattKernel.encodeBloodPressure(baseBp)

        emit(HardwareTelemetryFrame(
            peripheral,
            "ONLINE",
            "GATT Connected: PLX [0x2A5F: ${plxBytes.toHexString()}] | BP [0x2A35: ${bpBytes.toHexString()}] | Seha Telehealth Link Active"
        ))

        var frameCounter = 1
        while (true) {
            delay(1500)
            val jitter = Random.nextInt(1, 10)
            val simulatedSpo2 = 96.0 + Random.nextDouble(-1.5, 3.5)
            val simulatedPr = 70.0 + Random.nextDouble(-5.0, 15.0)
            val simulatedPi = 1.8 + Random.nextDouble(0.1, 1.5)

            val plx = PlxContinuousReading(
                spo2Pct = Math.round(simulatedSpo2 * 10.0) / 10.0,
                pulseRateBpm = Math.round(simulatedPr * 10.0) / 10.0,
                perfusionIndexPct = Math.round(simulatedPi * 100.0) / 100.0
            )
            val encodedPlx = gattKernel.encodePlxContinuous(plx)
            val decodedPlx = gattKernel.decodePlxContinuous(encodedPlx)

            val simulatedSys = 118.0 + Random.nextInt(-4, 12)
            val simulatedDia = 76.0 + Random.nextInt(-3, 8)
            val simulatedMap = simulatedDia + (simulatedSys - simulatedDia) / 3.0

            val bp = BloodPressureReading(
                systolicMmHg = simulatedSys,
                diastolicMmHg = simulatedDia,
                meanArterialPressureMmHg = Math.round(simulatedMap * 10.0) / 10.0,
                pulseRateBpm = decodedPlx.pulseRateBpm
            )
            val encodedBp = gattKernel.encodeBloodPressure(bp)
            val decodedBp = gattKernel.decodeBloodPressure(encodedBp)

            val payload = String.format(
                Locale.US,
                "PLX GATT 0x1822: SpO2 %.1f%% | PR %.0f BPM | PI %.2f%% | BP: %.0f/%.0f mmHg (MAP %.1f) | Frame #%d (+%dms)",
                decodedPlx.spo2Pct,
                decodedPlx.pulseRateBpm,
                decodedPlx.perfusionIndexPct ?: 2.0,
                decodedBp.systolicMmHg,
                decodedBp.diastolicMmHg,
                decodedBp.meanArterialPressureMmHg,
                frameCounter,
                jitter
            )

            emit(HardwareTelemetryFrame(peripheral, "ACTIVE_STREAMING", payload))
            frameCounter++
        }
    }
}
