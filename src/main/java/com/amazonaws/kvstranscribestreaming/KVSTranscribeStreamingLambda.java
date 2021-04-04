package com.amazonaws.kvstranscribestreaming;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.Date;
import java.net.URLEncoder;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;

/**
 * This Lambda is invoked by another Lambda. 
 * Get the audio stream from KVS. 
 * Get the real time transcription by Google Speech to Text.
 * Write the transcription to DynamoDB.
 * Save the audio data as wav file in s3.
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
public class KVSTranscribeStreamingLambda implements RequestHandler<TranscriptionRequest, String> {

    // Environment Variable
    private static final Regions REGION = Regions.fromName(System.getenv("REGION"));
    private static final String RECORDINGS_BUCKET_NAME = System.getenv("RECORDINGS_BUCKET_NAME");
    private static final String RECORDINGS_KEY_PREFIX = System.getenv("RECORDINGS_KEY_PREFIX");
    private static final boolean RECORDINGS_PUBLIC_READ_ACL = Boolean.parseBoolean(System.getenv("RECORDINGS_PUBLIC_READ_ACL"));
    private static final String START_SELECTOR_TYPE = System.getenv("START_SELECTOR_TYPE");
    private static final String TRANSCRIPT_TABLE_NAME = System.getenv("TRANSCRIPT_TABLE_NAME");
    private static final int LIMITDAY_OF_TTL = Integer.parseInt(System.getenv("LIMITDAY_OF_TTL"));

    private static final Logger logger = LoggerFactory.getLogger(KVSTranscribeStreamingLambda.class);
    public static final MetricsUtil metricsUtil = new MetricsUtil(AmazonCloudWatchClientBuilder.defaultClient());
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    // SegmentWriter saves Transcription segments to DynamoDB
    private TranscribedSegmentWriter fromCustomerSegmentWriter = null;

    // Number of replies
    private int replyCount = 1;

    /**
     * Handler function for the Lambda
     *
     * @param request
     * @param context
     * @return
     */
    @Override
    public String handleRequest(TranscriptionRequest request, Context context) {

        logger.info("received request: " + request.toString());
        logger.info("received context: " + context.toString());

        try {
            // create a SegmentWriter to be able to save off transcription results
            AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard();
            builder.setRegion(REGION.getName());
            fromCustomerSegmentWriter = new TranscribedSegmentWriter(request.getConnectContactId(), new DynamoDB(builder.build()));

            // Start Google Speech to Text
            startKVSToTranscribeStreaming(request.getStreamARN(), request.getStartFragmentNum(), request.getConnectContactId(), request.getSaveCallRecording(), request.getPhoneNumber());

            return "{ \"result\": \"Success\" }";

        } catch (Exception e) {
            logger.error("KVS to Transcribe Streaming failed with: ", e);
            return "{ \"result\": \"Failed\" }";
        }
    }

    /**
     * Get the audio stream from KVS and transcribe it with GoogleSpeechToText.
     * 
     * @param streamARN
     * @param startFragmentNum
     * @param contactId
     * @throws Exception
     */
    private void startKVSToTranscribeStreaming(String streamARN, String startFragmentNum, String contactId,Optional<Boolean> saveCallRecording, String phoneNumber) throws Exception {

        String streamName = streamARN.substring(streamARN.indexOf("/") + 1, streamARN.lastIndexOf("/"));
        KVSStreamTrackObject kvsStreamTrackObjectFromCustomer = getKVSStreamTrackObject(streamName, startFragmentNum, KVSUtils.TrackName.AUDIO_FROM_CUSTOMER.getName(), contactId);

        // Parameters required to get the audio stream from KVS
        StreamingMkvReader streamingMkvReader = kvsStreamTrackObjectFromCustomer.getStreamingMkvReader();
        FragmentMetadataVisitor fragmentVisitor = kvsStreamTrackObjectFromCustomer.getFragmentVisitor();
        KVSContactTagProcessor tagProcessor = kvsStreamTrackObjectFromCustomer.getTagProcessor();
        int CHUNK_SIZE_IN_KB = 4;
        String track = kvsStreamTrackObjectFromCustomer.getTrackName();

        // Get audio file link to save to s3
        String audio_file_path = URLEncoder.encode(kvsStreamTrackObjectFromCustomer.getSaveAudioFilePath().toString().replace(".raw", ".wav").replace("/tmp/", ""), "UTF-8");
        String audio_file_link = "https://" + RECORDINGS_BUCKET_NAME + ".s3-ap-northeast-1.amazonaws.com/"+ RECORDINGS_KEY_PREFIX + audio_file_path;

        logger.info(String.format("phoneNumber: %s\n", phoneNumber));
        logger.info(String.format("audioFileLink: %s\n", audio_file_link));
        logger.info(String.format("replyCount: %d\n", replyCount));

        ResponseObserver<StreamingRecognizeResponse> responseObserver = null;

        // Google Speech to Text Streaming
        try (SpeechClient client = SpeechClient.create()) {

            ClientStream<StreamingRecognizeRequest> clientStream;

            // response handler
            responseObserver =
                new ResponseObserver<StreamingRecognizeResponse>() {

                    public void onStart(StreamController controller) {
                        logger.info(String.format("GoogleSTT Start"));
                        replyCount = 1;
                    }

                    public void onResponse(StreamingRecognizeResponse response) {

                        logger.info("===response===");
                        System.out.println(response);

                        StreamingRecognitionResult result = response.getResultsList().get(0);
                        SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                        String transcript_segment = alternative.getTranscript();
                        float confidence = alternative.getConfidence();

                        logger.info(String.format("Transctipt : %s\n", transcript_segment));
                        logger.info(String.format("Confidence : %f\n", confidence));
                        
                        if(result.getIsFinal()){
                            fromCustomerSegmentWriter.writeToDynamoDB(transcript_segment, phoneNumber, TRANSCRIPT_TABLE_NAME, replyCount, audio_file_link, LIMITDAY_OF_TTL);
                            replyCount++;
                        }
                    }

                    public void onComplete() {
                        logger.info(String.format("GoogleSTT Complete"));
                    }

                    public void onError(Throwable t) { 
                        logger.error(String.format("GoogleSTT Error : %s\n", t));
                    }
                };

            clientStream = client.streamingRecognizeCallable().splitCall(responseObserver);

            // request parameter
            RecognitionConfig recognitionConfig =
                RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setLanguageCode("ja-JP")
                    .setSampleRateHertz(8000) // great for phone voice
                    .setEnableAutomaticPunctuation(true)
                    .setUseEnhanced(true) // use enhanced model
                    .build();

            StreamingRecognitionConfig streamingRecognitionConfig =
                StreamingRecognitionConfig.newBuilder()
                    .setConfig(recognitionConfig)
                    .setSingleUtterance(false)
                    .build();

            StreamingRecognizeRequest request_google =
                StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingRecognitionConfig)
                    .build();

            // Send configuration request
            clientStream.send(request_google);

            // Get audio stream and send request to Google STT
            while (true) {

                // Get audio stream
                ByteBuffer audioBuffer = KVSUtils.getByteBufferFromStream(streamingMkvReader, fragmentVisitor,
                        tagProcessor, contactId, CHUNK_SIZE_IN_KB, track);

                if (audioBuffer.remaining() > 0) {

                    byte[] audioBytes = new byte[audioBuffer.remaining()];
                    audioBuffer.get(audioBytes);

                    // send request to Google STT
                    request_google = StreamingRecognizeRequest.newBuilder()
                            .setAudioContent(ByteString.copyFrom(audioBytes)).build();
                    clientStream.send(request_google);

                    // Write audio stream to outputStream
                    kvsStreamTrackObjectFromCustomer.getOutputStream().write(audioBytes);

                } else {
                    break;
                }
            }
            
            responseObserver.onComplete();

        } catch (Exception e) {

            logger.info(String.format("Error KVS or GoogleSTT : %s\n", e));
        }

        try {
            // save wav file to s3
            closeFileAndUploadRawAudio(kvsStreamTrackObjectFromCustomer,contactId,saveCallRecording);
        }catch(IOException e){
            logger.info(String.format("Error closeFile and UploadRawAudio: %s\n", e));
        }

    }

    /**
     * Create all objects necessary for KVS streaming from each track
     *
     * @param streamName
     * @param startFragmentNum
     * @param trackName
     * @param contactId
     * @return
     * @throws FileNotFoundException
     */
    private KVSStreamTrackObject getKVSStreamTrackObject(String streamName, String startFragmentNum,
            String trackName, String contactId) throws FileNotFoundException {
        
        InputStream kvsInputStream = KVSUtils.getInputStreamFromKVS(streamName, REGION, startFragmentNum, getAWSCredentials(), START_SELECTOR_TYPE);
        StreamingMkvReader streamingMkvReader = StreamingMkvReader.createDefault(new InputStreamParserByteSource(kvsInputStream));

        KVSContactTagProcessor tagProcessor = new KVSContactTagProcessor(contactId);
        FragmentMetadataVisitor fragmentVisitor = FragmentMetadataVisitor.create(Optional.of(tagProcessor));

        String fileName = String.format("%s_%s_%s.raw", contactId, DATE_FORMAT.format(new Date()), trackName);
        Path saveAudioFilePath = Paths.get("/tmp", fileName);
        FileOutputStream fileOutputStream = new FileOutputStream(saveAudioFilePath.toString());

        return new KVSStreamTrackObject(kvsInputStream, streamingMkvReader, tagProcessor, fragmentVisitor, saveAudioFilePath, fileOutputStream, trackName);
    }

    /**
     * @return AWS credentials to be used to connect to s3 (for fetching and uploading audio) and KVS
     */
    private static AWSCredentialsProvider getAWSCredentials() {
        return DefaultAWSCredentialsProviderChain.getInstance();
    }

    /**
     * Closes the FileOutputStream and uploads the Raw audio file to S3
     *
     * @param kvsStreamTrackObject
     * @param saveCallRecording
     * @throws IOException
     */
    private void closeFileAndUploadRawAudio(KVSStreamTrackObject kvsStreamTrackObject, String contactId,
            Optional<Boolean> saveCallRecording) throws IOException {

        kvsStreamTrackObject.getInputStream().close();
        kvsStreamTrackObject.getOutputStream().close();

        //Upload the Raw Audio file to S3
        if ((saveCallRecording.isPresent() ? saveCallRecording.get() : false)
                && (new File(kvsStreamTrackObject.getSaveAudioFilePath().toString()).length() > 0)) {
            AudioUtils.uploadRawAudio(REGION, RECORDINGS_BUCKET_NAME, RECORDINGS_KEY_PREFIX,
                    kvsStreamTrackObject.getSaveAudioFilePath().toString(), contactId, RECORDINGS_PUBLIC_READ_ACL,
                    getAWSCredentials());
        } else {
            logger.info("Skipping upload to S3.  saveCallRecording was disabled or audio file has 0 bytes: "
                    + kvsStreamTrackObject.getSaveAudioFilePath().toString());
        }
    }
    
}
