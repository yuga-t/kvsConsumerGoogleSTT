package com.amazonaws.kvstranscribestreaming;

import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadata;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.MkvTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * An MkvTagProcessor that will ensure that we are only reading until end of
 * stream OR the contact id changes from what is expected.
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
public class KVSContactTagProcessor implements FragmentMetadataVisitor.MkvTagProcessor {
    private static final Logger logger = LoggerFactory.getLogger(KVSContactTagProcessor.class);

    private final String contactId;

    private boolean sameContact = true;
    private boolean stopStreaming = false;

    public KVSContactTagProcessor(String contactId) {
        this.contactId = contactId;
    }

    public void process(MkvTag mkvTag, Optional<FragmentMetadata> currentFragmentMetadata) {
        if ("ContactId".equals(mkvTag.getTagName())) {
            if (contactId.equals(mkvTag.getTagValue())) {
                sameContact = true;
            }
            else {
                logger.info("Contact Id in tag does not match expected, will stop streaming. "
                                + "contact id: %s, expected: %s",
                        mkvTag.getTagValue(), contactId);
                sameContact = false;
            }
        }
        if ("STOP_STREAMING".equals(mkvTag.getTagName())) {
            if ("true".equals(mkvTag.getTagValue())) {
                logger.info("STOP_STREAMING tag detected, will stop streaming");
                stopStreaming = true;
            }
        }
    }

    public boolean shouldStopProcessing() {
        return sameContact == false || stopStreaming == true;
    }
}
