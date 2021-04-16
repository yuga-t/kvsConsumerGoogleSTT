# kvsConsumerGoogleSTT

This Lambda is invoked by another Lambda.<br>
Get the audio stream from KVS.<br>
Get the real time transcription by Google Speech to Text.<br>
Write the transcription to DynamoDB.<br>
Save the audio data as wav file in s3.

# DEMO

https://drive.google.com/file/d/1OsolNuuSxA6zbFeaM-fIVdolntdGaLqo/view?usp=sharing

# Requirement

* openjdk version "1.8.0_265"
* Gradle 6.6.1
* credentials.json with Google Speech To Text enabled

Maybe it will work with other versions as well. Try it for yourself.

# Usage

```bash
git clone https://github.com/yuga-t/kvsConsumerGoogleSTT.git

cd kvsConsumerGoogleSTT
```

Put credentials.json in kvsConsumerGoogleSTT/src/resources/

```bash
gradle build
```

Upload kvsConsumerGoogleSTT/build/ditributions/kvsConsumerGoogleSTT.zip to Lambda.

Set Handler of Runtime settings

`com.amazonaws.kvstranscribestreaming.KVSTranscribeStreamingLambda::handleRequest`

Set Environment Variable of Lambda.

- key:`GOOGLE_APPLICATION_CREDENTIALS`, value:`credentials.json`
- key:`LIMITDAY_OF_TTL`, value:`1`
- key:`RECORDINGS_BUCKET_NAME`, value:`amazon-connect-audio-bucket`
- key:`RECORDINGS_KEY_PREFIX`, value:`recordings/`
- key:`RECORDINGS_PUBLIC_READ_ACL`, value:`TRUE`
- key:`REGION`, value:`ap-northeast-1`
- key:`START_SELECTOR_TYPE`, value:`NOW`
- key:`TRANSCRIPT_TABLE_NAME`, value:`transcriptTable`

# Note

The demo will not work on this project alone.<br>Requires AmazonConnect ContactFlow and another Lambda, and so on.<br>Please refer to [here](https://qiita.com/yuga-t/items/3f827bd6fd3a8a509646).

# References

- https://github.com/amazon-connect/amazon-connect-realtime-transcription
- https://github.com/googleapis/java-speech/blob/master/samples/snippets/src/main/java/com/example/speech/InfiniteStreamRecognize.java

# License

"kvsConsumerGoogleSTT" is under [MIT license](https://en.wikipedia.org/wiki/MIT_License).