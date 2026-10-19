package io.github.bennixtech.daraja;

import io.github.bennixtech.daraja.accountbalance.*;
import io.github.bennixtech.daraja.b2b.*;
import io.github.bennixtech.daraja.b2c.*;
import io.github.bennixtech.daraja.c2b.*;
import io.github.bennixtech.daraja.reversal.*;
import io.github.bennixtech.daraja.stkpush.*;
import io.github.bennixtech.daraja.transactionstatus.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** Blocking Daraja operations. Responses acknowledge requests; callbacks report final outcomes. */
@HttpExchange(contentType = MediaType.APPLICATION_JSON_VALUE, accept = MediaType.APPLICATION_JSON_VALUE)
public interface DarajaApiClient {
    @NotNull
    @PostExchange("/mpesa/stkpush/v1/processrequest")
    StkPushResponse initiateStkPush(@RequestBody @NotNull @Valid StkPushRequest request);

    @NotNull
    @PostExchange("/mpesa/c2b/v1/registerurl")
    C2BRegisterUrlResponse registerC2BUrls(@RequestBody @NotNull @Valid C2BRegisterUrlRequest request);

    @NotNull
    @PostExchange("/mpesa/b2c/v3/paymentrequest")
    B2CResponse b2cPayment(@RequestBody @NotNull @Valid B2CRequest request);

    @NotNull
    @PostExchange("/mpesa/b2b/v1/paymentrequest")
    B2BResponse b2bPayment(@RequestBody @NotNull @Valid B2BRequest request);

    @NotNull
    @PostExchange("/mpesa/transactionstatus/v1/query")
    TransactionStatusResponse transactionStatus(@RequestBody @NotNull @Valid TransactionStatusRequest request);

    @NotNull
    @PostExchange("/mpesa/accountbalance/v1/query")
    AccountBalanceResponse accountBalance(@RequestBody @NotNull @Valid AccountBalanceRequest request);

    @NotNull
    @PostExchange("/mpesa/reversal/v1/request")
    ReversalResponse reversal(@RequestBody @NotNull @Valid ReversalRequest request);
}
