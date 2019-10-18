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
            mccMnc: String,
            apn: String): Either<ConsumptionResult, ConsumptionRequest> {

        val requested = multipleServiceCreditControl.requested?.totalOctets ?: 0
        val used = multipleServiceCreditControl.used?.totalOctets ?: 0

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
            else -> {
                ConsumptionResult(
                        msisdnAnalyticsId = msisdn,
                        granted = 0L,
                        balance = 0L
                ).left()
            }
        }
    }
}
