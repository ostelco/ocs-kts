import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.ostelco.ocs.api.MultipleServiceCreditControl
import org.ostelco.prime.ocs.core.ConsumptionPolicy
import org.ostelco.prime.ocs.core.ConsumptionRequest
import org.ostelco.prime.storage.ConsumptionResult

private data class ServiceIdRatingGroup(
        val serviceId: Long,
        val ratingGroup: Long
)

object : ConsumptionPolicy {

    override fun checkConsumption(
            msisdn: String,
            multipleServiceCreditControl: MultipleServiceCreditControl,
            sgsnMccMnc: String,
            apn: String,
            imsiMccMnc: String): Either<ConsumptionResult, ConsumptionRequest> {

        val requested = multipleServiceCreditControl.requested?.totalOctets ?: 0
        val used = multipleServiceCreditControl.used?.totalOctets ?: 0

        if (!isMccMncAllowed(sgsnMccMnc, imsiMccMnc)) {
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
        return when (imsiMccMnc) {
            "52503" -> true  // M1
            "50216" -> true  // Digi
            "24201" -> true  // Telenor Norway
            else -> false
        }
    }
}
