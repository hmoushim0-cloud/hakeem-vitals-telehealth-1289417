package sa.factory.hakeemvitalstelehe.data.repository

import sa.factory.hakeemvitalstelehe.domain.repository.HakeemVitalsRepository
import sa.factory.hakeemvitalstelehe.hardware.HardwareSensorDriver
import sa.factory.hakeemvitalstelehe.model.HakeemVitalsItem
import sa.factory.hakeemvitalstelehe.model.HakeemVitalsOrder
import sa.factory.hakeemvitalstelehe.model.HakeemVitalsTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HakeemVitalsRepositoryImpl(
    private val sensorDriver: HardwareSensorDriver
) : HakeemVitalsRepository {

    private val _catalog = MutableStateFlow<List<HakeemVitalsItem>>(listOf(
        HakeemVitalsItem("1", "تحقق أكاديمي كامل", "مراجعة منهجية لبيانات أجهزة BLE مع تقرير تحليلي من خبراء تقنيين", 350.0, "معتمد", "تحقق أكاديمي"),
        HakeemVitalsItem("2", "تحقق فوري", "تدقيق تلقائي لقراءات ضغط الدم والأكسجين مع تنبيهات الانحرافات", 120.0, "فوري", "تحليل نتائج"),
        HakeemVitalsItem("3", "تخصيص معايير التحقق", "ضبط عتبات التحقق وفقًا لبروتوكولات بحثية مخصصة", 280.0, "جديد", "توثيق طبي")
    ))
    override val catalogFlow: Flow<List<HakeemVitalsItem>> = _catalog.asStateFlow()

    private val _cart = MutableStateFlow<List<HakeemVitalsItem>>(emptyList())
    override val activeCart: StateFlow<List<HakeemVitalsItem>> = _cart.asStateFlow()

    override val telemetryFlow: Flow<HakeemVitalsTelemetry> = sensorDriver.observePeripheralStream()
        .map { frame ->
            HakeemVitalsTelemetry(
                peripheralName = frame.peripheralName,
                status = frame.connectionState,
                liveReading = frame.livePayload,
                batteryPct = 96,
                signalDbm = -54
            )
        }

    override fun addToCart(item: HakeemVitalsItem) {
        val current = _cart.value.toMutableList()
        current.add(item)
        _cart.value = current
    }

    override fun removeFromCart(item: HakeemVitalsItem) {
        val current = _cart.value.toMutableList()
        current.remove(item)
        _cart.value = current
    }

    override fun clearCart() {
        _cart.value = emptyList()
    }

    override suspend fun checkout(paymentMethod: String): HakeemVitalsOrder {
        val currentItems = _cart.value.toList()
        val subtotal = currentItems.sumOf { it.priceSar }
        val vat = subtotal * 0.15
        val total = subtotal + vat
        val order = HakeemVitalsOrder(
            orderId = "ORD-${System.currentTimeMillis() % 100000}",
            items = currentItems,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total,
            paymentMethod = paymentMethod
        )
        clearCart()
        return order
    }

    override suspend fun refreshCatalog() {
        // Offline-first sync logic
    }
}
