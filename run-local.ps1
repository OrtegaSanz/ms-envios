# ===========================================================
#  RutaExpress . Arrancar ms-envios como lo haria el pod
# ===========================================================
#  COURIER_API_TOKEN  simula el Secret ms-envios-secret
#  additional-location simula el ConfigMap montado en /config
# ===========================================================
$env:COURIER_API_TOKEN = "tk-courier-9f3a-2026"
mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.config.additional-location=file:./config/"
