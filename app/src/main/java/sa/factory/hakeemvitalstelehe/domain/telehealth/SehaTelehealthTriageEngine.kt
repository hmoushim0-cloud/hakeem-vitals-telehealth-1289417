package sa.factory.hakeemvitalstelehe.domain.telehealth

import sa.factory.hakeemvitalstelehe.hardware.gatt.BloodPressureReading
import sa.factory.hakeemvitalstelehe.hardware.gatt.PlxContinuousReading
import java.io.Serializable

/**
 * Oxygen Saturation (SpO2) Clinical Triage Tiers (Saudi MOH Standards)
 */
enum class OxygenSaturationTriage(val titleAr: String, val isEmergency: Boolean) {
    NORMAL_OXYGENATION("تشبع أكسجين طبيعي ومستقر (95% فأعلى)", false),
    MILD_HYPOXIA("نقص أكسجين طفيف (91% - 94%) - متابعة سريرية عن بعد", false),
    MODERATE_HYPOXIA("نقص أكسجين متوسط (86% - 90%) - استشارة عاجلة ومراقبة مستمرة", true),
    CRITICAL_HYPOXIA("نقص أكسجين حرج (أقل من 86%) - نقل فوري لطوارئ المستشفى", true)
}

/**
 * Blood Pressure AHA / Saudi Heart Association Classification
 */
enum class BloodPressureClassification(val titleAr: String, val isEmergency: Boolean) {
    OPTIMAL_NORMAL("ضغط دم طبيعي ومثالي", false),
    ELEVATED("ضغط دم مرتفع طفيف", false),
    HYPERTENSION_STAGE_1("ارتفاع ضغط الدم - المرحلة الأولى", false),
    HYPERTENSION_STAGE_2("ارتفاع ضغط الدم - المرحلة الثانية", false),
    HYPERTENSIVE_CRISIS("أزمة فرط ضغط الدم الحادة - إنذار أحمر طارئ", true)
}

/**
 * Telehealth Clinical Severity Score
 */
enum class ClinicalTriageSeverity(val code: String, val titleAr: String) {
    ROUTINE_GREEN("GREEN", "حالة روتينية مستقرة"),
    URGENT_YELLOW("YELLOW", "حالة عاجلة تستدعي تدخلا طبيا خلال ساعتين"),
    EMERGENCY_RED("RED", "حالة طارئة حرجة تستدعي الإسعاف الفوري")
}

/**
 * Clinical Consultation Medical Specialty
 */
enum class DoctorConsultationSpecialty(val titleAr: String, val baseFeeSar: Double) {
    FAMILY_MEDICINE("طب الأسرة والرعاية الوقائية", 150.0),
    CARDIOLOGY("طب وجراحة القلب والأوعية الدموية", 350.0),
    PULMONOLOGY("طب الأمراض الصدرية والجهاز التنفسي", 320.0),
    INTERNAL_MEDICINE("الطب الباطني والأمراض المزمنة", 250.0)
}

/**
 * Multi-parameter Vitals Risk Stratification Result
 */
