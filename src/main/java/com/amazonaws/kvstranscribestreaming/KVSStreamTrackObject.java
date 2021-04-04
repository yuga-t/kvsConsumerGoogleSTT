package com.amazonaws.kvstranscribestreaming;

import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Class that holds the KVS audio stream.
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
public class KVSStreamTrackObject {
    private InputStream inputStream;
    private StreamingMkvReader streamingMkvReader;
    private KVSContactTagProcessor tagProcessor;
    private FragmentMetadataVisitor fragmentVisitor;
    private Path saveAudioFilePath;
    private FileOutputStream outputStream;
    private String trackName;

    public KVSStreamTrackObject(InputStream inputStream, StreamingMkvReader streamingMkvReader,
                                KVSContactTagProcessor tagProcessor, FragmentMetadataVisitor fragmentVisitor,
                                Path saveAudioFilePath, FileOutputStream outputStream, String trackName) {
        this.inputStream = inputStream;
        this.streamingMkvReader = streamingMkvReader;
        this.tagProcessor = tagProcessor;
        this.fragmentVisitor = fragmentVisitor;
        this.saveAudioFilePath = saveAudioFilePath;
        this.outputStream = outputStream;
        this.trackName = trackName;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public StreamingMkvReader getStreamingMkvReader() {
        return streamingMkvReader;
    }

    public KVSContactTagProcessor getTagProcessor() {
        return tagProcessor;
    }

    public FragmentMetadataVisitor getFragmentVisitor() {
        return fragmentVisitor;
    }

    public Path getSaveAudioFilePath() {
        return saveAudioFilePath;
    }

    public FileOutputStream getOutputStream() {
        return outputStream;
    }

    public String getTrackName() {
        return trackName;
    }
}