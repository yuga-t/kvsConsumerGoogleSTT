package com.amazonaws.kvstranscribestreaming;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * TranscribedSegmentWriter writes the transcript segments to DynamoDB
 * 
 * 
 * MIT License
 * 
 * Copyright (c) 2021 YugaTamae
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class TranscribedSegmentWriter {

    private String contactId;
    private DynamoDB ddbClient;
    private static final Logger logger = LoggerFactory.getLogger(TranscribedSegmentWriter.class);

    public TranscribedSegmentWriter(String contactId, DynamoDB ddbClient) {

        this.contactId = Validate.notNull(contactId);
        this.ddbClient = Validate.notNull(ddbClient);
    }

    public String getContactId() {

        return this.contactId;
    }

    public DynamoDB getDdbClient() {

        return this.ddbClient;
    }

    public void writeToDynamoDB(String transcript_segment, String phoneNumber, String tableName, int count, String audioFileLink, int limitDayOfTTL) {

        logger.info(String.format("writing to \"%s\"", tableName));

        if (!transcript_segment.equals("")) {
            try {
                Item ddbItem = toDynamoDbItem(transcript_segment, phoneNumber, count, audioFileLink,limitDayOfTTL);
                if (ddbItem != null) {
                    this.getDdbClient().getTable(tableName).putItem(ddbItem);
                }
                logger.info("write to dynamo success!");

            } catch (Exception e) {
                logger.error("Exception while writing to DDB: ", e);
            }
        }
    }

    private Item toDynamoDbItem(String transcript_segment, String phoneNumber, int replyCount, String audioFileLink, int limitDayOfTTL) {

        String contactId = this.getContactId();
        Item ddbItem = null;

        // Get the current time
        Instant now = Instant.now();
        String now_str = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.ofInstant(now, ZoneId.of("Asia/Tokyo")));

        // Get the TTL time
        long ExpireTime = now.getEpochSecond() + 60*60*24*limitDayOfTTL;

        ddbItem = new Item()
                .withKeyComponent("ContactId", contactId)
                .withKeyComponent("ReplyCount", replyCount)
                .withString("Transcript", transcript_segment)
                .withString("PhoneNumber", phoneNumber)
                .withString("AudioFileLink", audioFileLink)
                .withString("CreateTime", now_str)
                .withNumber("ExpireTime", ExpireTime);

        return ddbItem;
    }
}
