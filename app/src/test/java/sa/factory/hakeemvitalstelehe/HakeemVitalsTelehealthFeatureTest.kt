package sa.factory.hakeemvitalstelehe

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.factory.hakeemvitalstelehe.domain.telehealth.*
import sa.factory.hakeemvitalstelehe.hardware.gatt.*

class HakeemVitalsTelehealthFeatureTest {

    private lateinit var gattKernel: BleTelehealthGattKernel
    private lateinit var triageEngine: SehaTelehealthTriageEngine

    @Before
    fun setUp() {
        gattKernel = BleTelehealthGattKernel()
        triageEngine = SehaTelehealthTriageEngine()
    }

    @Test
    fun testIeee11073SfloatEncodingAndDecoding() {
        // SpO2: 98.5%
        val encodedSpo2 = Ieee11073Sfloat.encode(98.5, precision = 1)
        val decodedSpo2 = Ieee11073Sfloat.decode(encodedSpo2)
        assertEquals(98.5, decodedSpo2, 0.05)

        // Pulse Rate: 72.0 BPM
        val encodedPr = Ieee11073Sfloat.encode(72.0, precision = 1)
        val decodedPr = Ieee11073Sfloat.decode(encodedPr)
        assertEquals(72.0, decodedPr, 0.05)

        // Perfusion Index: 3.45%
        val encodedPi = Ieee11073Sfloat.encode(3.45, precision = 2)
        val decodedPi = Ieee11073Sfloat.decode(encodedPi)
        assertEquals(3.45, decodedPi, 0.05)

        // Blood Pressure: 128.0 mmHg
        val encodedBp = Ieee11073Sfloat.encode(128.0, precision = 1)
        val decodedBp = Ieee11073Sfloat.decode(encodedBp)
        assertEquals(128.0, decodedBp, 0.05)
    }

    @Test
    fun testPlxContinuousMeasurementPacketRoundtrip() {
        val original = PlxContinuousReading(
            spo2Pct = 97.4,
            pulseRateBpm = 72.0,
            perfusionIndexPct = 3.12,
            isProbeConnected = true
        )

        val packet = gattKernel.encodePlxContinuous(original)
        // 1 byte flags + 2 bytes SpO2 + 2 bytes PR + 2 bytes PI = 7 bytes
        assertEquals(7, packet.size)
        // Bit 4 set for PI presence (0x10)
        assertEquals(0x10.toByte(), packet[0])

        val decoded = gattKernel.decodePlxContinuous(packet)
        assertEquals(original.spo2Pct, decoded.spo2Pct, 0.1)
        assertEquals(original.pulseRateBpm, decoded.pulseRateBpm, 0.1)
        assertNotNull(decoded.perfusionIndexPct)
        assertEquals(original.perfusionIndexPct ?: 0.0, decoded.perfusionIndexPct ?: 0.0, 0.05)
        assertTrue(decoded.isProbeConnected)
    }

    @Test
    fun testBloodPressureMeasurementPacketRoundtrip() {
        val original = BloodPressureReading(
            systolicMmHg = 125.0,
            diastolicMmHg = 82.0,
            meanArterialPressureMmHg = 96.3,
            pulseRateBpm = 70.0,
            isCuffTooLoose = false,
            isIrregularPulseDetected = false
        )

        val packet = gattKernel.encodeBloodPressure(original)
        // 1 byte flags + 2 sys + 2 dia + 2 map + 2 pr = 9 bytes
        assertEquals(9, packet.size)
        // Bit 2 set for Pulse Rate presence (0x04)
        assertEquals(0x04.toByte(), packet[0])

        val decoded = gattKernel.decodeBloodPressure(packet)
        assertEquals(original.systolicMmHg, decoded.systolicMmHg, 0.1)
        assertEquals(original.diastolicMmHg, decoded.diastolicMmHg, 0.1)
        assertEquals(original.meanArterialPressureMmHg, decoded.meanArterialPressureMmHg, 0.1)
        assertNotNull(decoded.pulseRateBpm)
        assertEquals(original.pulseRateBpm ?: 0.0, decoded.pulseRateBpm ?: 0.0, 0.1)
        assertFalse(decoded.isCuffTooLoose)
    }

