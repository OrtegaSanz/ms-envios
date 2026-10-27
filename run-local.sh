#!/usr/bin/env bash
# COURIER_API_TOKEN simula el Secret; additional-location simula el ConfigMap montado en /config
export COURIER_API_TOKEN="tk-courier-9f3a-2026"
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=file:./config/"
