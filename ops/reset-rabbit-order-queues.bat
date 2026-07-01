@echo off
setlocal

set RABBITMQ_MANAGEMENT_URL=%RABBITMQ_MANAGEMENT_URL%
if "%RABBITMQ_MANAGEMENT_URL%"=="" set RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672
set RABBITMQ_USER=%RABBITMQ_USER%
if "%RABBITMQ_USER%"=="" set RABBITMQ_USER=guest
set RABBITMQ_PASSWORD=%RABBITMQ_PASSWORD%
if "%RABBITMQ_PASSWORD%"=="" set RABBITMQ_PASSWORD=guest
set ORDER_QUEUE=%LOGISTICS_ORDER_CREATED_QUEUE%
if "%ORDER_QUEUE%"=="" set ORDER_QUEUE=logistics.order.created.queue

echo This script deletes local RabbitMQ order queues so Spring can recreate them with DLQ arguments.
echo Target: %RABBITMQ_MANAGEMENT_URL%
echo Queue : %ORDER_QUEUE%
echo.

curl.exe -s -u "%RABBITMQ_USER%:%RABBITMQ_PASSWORD%" -X DELETE "%RABBITMQ_MANAGEMENT_URL%/api/queues/%%2F/%ORDER_QUEUE%?if-empty=false^&if-unused=false" >nul
if errorlevel 1 (
  echo Failed to delete %ORDER_QUEUE%. Make sure RabbitMQ management plugin is enabled on port 15672.
  exit /b 1
)

curl.exe -s -u "%RABBITMQ_USER%:%RABBITMQ_PASSWORD%" -X DELETE "%RABBITMQ_MANAGEMENT_URL%/api/queues/%%2F/%ORDER_QUEUE%.dlq?if-empty=false^&if-unused=false" >nul

echo Done. Restart Java; the queue will be declared again with x-dead-letter-exchange.
