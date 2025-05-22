package com.function.service;

import java.time.LocalDateTime;
import java.util.logging.Logger;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.microsoft.azure.functions.ExecutionContext;

/**
 * Service to reprocess messages from Dead Letter Queue (DLQ) to the main Service Bus queue.
 */
public class DocusignReportsReprocessService {

    // Connection string and queue name should be stored as environment variables
    private static final String CONNECTION_STRING = System.getenv("AZURE_SERVICE_BUS_CONNECTION_STRING");
    private static final String QUEUE_NAME = System.getenv("AZURE_SERVICE_BUS_QUEUE_NAME");

    /**
     * This method reads messages from DLQ and republishes them to the same main queue.
     * @param context - Azure Function ExecutionContext for logging
     */
    @SuppressWarnings("LoggerStringConcat")
    public void reprocessDLQ(ExecutionContext context) {

        Logger logger = context.getLogger();
        logger.info("[Service] Step A: DLQ Reprocessing started at " + LocalDateTime.now());

        // Step B: Construct DLQ path for receiver
        String dlqPath = QUEUE_NAME + "/$DeadLetterQueue";
        logger.info("[Service] Step B: Connecting to DLQ path: " + dlqPath);

        try (
            // Step C: Create receiver client for DLQ
            ServiceBusReceiverClient receiver = new ServiceBusClientBuilder()
                .connectionString(CONNECTION_STRING)
                .receiver()
                .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
                .queueName(dlqPath)
                .buildClient();

            // Step D: Create sender client for the main queue
            ServiceBusSenderClient sender = new ServiceBusClientBuilder()
                .connectionString(CONNECTION_STRING)
                .sender()
                .queueName(QUEUE_NAME)
                .buildClient()
        ) {
            logger.info("[Service] Step E: Clients initialized for DLQ and main queue.");

            // Step F: Receive messages in batch from DLQ (max 50)
            Iterable<ServiceBusReceivedMessage> dlqMessages = receiver.receiveMessages(50);
            int count = 0;

            for (ServiceBusReceivedMessage message : dlqMessages) {
                count++;
                String messageId = message.getMessageId();

                // Step G: Log DLQ message
                logger.info(String.format("[Service] Step F%d: DLQ message received with ID: %s", count, messageId));

                // Step H: Create new message with original content
                ServiceBusMessage forwardMessage = new ServiceBusMessage(message.getBody());

                // Copy basic properties
                forwardMessage.getApplicationProperties().putAll(message.getApplicationProperties());
                forwardMessage.setContentType(message.getContentType());
                forwardMessage.setSubject(message.getSubject());

                // Step I: Send message to main queue
                sender.sendMessage(forwardMessage);
                logger.info(String.format("[Service] Step G%d: Message forwarded to main queue. ID: %s", count, messageId));

                // Step J: Complete (remove) message from DLQ
                receiver.complete(message);
                logger.info(String.format("[Service] Step H%d: Message completed from DLQ. ID: %s", count, messageId));
            }

            // Step K: Summary
            if (count == 0) {
                logger.info("[Service] Step I: No messages found in DLQ.");
            } else {
                logger.info(String.format("[Service] Step J: Total messages reprocessed from DLQ: %d", count));
            }

        } catch (Exception ex) {
            logger.severe(String.format("[Service] Step Z: Exception occurred: %s", ex.getMessage()));
        }
    }
}
