package com.function.utils;

import java.util.UUID;
import java.util.logging.Logger;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;

public class ServiceBusHelper {
    /**
     * Publishes a message to Azure Service Bus Queue with correlation ID using Managed Identity.
     *
     * Required environment variables:
     * - SERVICE_BUS_NAMESPACE (e.g., your-namespace.servicebus.windows.net)
     * - SERVICE_BUS_QUEUE_NAME
     *
     * @param messageBody The message payload to be sent (as String)
     * @param logger      Logger for tracing execution
     * @return JSON string containing status or error and correlation ID
     */
    public static String publishMessage(String messageBody, Logger logger) {
        String correlationId = UUID.randomUUID().toString();
        logger.info("[ServiceBus] [Step 1] Correlation ID generated: " + correlationId);

        try {
            // Step 2: Read environment variables
            logger.info("[ServiceBus] [Step 2] Reading environment variables");
            String namespace = System.getenv("SERVICE_BUS_NAMESPACE"); // e.g. "your-namespace.servicebus.windows.net"
            String queueName = System.getenv("SERVICE_BUS_QUEUE_NAME");

            if (namespace == null || queueName == null) {
                logger.severe("[ServiceBus] [Step 2] Missing SERVICE_BUS_NAMESPACE or SERVICE_BUS_QUEUE_NAME");
                return String.format("{\"error\": \"Missing Service Bus environment variables\", \"correlationId\": \"%s\"}", correlationId);
            }

            // Step 3: Create sender client using Managed Identity
            logger.info("[ServiceBus] [Step 3] Creating sender client using Managed Identity for queue: " + queueName);
            ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                    .credential(namespace, new DefaultAzureCredentialBuilder().build())
                    .sender()
                    .queueName(queueName)
                    .buildClient();

            // Step 4: Prepare message
            logger.info("[ServiceBus] [Step 4] Preparing message payload");
            ServiceBusMessage message = new ServiceBusMessage(messageBody);
            message.setMessageId(correlationId);
            message.getApplicationProperties().put("source", "JavaAzureFunction");
            message.getApplicationProperties().put("correlationId", correlationId);

            // Step 5: Send message
            logger.info("[ServiceBus] [Step 5] Sending message to queue...");
            senderClient.sendMessage(message);

            // Step 6: Close sender
            logger.info("[ServiceBus] [Step 6] Closing sender client");
            senderClient.close();

            logger.info("[ServiceBus] [Step 7] Message sent successfully");
            return String.format("{\"status\": \"Message sent successfully\", \"correlationId\": \"%s\"}", correlationId);

        } catch (Exception e) {
            logger.severe(String.format("[ServiceBus] [Error] Exception occurred while sending message: %s", e.getMessage()));
            return String.format("{\"error\": \"%s\", \"correlationId\": \"%s\"}", e.getMessage().replace("\"", "'"), correlationId);
        }
    }
    
}