data class PatientVitalsEvaluation(
    val patientId: String,
    val spo2Reading: PlxContinuousReading,
    val bpReading: BloodPressureReading,
    val oxygenTriage: OxygenSaturationTriage,
    val bpClassification: BloodPressureClassification,
    val overallSeverity: ClinicalTriageSeverity,
    val clinicalRecommendationAr: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * Seha Virtual Hospital Integrated Telehealth Encounter
 */
data class TelehealthConsultation(
    val consultationId: String,
    val patientId: String,
    val patientNameAr: String,
    val doctorNameAr: String,
    val specialty: DoctorConsultationSpecialty,
    val vitals: PatientVitalsEvaluation,
    val baseFeeSar: Double,
    val remoteMonitoringAddonSar: Double,
    val subtotalSar: Double,
    val vatSar: Double, // 15% ZATCA VAT
    val totalSar: Double,
    val isSehaVirtualHospitalSynced: Boolean = true,
    val prescriptionSummaryAr: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Seha Virtual Hospital Clinical Triage & Teleconsultation Engine
 */
class SehaTelehealthTriageEngine {

    companion object {
        const val ZATCA_VAT_RATE = 0.15 // 15% Standard KSA VAT
        const val REMOTE_MONITORING_ADDON_SAR = 45.0
    }

    /**
     * Evaluates live multi-vital telemetry and generates clinical triage report
     */
    fun evaluateVitals(
        patientId: String,
        spo2: PlxContinuousReading,
        bp: BloodPressureReading
    ): PatientVitalsEvaluation {
        val o2Triage = classifyOxygenSaturation(spo2.spo2Pct)
        val bpClass = classifyBloodPressure(bp.systolicMmHg, bp.diastolicMmHg)
        val severity = resolveOverallSeverity(o2Triage, bpClass, spo2.pulseRateBpm)

        val recommendation = when (severity) {
            ClinicalTriageSeverity.EMERGENCY_RED ->
                "تحذير سريري حرج: المؤشرات الحيوية تدل على حالة طوارئ فورية. تم إرسال تنبيه الإسعاف ونظام صحتي الافتراضي."
            ClinicalTriageSeverity.URGENT_YELLOW ->
                "حالة عاجلة: تستوجب مراجعة فورية مع استشاري الأمراض الباطنية أو الصدرية خلال ساعتين."
            ClinicalTriageSeverity.ROUTINE_GREEN ->
                "المؤشرات الحيوية مستقرة وفي الحدود الفسيولوجية الطبيعية. استمر في المتابعة الدورية."
        }

        return PatientVitalsEvaluation(
            patientId = patientId,
            spo2Reading = spo2,
            bpReading = bp,
            oxygenTriage = o2Triage,
            bpClassification = bpClass,
            overallSeverity = severity,
            clinicalRecommendationAr = recommendation,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Classifies SpO2 reading into clinical hypoxemia stages
     */
    fun classifyOxygenSaturation(spo2Pct: Double): OxygenSaturationTriage {
        return when {
            spo2Pct >= 95.0 -> OxygenSaturationTriage.NORMAL_OXYGENATION
            spo2Pct >= 91.0 -> OxygenSaturationTriage.MILD_HYPOXIA
            spo2Pct >= 86.0 -> OxygenSaturationTriage.MODERATE_HYPOXIA
            else -> OxygenSaturationTriage.CRITICAL_HYPOXIA
        }
    }

    /**
     * Classifies blood pressure into Saudi Heart Association / AHA tiers
     */
    fun classifyBloodPressure(systolic: Double, diastolic: Double): BloodPressureClassification {
        return when {
            systolic > 180.0 || diastolic > 120.0 -> BloodPressureClassification.HYPERTENSIVE_CRISIS
            systolic >= 140.0 || diastolic >= 90.0 -> BloodPressureClassification.HYPERTENSION_STAGE_2
            systolic >= 130.0 || diastolic >= 80.0 -> BloodPressureClassification.HYPERTENSION_STAGE_1
            systolic >= 120.0 && diastolic < 80.0 -> BloodPressureClassification.ELEVATED
            else -> BloodPressureClassification.OPTIMAL_NORMAL
        }
    }

    /**
     * Synthesizes overall clinical severity
     */
    fun resolveOverallSeverity(
        o2: OxygenSaturationTriage,
        bp: BloodPressureClassification,
        pulseBpm: Double
    ): ClinicalTriageSeverity {
        if (o2 == OxygenSaturationTriage.CRITICAL_HYPOXIA ||
            bp == BloodPressureClassification.HYPERTENSIVE_CRISIS ||
            pulseBpm > 150.0 || pulseBpm < 40.0
        ) {
            return ClinicalTriageSeverity.EMERGENCY_RED
        }

        if (o2 == OxygenSaturationTriage.MODERATE_HYPOXIA ||
            o2 == OxygenSaturationTriage.MILD_HYPOXIA ||
            bp == BloodPressureClassification.HYPERTENSION_STAGE_2 ||
            pulseBpm > 115.0 || pulseBpm < 50.0
        ) {
            return ClinicalTriageSeverity.URGENT_YELLOW
        }

        return ClinicalTriageSeverity.ROUTINE_GREEN
    }

    /**
     * Books a telehealth appointment and creates ZATCA 15% VAT invoice
     */
    fun bookTelehealthConsultation(
        consultationId: String,
        patientId: String,
        patientNameAr: String,
        doctorNameAr: String,
        specialty: DoctorConsultationSpecialty,
        vitals: PatientVitalsEvaluation,
        prescriptionNote: String? = null
    ): TelehealthConsultation {
        val baseFee = specialty.baseFeeSar
        val monitoringFee = REMOTE_MONITORING_ADDON_SAR
        val subtotal = Math.round((baseFee + monitoringFee) * 100.0) / 100.0
        val vat = Math.round(subtotal * ZATCA_VAT_RATE * 100.0) / 100.0
        val total = Math.round((subtotal + vat) * 100.0) / 100.0

        return TelehealthConsultation(
            consultationId = consultationId,
            patientId = patientId,
            patientNameAr = patientNameAr,
            doctorNameAr = doctorNameAr,
            specialty = specialty,
            vitals = vitals,
            baseFeeSar = baseFee,
            remoteMonitoringAddonSar = monitoringFee,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total,
            isSehaVirtualHospitalSynced = true,
            prescriptionSummaryAr = prescriptionNote,
            createdAt = System.currentTimeMillis()
        )
    }
}