    @Test
    fun testClinicalHypoxemiaTriageClassification() {
        // Normal (>= 95%)
        assertEquals(OxygenSaturationTriage.NORMAL_OXYGENATION, triageEngine.classifyOxygenSaturation(98.0))
        assertFalse(triageEngine.classifyOxygenSaturation(98.0).isEmergency)

        // Mild Hypoxia (91% - 94%)
        assertEquals(OxygenSaturationTriage.MILD_HYPOXIA, triageEngine.classifyOxygenSaturation(92.5))
        assertFalse(triageEngine.classifyOxygenSaturation(92.5).isEmergency)

        // Moderate Hypoxia (86% - 90%)
        assertEquals(OxygenSaturationTriage.MODERATE_HYPOXIA, triageEngine.classifyOxygenSaturation(88.0))
        assertTrue(triageEngine.classifyOxygenSaturation(88.0).isEmergency)

        // Critical Hypoxia (< 86%)
        assertEquals(OxygenSaturationTriage.CRITICAL_HYPOXIA, triageEngine.classifyOxygenSaturation(82.0))
        assertTrue(triageEngine.classifyOxygenSaturation(82.0).isEmergency)
    }

    @Test
    fun testBloodPressureHypertensionStratification() {
        // Normal (< 120 and < 80)
        assertEquals(BloodPressureClassification.OPTIMAL_NORMAL, triageEngine.classifyBloodPressure(115.0, 75.0))

        // Elevated (120-129 and < 80)
        assertEquals(BloodPressureClassification.ELEVATED, triageEngine.classifyBloodPressure(124.0, 78.0))

        // Stage 1 (130-139 or 80-89)
        assertEquals(BloodPressureClassification.HYPERTENSION_STAGE_1, triageEngine.classifyBloodPressure(134.0, 84.0))

        // Stage 2 (>= 140 or >= 90)
        assertEquals(BloodPressureClassification.HYPERTENSION_STAGE_2, triageEngine.classifyBloodPressure(148.0, 94.0))

        // Hypertensive Crisis (> 180 or > 120)
        assertEquals(BloodPressureClassification.HYPERTENSIVE_CRISIS, triageEngine.classifyBloodPressure(192.0, 125.0))
        assertTrue(triageEngine.classifyBloodPressure(192.0, 125.0).isEmergency)
    }

    @Test
    fun testEndToEndTelehealthEncounterAndZatcaVatInvoice() {
        val spo2 = PlxContinuousReading(spo2Pct = 93.0, pulseRateBpm = 76.0, perfusionIndexPct = 2.10)
        val bp = BloodPressureReading(systolicMmHg = 136.0, diastolicMmHg = 86.0, meanArterialPressureMmHg = 102.7, pulseRateBpm = 76.0)

        val vitalsEval = triageEngine.evaluateVitals("PAT-2026-8819", spo2, bp)
        assertEquals(OxygenSaturationTriage.MILD_HYPOXIA, vitalsEval.oxygenTriage)
        assertEquals(BloodPressureClassification.HYPERTENSION_STAGE_1, vitalsEval.bpClassification)
        assertEquals(ClinicalTriageSeverity.URGENT_YELLOW, vitalsEval.overallSeverity)

        val consultation = triageEngine.bookTelehealthConsultation(
            consultationId = "CONS-2026-9901",
            patientId = "PAT-2026-8819",
            patientNameAr = "سلمان بن عبدالعزيز الدوسري",
            doctorNameAr = "د. فهد بن ناصر القحطاني",
            specialty = DoctorConsultationSpecialty.CARDIOLOGY, // 350 SAR
            vitals = vitalsEval,
            prescriptionNote = "متابعة دورية مع فحص تخطيط القلب وتعديل الجرعة الدوائية"
        )

        // Subtotal = 350.0 (base) + 45.0 (remote monitoring add-on) = 395.0 SAR
        assertEquals(395.0, consultation.subtotalSar, 0.01)
        // ZATCA VAT (15%) = 395.0 * 0.15 = 59.25 SAR
        assertEquals(59.25, consultation.vatSar, 0.01)
        // Grand Total = 454.25 SAR
        assertEquals(454.25, consultation.totalSar, 0.01)
        assertTrue(consultation.isSehaVirtualHospitalSynced)
    }
}
