apollo {
  mapScalarToKotlinString("URL")
  mapScalar("LocalDate", "java.time.LocalDate")
  mapScalarToUpload("Upload")
  mapScalar("PaymentMethodsResponse", "com.adyen.checkout.components.model.PaymentMethodsApiResponse")
  mapScalarToKotlinString("CheckoutPaymentsAction")
  mapScalarToKotlinString("CheckoutPaymentAction")
  mapScalar("JSONString", "org.json.JSONObject")
  mapScalar("Instant", "java.time.Instant")
}
