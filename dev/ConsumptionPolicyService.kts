import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.ostelco.ocs.api.MultipleServiceCreditControl
import org.ostelco.prime.getLogger
import org.ostelco.prime.ocs.core.ConsumptionPolicy
import org.ostelco.prime.ocs.core.ConsumptionRequest
import org.ostelco.prime.storage.ConsumptionResult

private data class ServiceIdRatingGroup(
        val serviceId: Long,
        val ratingGroup: Long
)

enum class Mcc(val value: String) {
    AUSTRALIA("505"),
    CHINA("460"),
    HONG_KONG("454"),
    INDONESIA("510"),
    JAPAN("440"),
    MALAYSIA("502"),
    NORWAY("242"),
    PHILIPPINES("515"),
    THAILAND("520"),
    SINGAPORE("525"),
    SOUTH_KOREA("450"),
    VIET_NAM("452")
}

enum class MccMnc(val value: String) {
    M1("52503"),
    DIGI("50216"),
    LOLTEL("24201")
}

object : ConsumptionPolicy {

    private val logger by getLogger()

    private val digiAllowedMcc = setOf(
            Mcc.AUSTRALIA.value,
            Mcc.CHINA.value,
            Mcc.HONG_KONG.value,
            Mcc.INDONESIA.value,
            Mcc.JAPAN.value,
            Mcc.MALAYSIA.value,
            Mcc.NORWAY.value,
            Mcc.PHILIPPINES.value,
            Mcc.SINGAPORE.value,
            Mcc.THAILAND.value,
            Mcc.SOUTH_KOREA.value,
            Mcc.VIET_NAM.value
    )

    override fun checkConsumption(
            msisdn: String,
            multipleServiceCreditControl: MultipleServiceCreditControl,
            sgsnMccMnc: String,
            apn: String,
            imsiMccMnc: String): Either<ConsumptionResult, ConsumptionRequest> {

        val requested = multipleServiceCreditControl.requested?.totalOctets ?: 0
        val used = multipleServiceCreditControl.used?.totalOctets ?: 0

        if (!isMccMncAllowed(sgsnMccMnc, imsiMccMnc)) {
            logger.warn("Blocked usage for sgsnMccMnc $sgsnMccMnc imsiMccMnc $imsiMccMnc msisdn $msisdn ")
            return blockConsumption(msisdn)
        }

        return when (ServiceIdRatingGroup(
                serviceId = multipleServiceCreditControl.serviceIdentifier,
                ratingGroup = multipleServiceCreditControl.ratingGroup)) {

            // NORMAL
            ServiceIdRatingGroup(400L, 400L),       // TATA
            ServiceIdRatingGroup(-1L, 600L),        // M1
            ServiceIdRatingGroup(1L, 10L),          // LolTel
            ServiceIdRatingGroup(-1L, 102010001L)   /* Digi */ -> {
                ConsumptionRequest(
                        msisdn = msisdn,
                        usedBytes = used,
                        requestedBytes = requested
                ).right()
            }

            // ZERO-RATED
            ServiceIdRatingGroup(401L, 401L),       // TATA
            ServiceIdRatingGroup(402L, 402L),       // TATA
            ServiceIdRatingGroup(409L, 409L)        /* Digi */ -> {
                ConsumptionResult(
                        msisdnAnalyticsId = msisdn,
                        granted = multipleServiceCreditControl.requested.totalOctets,
                        balance = multipleServiceCreditControl.requested.totalOctets * 100
                ).left()
            }

            // BLOCKED
            else -> blockConsumption(msisdn)
        }
    }

    fun blockConsumption(msisdn: String) : Either<ConsumptionResult, ConsumptionRequest> {
        return ConsumptionResult(
                msisdnAnalyticsId = msisdn,
                granted = 0L,
                balance = 0L
        ).left()
    }

    fun isMccMncAllowed(sgsnMccMnc: String, imsiMccMnc: String) : Boolean {
        val sgsnMcc = sgsnMccMnc.substring(range = 0..2)
        return when (imsiMccMnc) {
            MccMnc.M1.value -> true
            MccMnc.DIGI.value -> digiAllowedMcc.contains(sgsnMcc)
            MccMnc.LOLTEL.value -> true
            else -> false
        }
    }
}
